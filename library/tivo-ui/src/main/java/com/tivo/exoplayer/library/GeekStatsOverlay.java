package com.tivo.exoplayer.library;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.chrono.MinguoChronology;
import java.util.Date;
import java.util.Locale;

/**
 * Monitors playback session and powers the stats for geeks overlay for player
 * debug.
 *
 * To make this overlay visible send the intent {@link #DISPLAY_INTENT} to the demo
 * or other android application hosting the player that supports geeks display.
 *
 * To integrate this UI in your ExoPlayer application add a view with this
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

  private TextView stateView;
  private TextView currentTimeView;
  private TextView currentVideoTrack;
  private final TextView currentAudioTrack;
  private TextView loadingLevel;
  private final TextView playbackRate;

  private final int updateInterval;

  private Format lastLoadedVideoFormat;
  private Format lastDownstreamVideoFormat;
  private Format lastDownstreamAudioFormat;
  private long lastPositionReport;

  // Statistic counters, reset on url change
  private long lastTimeUpdate;
  private int levelSwitchCount = 0;
  private int lastPlayState = Player.STATE_IDLE;
  private float lastVideoDownloadbps;
  private float minBandwidth = Float.MIN_VALUE;
  private float maxBandwidth = Float.MAX_VALUE;


  public GeekStatsOverlay(View view, Context context, int updateInterval) {
    containingView = view;
    currentVideoTrack = view.findViewById(R.id.video_track);
    currentAudioTrack = view.findViewById(R.id.audio_track);
    loadingLevel = view.findViewById(R.id.loading_level);
    stateView = view.findViewById(R.id.current_state);
    currentTimeView = view.findViewById(R.id.current_time);
    playbackRate = view.findViewById(R.id.playback_rate);

    bufferingGraph = view.findViewById(R.id.buffering_graph);

    int traceColor = containingView.getResources().getColor(R.color.colorBuffered);
    bufferingGraph.addTraceLine(traceColor, 0, 70);

    bufferingLevel = view.findViewById(R.id.buffering_level);

    bandwidthStats = view.findViewById(R.id.bandwidth_stats);

    bandwidthGraph = view.findViewById(R.id.bandwidth_graph);

    // Add a line for current level bitrate (in Mbps) TODO - move to track select
    traceColor = containingView.getResources().getColor(R.color.colorLevel);
    levelBitrateTraceNum =
        bandwidthGraph.addTraceLine(traceColor, 0, 30);

    // Add a line for current bandwidth bitrate (in Mbps)
    traceColor = containingView.getResources().getColor(R.color.colorBandwidth);
    bandwidthTraceNum =
        bandwidthGraph.addTraceLine(traceColor, 0, 150);

    manifestUrl = view.findViewById(R.id.manifest_url);
    this.updateInterval = updateInterval;
  }

  public GeekStatsOverlay(View view, Context context) {
    this(view, context,1000);
  }

  /**
   * Call this to update the player and it's associated TrickPlayControl (if any)
   *
   * @param player current player, or null to remove old player
   * @param trickPlayControl trickplay control for the player (if any)
   */
  public void setPlayer(@Nullable SimpleExoPlayer player, TrickPlayControl trickPlayControl) {
    this.trickPlayControl = trickPlayControl;

    if (player == null && this.player != null) {
      stop();
      this.player = null;
    } else {
      this.player = player;
      start();
    }
  }

  public void toggleVisible() {
    int visibility = containingView.getVisibility();

    if (visibility == View.VISIBLE) {
      containingView.setVisibility(View.INVISIBLE);
      stop();
    } else {
      containingView.setVisibility(View.VISIBLE);
      start();
    }
  }

  private void start() {
    if (player != null) {
      player.addAnalyticsListener(this);
      timedTextUpdate();
    }
  }

  private void stop() {
    if (player != null) {
      player.removeAnalyticsListener(this);
    }
    containingView.removeCallbacks(this);
    resetStats();
  }

  private void timedTextUpdate() {
    currentTimeView.setText(getPositionString());
    currentVideoTrack.setText(getPlayingVideoTrack());
    currentAudioTrack.setText(getPlayingAudioTrack());
    playbackRate.setText(getPlaybackRateString());

    float buffered = getBufferedSeconds();
    bufferingGraph.addDataPoint(buffered, 0);
    bufferingLevel.setText(String.format(Locale.getDefault(), "%.2fs", buffered));
    containingView.removeCallbacks(this);
    containingView.postDelayed(this, updateInterval);
  }

  private float getBufferedSeconds() {
    float buffered = 0.0f;
    if (player != null) {
      long bufferedMs = player.getTotalBufferedDuration();
      buffered = bufferedMs / 1000.0f;
    }
    return buffered;
  }

  private void resetStats() {
    levelSwitchCount = 0;
    lastVideoDownloadbps = 0.0f;
    lastTimeUpdate = C.TIME_UNSET;
    minBandwidth = Float.MIN_VALUE;
    maxBandwidth = Float.MAX_VALUE;
    manifestUrl.setText("");
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
      Format format = lastDownstreamVideoFormat
          == null ? player.getVideoFormat() : lastDownstreamVideoFormat;

      trackInfo += getFormatString(format) + " - abr: " + levelSwitchCount + " ";

      trackInfo += getDecoderCountersString(player.getVideoDecoderCounters());
    }

    return trackInfo;
  }


  private CharSequence getPlayingAudioTrack() {
    String trackInfo = "A: ";

    if (player == null) {
      trackInfo += "<unknown>";
    } else {
      Format format =
          lastDownstreamAudioFormat == null ? player.getAudioFormat() : lastDownstreamAudioFormat;
      DecoderCounters decoderCounters = player.getAudioDecoderCounters();

      trackInfo += getFormatString(format) + " " + getDecoderCountersString(decoderCounters);
    }
    return trackInfo;
  }


  private String getDecoderCountersString(DecoderCounters decoderCounters) {
    String value = "";
    if (decoderCounters != null) {
      value += String.format(Locale.getDefault(), "(qib:%d db:%d mcdb:%d r:%d)",
          decoderCounters.inputBufferCount,
          decoderCounters.droppedBufferCount,
          decoderCounters.maxConsecutiveDroppedBufferCount,
          decoderCounters.renderedOutputBufferCount);
    }
    return value;
  }

  private String getFormatString(Format format) {
    String display = "<unknown>";

    if (format != null) {
      int bps = format.bitrate;
      float mbps = bps / 1_000_000.0f;

      if (isVideoFormat(format)) {
        if (bps == -1) {
          display = String.format(Locale.getDefault(),
              "id:(%s) - %dx%d@<unk>", format.id, format.width, format.height);
        } else {
          display = String.format(Locale.getDefault(),
              "id:(%s) - %dx%d@%.3f", format.id, format.width, format.height, mbps);
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
              "id:(%.10s) - %s@<unk>", format.id, audioType);
        } else {
          display = String.format(Locale.getDefault(),
              "id:(%.10s) - %s@%.3f", format.id, audioType, mbps);
        }
      }

    }
    return display;
  }

  protected String getPositionString() {
    String time = "";
    long position = 0L;

    if (player != null) {
      position = player.getCurrentPosition();

      Timeline timeline = player.getCurrentTimeline();
      if (! timeline.isEmpty()) {
        int windowIndex = player.getCurrentWindowIndex();
        Timeline.Window currentWindow = timeline.getWindow(windowIndex, new Timeline.Window());
        long absTime;
        DateFormat format;
        if (currentWindow.windowStartTimeMs == C.TIME_UNSET) {
          format = UTC_TIME;
          absTime = position;
        } else {
          format = UTC_DATETIME;
          absTime = currentWindow.windowStartTimeMs + position;
        }
        Date currentMediaTime = new Date(absTime);
        time = format.format(currentMediaTime);
      }

    }

    return time + " (" + position + ")";
  }


  protected String getPlaybackRateString() {
    float playbackRate = 0.0f;
    String rateString = "";

    if (lastTimeUpdate == C.TIME_UNSET) {
        lastTimeUpdate = System.currentTimeMillis();
    } else if (player != null && trickPlayControl != null) {
        long currentPosition = player.getCurrentPosition();
        long positionChange = Math.abs(lastPositionReport - currentPosition);
        long timeChange = System.currentTimeMillis() - lastTimeUpdate;
        lastTimeUpdate = System.currentTimeMillis();
        lastPositionReport = currentPosition;
        playbackRate = (float)positionChange / (float)timeChange;
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

  private boolean isVideoTrack(MediaSourceEventListener.MediaLoadData loadData) {
    return  loadData.trackType == C.TRACK_TYPE_VIDEO || isVideoFormat(loadData.trackFormat);
  }

  private boolean isAudioOnlyTrack(MediaSourceEventListener.MediaLoadData loadData) {
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
      isAudioOnly = codecs.length == 1 && MimeTypes.isAudio(MimeTypes.getMediaMimeType(codecs[0]));
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
  public void onPlayerStateChanged(EventTime eventTime, boolean playWhenReady, int playbackState) {
    if (player != null) {
      int currentState = player.getPlaybackState();

      if (lastPlayState != Player.STATE_IDLE && currentState == Player.STATE_IDLE) {
        resetStats();
      }
      lastPlayState = currentState;
      stateView.setText(getPlayerState());
      timedTextUpdate();
    }
  }

  @Override
  public void onDownstreamFormatChanged(EventTime eventTime, MediaSourceEventListener.MediaLoadData mediaLoadData) {
    if (isVideoTrack(mediaLoadData)) {
      lastDownstreamVideoFormat = mediaLoadData.trackFormat;
    } else if (isAudioOnlyTrack(mediaLoadData)) {
      lastDownstreamAudioFormat = mediaLoadData.trackFormat;
    }
  }

  @Override
  public void onTimelineChanged(EventTime eventTime, int reason) {
    Timeline.Window window = new Timeline.Window();
    if (eventTime.timeline.isEmpty()) {
      Log.d(TAG, "onTimeLineChanged - empty timeline");
    } else {
      eventTime.timeline.getWindow(0, window);
      long windowStartTimeMs = window.windowStartTimeMs;
      Log.d(TAG, "onTimeLineChanged - eventPlaybackPositionMs: " + eventTime.eventPlaybackPositionMs
          + " widowStartTime: " + UTC_DATETIME.format(windowStartTimeMs) + "(" + + windowStartTimeMs + ")");

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

    bandwidthStats.setText(String.format(Locale.getDefault(), "%.2f / %.2f / %.2f Mbps", avgMbps, minBandwidth, maxBandwidth));

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
  public void onLoadCompleted(EventTime eventTime, MediaSourceEventListener.LoadEventInfo loadEventInfo, MediaSourceEventListener.MediaLoadData mediaLoadData) {

    // Set the playlist URL, if this is the first manifest load (timeline is empty)
    boolean isEmpty = manifestUrl.getText().length() == 0;
    if (isEmpty && mediaLoadData.dataType == C.DATA_TYPE_MANIFEST) {
      manifestUrl.setText(loadEventInfo.dataSpec.uri.toString());
    }

    Format format = mediaLoadData.trackFormat;
    if (isVideoTrack(mediaLoadData)) {
      lastVideoDownloadbps = (loadEventInfo.bytesLoaded * 8_000.0f) / loadEventInfo.loadDurationMs;

      levelSwitchCount += format.equals(lastLoadedVideoFormat) ? 0 : 1;
      lastLoadedVideoFormat = format;

      long kbps = (loadEventInfo.bytesLoaded / loadEventInfo.loadDurationMs) * 8;
      loadingLevel.setText(getFormatString(format) + " - " + kbps + "kbps");
    }
  }
}
