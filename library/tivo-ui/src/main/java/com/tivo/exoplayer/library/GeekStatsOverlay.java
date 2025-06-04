package com.tivo.exoplayer.library;

import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.SntpClient;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Formatter;
import java.util.Locale;

/**
 * Monitors playback session and powers the stats for geeks overlay for player
 * debug.
 *
 * To make this overlay visible send the intent {@link #DISPLAY_INTENT} to the demo
 * or other android application hosting the player that supports geeks display.
 *
 * To integrate this UI in your ExoPlayer application add a view with this
 *
 * PERFORMANCE OPTIMIZATION:
 * This class implements a start/stop pattern based on visibility to prevent
 * expensive operations when the debug overlay is not visible:
 * - When VISIBLE: Adds analytics listener and starts timer callbacks
 * - When INVISIBLE: Removes analytics listener and stops timer callbacks
 * This eliminates CPU/memory overhead and ANR contributions when not in use.
 */
public class GeekStatsOverlay implements AnalyticsListener, Runnable {

  public static final String DISPLAY_INTENT = "com.tivo.exoplayer.SHOW_GEEKSTATS";
  private static final String TAG = "ExoGeekStat";
  private static final SimpleDateFormat UTC_DATETIME
      = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.S Z", Locale.getDefault());
  private static final SimpleDateFormat UTC_TIME = new SimpleDateFormat("HH:mm:ss.S Z", Locale.getDefault());

  private final View containingView;
  private final SimpleSlidingGraph bufferingGraph;
  private final TextView bufferingLevel;
  private final TextView bandwidthStats;
  private final SimpleSlidingGraph bandwidthGraph;
  private final int levelBitrateTraceNum;
  private final int bandwidthTraceNum;
  private final TextView manifestUrl;

  @Nullable
  private SimpleExoPlayer player;

  @Nullable
  private TrickPlayControl trickPlayControl;

  private final TextView stateView;
  private final TextView currentTimeView;
  private final TextView currentVideoTrack;
  private final TextView currentAudioTrack;
  private final TextView loadingLevel;
  private final TextView playbackRate;
  private final TextView bufferingCountDisplay;

  private final int updateInterval;

  private Format lastLoadedVideoFormat;
  private Format lastDownstreamVideoFormat;
  private long lastPositionReport;

  // Statistic counters, reset on url change
  private long lastTimeUpdate;
  private int levelSwitchCount = 0;
  private int bufferingCount = 0;   // counts "buffering" for any reason (initial playback, seek, or "stalling")
  private float minBandwidth = Float.MIN_VALUE;
  private float maxBandwidth = Float.MAX_VALUE;
  private Loader timeLoader;

  // Very chatty logging for debugging live offset, disabled by default
  private boolean enableLiveOffsetLogging;


  public GeekStatsOverlay(View view, int updateInterval) {
    containingView = view;
    currentVideoTrack = view.findViewById(R.id.video_track);
    currentAudioTrack = view.findViewById(R.id.audio_track);
    loadingLevel = view.findViewById(R.id.loading_level);
    stateView = view.findViewById(R.id.current_state);
    currentTimeView = view.findViewById(R.id.current_time);
    playbackRate = view.findViewById(R.id.playback_rate);
    bufferingCountDisplay = view.findViewById(R.id.buffering_count);
    bufferingGraph = view.findViewById(R.id.buffering_graph);

    int traceColor = containingView.getResources().getColor(R.color.colorBuffered);
    bufferingGraph.addTraceLine(traceColor, 0, 70);

    bufferingLevel = view.findViewById(R.id.buffering_level);

    bandwidthStats = view.findViewById(R.id.bandwidth_stats);

    bandwidthGraph = view.findViewById(R.id.bandwidth_graph);

    // Add a line for current level bitrate (in Mbps) TODO - move to track select
    traceColor = containingView.getResources().getColor(R.color.colorLevel);
    levelBitrateTraceNum =
        bandwidthGraph.addTraceLine(traceColor, 0, 40);

    // Add a line for current bandwidth bitrate (in Mbps)
    traceColor = containingView.getResources().getColor(R.color.colorBandwidth);
    bandwidthTraceNum =
        bandwidthGraph.addTraceLine(traceColor, 0, 40);

    manifestUrl = view.findViewById(R.id.manifest_url);
    this.updateInterval = updateInterval;
    enableLiveOffsetLogging = false;
    initializeNtpTime();

    containingView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
      @Override
      public void onViewAttachedToWindow(View v) {
      }

      @Override
      public void onViewDetachedFromWindow(View v) {
        setPlayer(null, null);  // cleanup when view is detached
        freeNtpTimeLoader();
      }
    });
  }

  public GeekStatsOverlay(View view) {
    this(view, 1000);
  }

  /**
   * Call this to update the player and it's associated TrickPlayControl (if any).
   *
   * <p>Calling with a null player and trickPlayControl will remove the old player listeners
   * and stop the periodic update timer.</p>
   *
   * <p>When called with a player, the periodic update timer is started if the overlay is
   * already visible ({@link #isGeekOverlayVisible()} is true)</p>
   *
   * <p>If this call changes the active player, (this.player != player) then
   * the statistics are reset and the listeners are removed for the old player.</p>
   *
   * @param player current player, or null to remove old player
   * @param trickPlayControl trickplay control for the player (if any)
   */
  public void setPlayer(@Nullable SimpleExoPlayer player, TrickPlayControl trickPlayControl) {
    this.trickPlayControl = trickPlayControl;

    if (player == null && this.player != null) {
      stop();
      this.player.removeAnalyticsListener(this);
      this.player = null;
    } else if (player != null) {
      if (this.player != null && this.player != player) {
        // If the player is changing, reset stats and remove old listener
        resetStats();
        this.player.removeAnalyticsListener(this);
      }
      this.player = player;
      player.addAnalyticsListener(this);

      if (isGeekOverlayVisible()) {
        start();
      }
    }
  }

  /**
   * Toggles the visibility of the geek stats overlay.  If the overlay is currently,
   * visible it will be set to {@link View#INVISIBLE} and the periodic update timer is
   * stopped, if it is currently {@link View#INVISIBLE} it will be set to {@link View#VISIBLE}
   * and the periodic update timer is started.
   *
   * <p>Note that the statistics are not reset when the visibility is toggled, only when a new
   * media item is prepared. Or if {@link #resetStats()} is explicitly called.</p>
   */
  public void toggleVisible() {
    int visibility = containingView.getVisibility();
    setVisible(visibility != View.VISIBLE);
  }

  /**
   * Sets the visibility of the geek stats overlay.  See {link #toggleVisible()} for details
   * on how the visibility is handled.
   *
   * @param visible true to make the overlay visible, false to make it invisible.
   */
  public void setVisible(boolean visible) {
    int visibility = containingView.getVisibility();

    if (visible) {
      containingView.setVisibility(View.VISIBLE);
      if (visibility != View.VISIBLE) {
        // Only start the timer if we are becoming visible
        if (player != null) {
          start();
        }
      }
    } else {
      containingView.setVisibility(View.INVISIBLE);
      if (visibility == View.VISIBLE) {
        // Only stop the timer if we are becoming invisible
        stop();
      }
    }
  }

  public boolean isGeekOverlayVisible() {
    return containingView.getVisibility() == View.VISIBLE;
  }

  /**
   * Called to reset the current statistics counters driving the display.
   *
   * <p>This will reset the level switch count, buffering count, last time update, etc.
   * it is automatically called when a new media item is prepared (channel change).</p>
   */
  public void resetStats() {
    levelSwitchCount = 0;
    bufferingCount = 0;
    lastTimeUpdate = C.TIME_UNSET;
    minBandwidth = Float.MIN_VALUE;
    maxBandwidth = Float.MAX_VALUE;
    manifestUrl.setText("");
  }

  public void setEnableLiveOffsetLogging(boolean enableLiveOffsetLogging) {
    this.enableLiveOffsetLogging = enableLiveOffsetLogging;
  }

  /**
   * Starts the periodic update timer
   */
  private void start() {
    if (player != null && isGeekOverlayVisible()) {
      long currentTime = System.currentTimeMillis();
      long nextTargetTime = (currentTime + updateInterval) - currentTime % updateInterval;
      postNextUpdateTimer(nextTargetTime - currentTime);
    }
  }


  private void stop() {
    containingView.removeCallbacks(this);
  }

  private void initializeNtpTime() {
    timeLoader = new Loader("GeekStatsTimeLoader");

    SntpClient.initialize(timeLoader, new SntpClient.InitializationCallback() {
      @Override
      public void onInitialized() {
        StringBuilder logString = new StringBuilder();
        Formatter formatter = new Formatter(logString);
        long currentTimeMillis = System.currentTimeMillis();
        long ntpTimeMillis = Util.getNowUnixTimeMs(SntpClient.getElapsedRealtimeOffsetMs());
        formatter.format("SntpClient init - System.currentTimeMillis(): %1$tFT%1$tT.%1$tL (%1$d), NTP Time: %2$tFT%2$tT.%2$tL (%2$d), delta %3$d",
            currentTimeMillis,
            ntpTimeMillis,
            currentTimeMillis - ntpTimeMillis
        );

        Log.d("LivePosition", logString.toString());
        freeNtpTimeLoader();
      }

      @Override
      public void onInitializationFailed(IOException error) {
        freeNtpTimeLoader();
      }
    });
  }

  private void freeNtpTimeLoader() {
    if (timeLoader != null) {
      timeLoader.release();
      timeLoader = null;
    }
  }

  private void timedTextUpdate() {
    long currentTime = System.currentTimeMillis();
    if (player != null && enableLiveOffsetLogging) {
      logDebugPositionForLiveOffset(player);
    }
    updateChildViews();
    long nextTargetTime = (currentTime + updateInterval) - currentTime % updateInterval;
    postNextUpdateTimer(nextTargetTime - currentTime);
  }

  private void postNextUpdateTimer(long delayMillis) {
    containingView.removeCallbacks(this);
    containingView.postDelayed(this, delayMillis);
  }

  private static void logDebugPositionForLiveOffset(@NonNull SimpleExoPlayer player) {
    long position = player.getCurrentPosition();
    StringBuilder logString = new StringBuilder();
    Formatter formatter = new Formatter(logString);
    Timeline timeline = player.getCurrentTimeline();
    if (!timeline.isEmpty()) {
      int windowIndex = player.getCurrentWindowIndex();
      Timeline.Window window = timeline.getWindow(windowIndex, new Timeline.Window());
      long liveOffsetMs = player.getCurrentLiveOffset();
      // Now (time), Now (ms), Window Start, Window Start Time(ms), Position (time), Position in Window, Duration (ms), Live Offset (ms)
      formatter.format(
          "%1$tFT%1$tT.%1$tL, %1$d, %2$tFT%2$tT.%2tL, %2$d, %3tFT%3$tT.%3$tL, %3$d, %4$d, %5$3.2f",
          window.getCurrentUnixTimeMs(),
          window.windowStartTimeMs,
          window.windowStartTimeMs + position,
          position,
          liveOffsetMs / 1000.0
      );
      Log.d("LivePosition", logString.toString());
    }
  }

  private void updateChildViews() {
    currentTimeView.setText(getPositionString());
    currentVideoTrack.setText(getPlayingVideoTrack());
    currentAudioTrack.setText(getPlayingAudioTrack());
    playbackRate.setText(getPlaybackRateString());
    bufferingCountDisplay.setText(String.valueOf(bufferingCount));

    float buffered = getBufferedSeconds();
    bufferingGraph.addDataPoint(buffered, 0);
    bufferingLevel.setText(String.format(Locale.getDefault(), "%.2fs", buffered));
  }

  private float getBufferedSeconds() {
    float buffered = 0.0f;
    if (player != null) {
      long bufferedMs = player.getTotalBufferedDuration();
      buffered = bufferedMs / 1000.0f;
    }
    return buffered;
  }

  private String getPlayerState() {
    String state = "no-player";
    if (player != null) {
      switch (player.getPlaybackState()) {
        case Player.STATE_BUFFERING:
          state = "buffering";
          break;
        case Player.STATE_IDLE:
          state = "idle";
          break;
        case Player.STATE_ENDED:
          state = "ended";
          break;
        case Player.STATE_READY:
          state = "ready";
          break;
      }
    }
    return state;
  }

  private CharSequence getPlayingVideoTrack() {
    String trackInfo = "V: ";

    if (player == null) {
      trackInfo += "<unknown>";
    } else {
      Format format = player.getVideoFormat();
      trackInfo += getFormatString(format);
      trackInfo += getDecoderCountersString(player.getVideoDecoderCounters());
    }

    return trackInfo;
  }


  private CharSequence getPlayingAudioTrack() {
    String trackInfo = "A: ";

    if (player == null) {
      trackInfo += "<unknown>";
    } else {
      Format format = player.getAudioFormat();
      DecoderCounters decoderCounters = player.getAudioDecoderCounters();

      trackInfo += getFormatString(format) + " " + getDecoderCountersString(decoderCounters);
    }
    return trackInfo;
  }


  private String getDecoderCountersString(DecoderCounters decoderCounters) {
    String value = "";
    if (decoderCounters != null) {
      if (decoderCounters.totalVideoFrameProcessingOffsetUs > 0) {
        value += String.format(Locale.getDefault(), "(qib:%d db:%d mcdb:%d r:%d vfpo:%3.1f)",
            decoderCounters.inputBufferCount,
            decoderCounters.droppedBufferCount,
            decoderCounters.maxConsecutiveDroppedBufferCount,
            decoderCounters.renderedOutputBufferCount,
            (float) C.usToMs(decoderCounters.totalVideoFrameProcessingOffsetUs) / (float) decoderCounters.videoFrameProcessingOffsetCount);
      } else {
        value += String.format(Locale.getDefault(), "(qib:%d db:%d mcdb:%d r:%d)",
            decoderCounters.inputBufferCount,
            decoderCounters.droppedBufferCount,
            decoderCounters.maxConsecutiveDroppedBufferCount,
            decoderCounters.renderedOutputBufferCount);
      }
    }
    return value;
  }

  private String getFormatString(Format format) {
    String display = "<unknown>";

    if (format != null) {
      int bps = format.bitrate;
      float mbps = bps / 1_000_000.0f;
      String label = format.label == null ? format.id : format.label;
      if (isVideoFormat(format)) {
        if (bps == -1) {
          display = String.format(Locale.getDefault(),
              "%s - %dx%d@<unk>", label, format.width, format.height);
        } else {
          display = String.format(Locale.getDefault(),
              "%s - %dx%d@%.3f", label, format.width, format.height, mbps);
        }
        if ((format.roleFlags & C.ROLE_FLAG_TRICK_PLAY) != 0) {
          display += " (ifrm)";
        }
      } else if (isAudioOnlyFormat(format)) {
        String mimeType = format.sampleMimeType;
        if (mimeType == null) {
          mimeType = MimeTypes.getMediaMimeType(format.codecs);
        }
        String[] mimeComponents = mimeType.split("/");
        String audioType = mimeComponents.length > 1 ? mimeComponents[1] : mimeType;
        if (bps == -1) {
          display = String.format(Locale.getDefault(),
              "%.10s - %s@<unk>", label, audioType);
        } else {
          display = String.format(Locale.getDefault(),
              "%.10s - %s@%.3f", label, audioType, mbps);
        }
      }

    }
    return display;
  }

  protected String getPositionString() {
    String time = "";

    StringBuilder timeString = new StringBuilder();

    if (player != null) {
      long position = player.getCurrentPosition();
      Formatter formatter = new Formatter(timeString);

      Timeline timeline = player.getCurrentTimeline();
      if (! timeline.isEmpty()) {
        int windowIndex = player.getCurrentWindowIndex();
        Timeline.Window currentWindow = timeline.getWindow(windowIndex, new Timeline.Window());
        if (currentWindow.windowStartTimeMs == C.TIME_UNSET || !currentWindow.isDynamic ) {    // VOD or ended Socu
          float positionSeconds = position / 1000.0f;
          long positionSecondsTruncated = (long) positionSeconds;
          formatter.format("%d:%02d:%02d (%d)",
              positionSecondsTruncated / 3600,
              (positionSecondsTruncated % 3600) / 60,
              positionSecondsTruncated % 60,
              position);
        } else if (currentWindow.isLive()) {
          formatter.format("%1$tF %1$tT.%1$tL [live - %3$.3f] (%2$d)",
              currentWindow.windowStartTimeMs + position,
              position,
              player.getCurrentLiveOffset() / 1000.0
          );
        }
        time = formatter.toString();
      }

    }

    return time;
  }

  /**
   * Playback position, either relative to the start of an unknown start time
   * VOD asset or in time since the unix Epoch for live or event asset.
   *
   * Time is always increasing (assuming no seeks)
   *
   * @return time in millieeconds since the epoch or start of VOD asset
   */
  protected long getPeriodPosition() {
    long position = C.TIME_UNSET;
    if (player != null) {
      position = player.getContentPosition();
      Timeline timeline = player.getCurrentTimeline();
      if (! timeline.isEmpty()) {
        int windowIndex = player.getCurrentWindowIndex();
        Timeline.Window currentWindow = timeline.getWindow(windowIndex, new Timeline.Window());
        if (currentWindow.windowStartTimeMs != C.TIME_UNSET) {    // VOD
          position += currentWindow.windowStartTimeMs;
        }
      }
    }
    return position;
 }

  protected String getPlaybackRateString() {
    String rateString = "";
    long latestElapsedRealtime = SystemClock.elapsedRealtime();
    if (lastTimeUpdate == C.TIME_UNSET) {
        lastTimeUpdate = latestElapsedRealtime;
    } else if (player != null && trickPlayControl != null) {
        long currentPosition = getPeriodPosition();
        long positionChange = Math.abs(lastPositionReport - currentPosition);
        long timeChange = latestElapsedRealtime - lastTimeUpdate;
        lastTimeUpdate = latestElapsedRealtime;
        lastPositionReport = currentPosition;
        float playbackRate = (float)positionChange / (float)timeChange;
        float trickSpeed = trickPlayControl.getSpeedFor(trickPlayControl.getCurrentTrickMode());
        rateString = String.format("%.3f (%.3f)", trickSpeed, playbackRate);
    }
    return rateString;
  }

  /**
   * Called this method before calling {@link SimpleExoPlayer#prepare(MediaSource)}
   *
   * This restarts a new data collection session.
   *
   * @param restart - if prepare call is to restart for a playback error set this flag true
   */
  public void startingPrepare(boolean restart) {
    if (! restart) {
      resetStats();
    }
  }

  private boolean isVideoTrack(MediaLoadData loadData) {
    return  loadData.trackType == C.TRACK_TYPE_VIDEO || isVideoFormat(loadData.trackFormat);
  }

  private boolean isAudioOnlyTrack(MediaLoadData loadData) {
    return loadData.trackType == C.TRACK_TYPE_AUDIO || isAudioOnlyFormat(loadData.trackFormat);
  }

  private boolean isVideoFormat(Format format) {
    boolean isVideo = false;
    if (format != null) {
      isVideo = format.height > 0 || MimeTypes.getVideoMediaMimeType(format.codecs) != null;
    }
    return isVideo;
  }

  private boolean isAudioOnlyFormat(Format format) {
    boolean isAudioOnly = false;
    if (format != null) {
      String[] codecs = Util.splitCodecs(format.codecs);
      isAudioOnly = codecs.length == 1 && MimeTypes.isAudio(MimeTypes.getMediaMimeType(codecs[0]))
              || MimeTypes.isAudio(format.sampleMimeType);
    }
    return isAudioOnly;
  }


  // Timer callback

  @Override
  public void run() {
    timedTextUpdate();
  }

  // Implement AnalyticsListener

  @Override
  public void onPlaybackStateChanged(EventTime eventTime, int state) {
    if (player != null) {
      int currentState = player.getPlaybackState();

      if (currentState == Player.STATE_BUFFERING) {
        bufferingCount++;
      }
      stateView.setText(getPlayerState());

      if (isGeekOverlayVisible()) {
        updateChildViews();
      }
    }
  }

  @Override
  public void onDownstreamFormatChanged(EventTime eventTime, MediaLoadData mediaLoadData) {
    if (isVideoTrack(mediaLoadData)) {
      lastDownstreamVideoFormat = mediaLoadData.trackFormat;
    }
  }

  public void onMediaItemTransition(EventTime eventTime, @Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason) {
    if (mediaItem != null && mediaItem.playbackProperties != null) {
      resetStats();
      manifestUrl.setText(mediaItem.playbackProperties.uri.toString());
    } else {
      manifestUrl.setText("");
    }
  }

  @Override
  public void onBandwidthEstimate(EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {

    float avgMbps = bitrateEstimate / 1_000_000.0f;

    if (minBandwidth == Float.MIN_VALUE) {
      minBandwidth = avgMbps;
    } else {
      minBandwidth = Math.min(minBandwidth, avgMbps);
    }
    if (maxBandwidth == Float.MAX_VALUE) {
      maxBandwidth = avgMbps;
    } else {
      maxBandwidth = Math.max(maxBandwidth, avgMbps);
    }

    bandwidthStats.setText(String.format(Locale.getDefault(), "%.3f / %.3f / %.3f Mbps", avgMbps, minBandwidth, maxBandwidth));

    bandwidthGraph.addDataPoint(avgMbps, bandwidthTraceNum);

    if (lastDownstreamVideoFormat != null && lastDownstreamVideoFormat.bitrate != Format.NO_VALUE) {
      int bps = lastDownstreamVideoFormat.bitrate;
      float bitrateMbps = bps / 1_000_000.0f;

      bandwidthGraph.addDataPoint(bitrateMbps, levelBitrateTraceNum);
    } else {
      bandwidthGraph.addDataPoint(0.0f, levelBitrateTraceNum);
    }

  }

  @Override
  public void onLoadCompleted(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
    Format format = mediaLoadData.trackFormat;
    if (isVideoTrack(mediaLoadData)) {

      levelSwitchCount += format.equals(lastLoadedVideoFormat) ? 0 : 1;
      lastLoadedVideoFormat = format;

      if (loadEventInfo.loadDurationMs > 0) {
        long kbps = (loadEventInfo.bytesLoaded / loadEventInfo.loadDurationMs) * 8;
        loadingLevel.setText("ABRs: " + levelSwitchCount + "  Trk: " + getFormatString(format) + " - " + kbps + "kbps");
      }
    }
  }
}

