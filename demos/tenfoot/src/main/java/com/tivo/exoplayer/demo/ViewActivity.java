package com.tivo.exoplayer.demo;// Copyright 2010 TiVo Inc.  All rights reserved.

import static android.media.AudioManager.ACTION_HDMI_AUDIO_PLUG;
import static android.media.AudioManager.ACTION_HEADSET_PLUG;
import static android.media.AudioManager.EXTRA_AUDIO_PLUG_STATE;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.CaptioningManager;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.analytics.PlaybackStatsListener;
import com.google.android.exoplayer2.demo.TrackSelectionDialog;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.trickplay.TrickPlayEventListener;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.ui.TimeBar;
import com.google.android.exoplayer2.util.EventLogger;
import com.tivo.exoplayer.library.GeekStatsOverlay;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;
import com.tivo.exoplayer.library.tracks.TrackInfo;
import com.tivo.exoplayer.library.util.AccessibilityHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Example player that uses a "ten foot" UI, that is majority of the UX is controlled by
 * media controls on an Android STB remote.
 *
 * The activity plays either a single URL (intent data is simply a string with the URL) or a list of URL's
 * (intent data "uri_list" is a list of URL's).  Switching URL's is via the channel up/down buttons (
 */
public class ViewActivity extends AppCompatActivity implements PlayerControlView.VisibilityListener {

  public static final String TAG = "ExoDemo";
  private PlayerView playerView;

  private boolean isShowingTrackSelectionDialog;

  private SimpleExoPlayerFactory exoPlayerFactory;

  public static final Integer DEFAULT_LOG_LEVEL = com.google.android.exoplayer2.util.Log.LOG_LEVEL_ALL;

  // Intents
  public static final String ACTION_VIEW = "com.tivo.exoplayer.action.VIEW";
  public static final String ACTION_VIEW_LIST = "com.tivo.exoplayer.action.VIEW_LIST";
  public static final String ACTION_GEEK_STATS = "com.tivo.exoplayer.action.GEEK_STATS";
  public static final String ACTION_STOP = "com.tivo.exoplayer.action.STOP_PLAYBACK";

  // Intent data
  public static final String ENABLE_TUNNELED_PLAYBACK = "enable_tunneled_playback";
  public static final String URI_LIST_EXTRA = "uri_list";
  public static final String CHUNKLESS_PREPARE = "chunkless";
  public static final String INITIAL_SEEK = "start_at";
  protected Uri[] uris;

  private int currentChannel;
  private Uri[] channelUris;

  private int initialSeek = C.POSITION_UNSET;

  private boolean isTrickPlaybarShowing = false;

  private boolean isAudioRenderOn = true;

  private BroadcastReceiver hdmiHotPlugReceiver = new BroadcastReceiver() {

    @Override
    public void onReceive(Context context, Intent intent) {
      // pause video
      String action = intent.getAction();

      switch (action) {
        case ACTION_HDMI_AUDIO_PLUG :
          int plugState = intent.getIntExtra(EXTRA_AUDIO_PLUG_STATE, -1);
          switch (plugState) {
            case 1:
              Log.d("HDMI", "HDMI Hotplug - plugged in");
              WindowManager windowManager = (WindowManager) context.getSystemService(WINDOW_SERVICE);
              int displayFlags = 0;
              if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                displayFlags = windowManager.getDefaultDisplay().getFlags();
              }
              if ((displayFlags & (Display.FLAG_SECURE | Display.FLAG_SUPPORTS_PROTECTED_BUFFERS)) !=
                  ((Display.FLAG_SECURE | Display.FLAG_SUPPORTS_PROTECTED_BUFFERS))) {
                Log.d("HDMI", "Insecure display plugged in");
              } else {
                Log.d("HDMI", "Secure display plugged in - flags: " + displayFlags);
              }
              break;

            case 0:
              // TODO - disable audio track to allow playback in QE test environment
              break;

          }
          break;

        case ACTION_HEADSET_PLUG:
          // TODO - might want to alter audio path on some platforms for this
          break;

      }
    }
  };
  private GeekStatsOverlay geekStats;
  private PlaybackStatsListener playbackStats;
  private AccessibilityHelper accessibilityHelper;

  private TimeBar timeBar;

  private Uri currentUri;


  // Adapter Back-port of the two dev-v2 inner-classes from PlaybackStats
  public static final class EventTimeAndFormat {
    public final AnalyticsListener.EventTime eventTime;
    @Nullable public final Format format;

    private EventTimeAndFormat(AnalyticsListener.EventTime eventTime, @Nullable Format format) {
      this.eventTime = eventTime;
      this.format = format;
    }
    public static List<EventTimeAndFormat> fromPairList(List<Pair<AnalyticsListener.EventTime, Format>> pairList) {
      List<EventTimeAndFormat> list = new ArrayList<>(pairList.size());
      for (Pair<AnalyticsListener.EventTime, Format> pair : pairList) {
        list.add(new EventTimeAndFormat(pair.first, pair.second));
      }
      return list;
    }
  }
  public static final class EventTimeAndPlaybackState {
    public final AnalyticsListener.EventTime eventTime;
    public final int playbackState;

    public EventTimeAndPlaybackState(AnalyticsListener.EventTime eventTime, int playbackState) {
      this.eventTime = eventTime;
      this.playbackState = playbackState;
    }
    public static List<EventTimeAndPlaybackState> fromPairList(List<Pair<AnalyticsListener.EventTime, Integer>> pairList) {
      List<EventTimeAndPlaybackState> list = new ArrayList<>(pairList.size());
      for (Pair<AnalyticsListener.EventTime, Integer> pair : pairList) {
        list.add(new EventTimeAndPlaybackState(pair.first, pair.second));
      }
      return list;
    }
  }
  // Activity lifecycle

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.d(TAG, "onCreate() called");

    Context context = getApplicationContext();

    SimpleExoPlayerFactory.initializeLogging(context, DEFAULT_LOG_LEVEL);
    exoPlayerFactory = new SimpleExoPlayerFactory(context, (eventTime, error, recovered) -> {
      if (! recovered) {
        showError("Un-recovered Playback Error", error);
      }
    });

    LayoutInflater inflater = LayoutInflater.from(context);
    ViewGroup activityView = (ViewGroup) inflater.inflate(R.layout.view_activity, null);
    View debugView = inflater.inflate(R.layout.stats_for_geeks, null);

    setContentView(activityView);
    activityView.addView(debugView);

    View debugContainer = debugView.findViewById(R.id.geek_stats);
    geekStats = new GeekStatsOverlay(debugContainer, context);

    playerView = findViewById(R.id.player_view);
    playerView.setControllerVisibilityListener(this);
    playerView.requestFocus();
    timeBar = playerView.findViewById(R.id.exo_progress);
    timeBar.setKeyTimeIncrement(10_000);

    playerView.setControllerAutoShow(false);
    playerView.setControllerShowTimeoutMs(-1);

    accessibilityHelper = new AccessibilityHelper(context, new AccessibilityHelper.AccessibilityStateChangeListener() {
      @Override
      public void captionStateChanged(boolean enabled, Locale captionLanguage) {
        exoPlayerFactory.setCloseCaption(enabled, captionLanguage.getLanguage());
      }

      @Override
      public void captionStyleChanged(CaptioningManager.CaptionStyle style, float fontScale) {
        // Subtitle view.
        SubtitleView subtitleView = findViewById(R.id.exo_subtitles);
        if (subtitleView != null) {   // set "defaults", which it fetches CaptioningManager
          subtitleView.setUserDefaultStyle();
          subtitleView.setUserDefaultTextSize();
        }
      }
    });

    exoPlayerFactory.setPreferredAudioLanguage(Locale.getDefault().getLanguage());

    exoPlayerFactory.setMediaSourceEventCallback((mediaSource, player) -> {
      if (initialSeek != C.POSITION_UNSET) {
        boundedSeekTo(player, exoPlayerFactory.getCurrentTrickPlayControl(), initialSeek);
        initialSeek = C.POSITION_UNSET;
      }
    });
  }

  @Override
   public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    String action = intent.getAction();
    action = action == null ? "" : action;
    switch (action) {
      case ACTION_GEEK_STATS:
        geekStats.toggleVisible();
        break;

      case ACTION_STOP:
        stopPlaybackIfPlaying();
        break;

      default:
        processIntent(intent);
        break;
    }
   }

  @Override
  public void onStart() {
    super.onStart();
    SimpleExoPlayerFactory.initializeLogging(getApplicationContext(), DEFAULT_LOG_LEVEL);
    Log.d(TAG, "onStart() called");
    SimpleExoPlayer player = exoPlayerFactory.createPlayer(false, false);

    TrickPlayControl trickPlayControl = exoPlayerFactory.getCurrentTrickPlayControl();
    trickPlayControl.addEventListener(new TrickPlayEventListener() {
      @Override
      public void playlistMetadataValid(boolean isMetadataValid) {
        if (isMetadataValid) {
          Log.d(TAG, "Trick play metadata valid, iframes supported: " + trickPlayControl.isSmoothPlayAvailable());
        } else {
          Log.d(TAG, "Trick play metadata invalidated");
        }
      }
    });

    playerView.setPlayer(player);
    geekStats.setPlayer(player, trickPlayControl);
    playbackStats = new PlaybackStatsListener(true, new PlaybackStatsListener.Callback() {
      @Override
      public void onPlaybackStatsReady(AnalyticsListener.EventTime eventTime, PlaybackStats playbackStats) {
        logPlaybackStats(eventTime, playbackStats);
      }
    });
    player.addAnalyticsListener(playbackStats);

    processIntent(getIntent());
  }

  protected void logPlaybackStats(AnalyticsListener.EventTime readTime, PlaybackStats stats) {
    StringBuilder sb = new StringBuilder();
    sb.append("\n  ");
    sb.append("total time:  ");
    sb.append(stats.getTotalElapsedTimeMs());sb.append("ms");
    sb.append("\n  ");
    sb.append("startup time:  ");
    sb.append(stats.getMeanJoinTimeMs());sb.append("ms");
    sb.append("\n  ");
    sb.append("playing time:  ");
    sb.append(stats.getTotalPlayTimeMs());sb.append("ms");
    sb.append("\n  ");
    sb.append("paused time:  ");
    sb.append(stats.getTotalPausedTimeMs());sb.append("ms");
    sb.append("\n  ");
    sb.append("re-buffering time:  ");
    sb.append(stats.getTotalRebufferTimeMs());sb.append("ms");
    sb.append("\n  ");
    sb.append("format changes:  ");
    sb.append(stats.videoFormatHistory.size());
    sb.append("\n  ");
    sb.append("mean bandwidth: ");
    sb.append(stats.getMeanBandwidth() / 1_000_000.0f);
    sb.append(" Mbps");

    HashMap<Format, Long> timeInFormat = new HashMap<>();
    long lastFormatChangeTime = C.TIME_UNSET;
    EventTimeAndFormat lastFormatEvent = null;
    for (EventTimeAndFormat formatEvent : EventTimeAndFormat.fromPairList(stats.videoFormatHistory)) {
      Long time = timeInFormat.get(formatEvent.format);
      if (time == null) {
        time = 0L;
      }
      if (lastFormatChangeTime != C.TIME_UNSET) {
        time += formatEvent.eventTime.realtimeMs - lastFormatChangeTime;
      }
      timeInFormat.put(formatEvent.format, time);
      lastFormatChangeTime = formatEvent.eventTime.realtimeMs;
      lastFormatEvent = formatEvent;
    }

    long playbackStopTime = findLastStoppedOrEnded(stats);

    // Balance of time is in last format (note could be playing, buffering or paused)
    if (playbackStopTime != C.TIME_UNSET) {
      Long time = timeInFormat.get(lastFormatEvent.format);
      time += playbackStopTime - lastFormatChangeTime;
      timeInFormat.put(lastFormatEvent.format, time);
    }

    Log.d(TAG, "Playback stats for '" + currentUri + "'" + sb.toString());

    if (stats.videoFormatHistory.size() > 0) {
      Log.d(TAG, "Time in variant:");
      for (Map.Entry<Format, Long> entry : timeInFormat.entrySet()) {
        Log.d(TAG, "  " + EventLogger.getVideoLevelStr(entry.getKey()) + " played for " + entry.getValue() + " ms total");
      }
    }
  }

  private static long findLastStoppedOrEnded(PlaybackStats stats) {
    List <EventTimeAndPlaybackState> stateHistory = EventTimeAndPlaybackState.fromPairList(stats.playbackStateHistory);
    for (int i = stateHistory.size() - 1; i > 0; i--) {
      EventTimeAndPlaybackState endEvent = stateHistory.get(i);
      if (endEvent.playbackState == PlaybackStats.PLAYBACK_STATE_ENDED ||
          endEvent.playbackState == PlaybackStats.PLAYBACK_STATE_STOPPED) {
        return endEvent.eventTime.realtimeMs;
      }
    }
    return C.TIME_UNSET;
  }

  @Override
  public void onResume() {
    super.onResume();
    Log.d(TAG, "onResume() called");
    IntentFilter filter = new IntentFilter();
    filter.addAction(ACTION_HDMI_AUDIO_PLUG);
    registerReceiver(hdmiHotPlugReceiver, filter);
  }

   @Override
   public void onPause() {
     super.onPause();
     unregisterReceiver(hdmiHotPlugReceiver);
   }

   @Override
   public void onStop() {
     super.onStop();
     Log.d(TAG, "onStop() called");
     stopPlaybackIfPlaying();
     exoPlayerFactory.releasePlayer();
   }

  protected void stopPlaybackIfPlaying() {
    SimpleExoPlayer currentPlayer = exoPlayerFactory.getCurrentPlayer();
    if (currentPlayer != null) {
      if (! (currentPlayer.getPlaybackState() == Player.STATE_ENDED || currentPlayer.getPlaybackState() == Player.STATE_IDLE)) {
        Log.d(TAG, "Stop and dump stats for current playback URI: '" + currentUri + "'");
        currentPlayer.stop();
        playbackStats.finishAllSessions();
      }
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (playerView != null) {
      playerView.setControllerVisibilityListener(null);
      playerView = null;
      exoPlayerFactory = null;
      geekStats = null;
      playbackStats = null;
    }
  }

// PlayerView listener

  @Override
  public void onVisibilityChange(int visibility) {
    isTrickPlaybarShowing = visibility == View.VISIBLE;
  }

  // Internal

  private boolean boundedSeekTo(SimpleExoPlayer player,
      TrickPlayControl trickPlayControl,
      long targetPositionMs) {
    if (targetPositionMs != C.TIME_UNSET) {
      targetPositionMs = Math.min(targetPositionMs, trickPlayControl.getLargestSafeSeekPositionMs());
    }
    targetPositionMs = Math.max(targetPositionMs, 0);
    player.seekTo(targetPositionMs);
    return true;
  }


  // UI

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    boolean handled = false;
    Uri nextChannel = null;

    TrickPlayControl trickPlayControl = exoPlayerFactory.getCurrentTrickPlayControl();
    SimpleExoPlayer player = exoPlayerFactory.getCurrentPlayer();

    if (trickPlayControl == null || player == null) {
      handled = false;
    } else {

      DefaultTrackSelector trackSelector = exoPlayerFactory.getTrackSelector();

      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        int keyCode = event.getKeyCode();

        if (keyCode != KeyEvent.KEYCODE_INFO) {
          playerView.showController();
        }

        switch (keyCode) {

          case KeyEvent.KEYCODE_INFO:
            toggleTrickPlayBar();
            break;

          case KeyEvent.KEYCODE_0:
            if (!isShowingTrackSelectionDialog && trackSelector != null && TrackSelectionDialog
                .willHaveContent(trackSelector)) {
              isShowingTrackSelectionDialog = true;
              TrackSelectionDialog trackSelectionDialog =
                  TrackSelectionDialog.createForTrackSelector(
                      trackSelector,
                      /* onDismissListener= */
                      dismissedDialog -> isShowingTrackSelectionDialog = false);
              trackSelectionDialog.show(getSupportFragmentManager(), /* tag= */ null);
            }
            handled = true;
            break;

          case KeyEvent.KEYCODE_F11:
            Intent intent = new Intent(Settings.ACTION_CAPTIONING_SETTINGS);
            startActivityForResult(intent, 1);
            handled = true;
            break;

          case KeyEvent.KEYCODE_MEDIA_STEP_FORWARD:

            long targetPositionMs = player.getCurrentPosition();

            int currentPositionMinutes = (int) Math.floor(player.getCurrentPosition() / 60_000);
            int minutesIntoPeriod = currentPositionMinutes % 15;
            switch (trickPlayControl.getCurrentTrickDirection()) {
              case FORWARD:
                targetPositionMs = (currentPositionMinutes + (15 - minutesIntoPeriod)) * 60_000;
                break;

              case REVERSE:
                targetPositionMs = (currentPositionMinutes - (15 - minutesIntoPeriod)) * 60_000;
                break;

              case NONE:
                targetPositionMs = player.getCurrentPosition() + 20_000;
                break;
            }

            boundedSeekTo(player, trickPlayControl, targetPositionMs);

            handled = true;

            break;

          case KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD:
            if (trickPlayControl.getCurrentTrickMode() == TrickPlayControl.TrickMode.NORMAL) {
                boundedSeekTo(player, trickPlayControl, player.getContentPosition() - 20_000);
            }
            handled = true;
            break;

          case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
          case KeyEvent.KEYCODE_MEDIA_REWIND:
            TrickPlayControl.TrickMode newMode = nextTrickMode(trickPlayControl.getCurrentTrickMode(), keyCode);

            if (trickPlayControl.setTrickMode(newMode) >= 0) {
              Log.d(TAG, "request to play trick-mode " + newMode + " succeeded");
            } else {
              Log.d(TAG, "request to play trick-mode " + newMode + " not possible");
            }
            handled = true;
            break;

          case KeyEvent.KEYCODE_3:
            trickPlayControl.setTrickMode(TrickPlayControl.TrickMode.FF1);
            handled = true;
            break;

          case KeyEvent.KEYCODE_1:
            trickPlayControl.setTrickMode(TrickPlayControl.TrickMode.FR1);
            handled = true;
            break;

          case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
          case KeyEvent.KEYCODE_2:
            if (trickPlayControl.getCurrentTrickMode() != TrickPlayControl.TrickMode.NORMAL) {
              trickPlayControl.setTrickMode(TrickPlayControl.TrickMode.NORMAL);
              handled = true;
            }
            break;

          case KeyEvent.KEYCODE_6:
            trickPlayControl.setTrickMode(TrickPlayControl.TrickMode.FF2);
            handled = true;
            break;

          case KeyEvent.KEYCODE_4:
            trickPlayControl.setTrickMode(TrickPlayControl.TrickMode.FR2);
            handled = true;
            break;

          case KeyEvent.KEYCODE_5:
            exoPlayerFactory.setRendererState(C.TRACK_TYPE_AUDIO, ! isAudioRenderOn);
            isAudioRenderOn = ! isAudioRenderOn;

//            geekStats.toggleVisible();
//            List<TrackInfo> audioTracks = exoPlayerFactory.getAvailableAudioTracks();
//            if (audioTracks.size() > 0) {
//              DialogFragment dialog =
//                  TrackInfoSelectionDialog
//                      .createForChoices("Select Audio", audioTracks, exoPlayerFactory);
//              dialog.show(getSupportFragmentManager(), null);
//            }
//            handled = true;
            break;

          case KeyEvent.KEYCODE_8:
            List<TrackInfo> textTracks = exoPlayerFactory.getAvailableTextTracks();
            if (textTracks.size() > 0) {
              DialogFragment dialog =
                  TrackInfoSelectionDialog
                      .createForChoices("Select Text", textTracks, exoPlayerFactory);
              dialog.show(getSupportFragmentManager(), null);
            }
            handled = true;
            break;

          case KeyEvent.KEYCODE_9:
            trickPlayControl.setTrickMode(TrickPlayControl.TrickMode.FF3);
            handled = true;
            break;

          case KeyEvent.KEYCODE_7:
            trickPlayControl.setTrickMode(TrickPlayControl.TrickMode.FR3);
            handled = true;
            break;

          case KeyEvent.KEYCODE_CHANNEL_DOWN:
            if (channelUris != null) {
              currentChannel = (currentChannel + (channelUris.length - 1)) % channelUris.length;
              nextChannel = channelUris[currentChannel];
              Log.d(TAG, "Channel change down to: " + nextChannel);
            }
            handled = true;
            break;
          case KeyEvent.KEYCODE_CHANNEL_UP:
            if (channelUris != null) {
              currentChannel = (currentChannel + 1) % channelUris.length;
              nextChannel = channelUris[currentChannel];
              Log.d(TAG, "Channel change up to: " + nextChannel);
            }
            handled = true;
            break;

          case KeyEvent.KEYCODE_LAST_CHANNEL:
              Timeline timeline = player.getCurrentTimeline();
              if (! timeline.isEmpty()) {
                Timeline.Window window = timeline.getWindow(player.getCurrentWindowIndex(), new Timeline.Window());
                boundedSeekTo(player, trickPlayControl , trickPlayControl.getLargestSafeSeekPositionMs() - 3000);
              }
          default:
            handled = false;
            break;
        }

        if (nextChannel != null) {
          playUri(nextChannel);

        }
      }
    }

    return handled || playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
  }

  protected void playUri(Uri uri) {
    // TODO chunkless should come from a properties file (so we can switch it when it's supported)
    boolean enableChunkless = getIntent().getBooleanExtra(CHUNKLESS_PREPARE, false);

    stopPlaybackIfPlaying();
    currentUri = uri;
    Log.d(TAG, "playUri() playUri: '" + uri + "' - chunkless: " + enableChunkless);
    exoPlayerFactory.playUrl(uri, enableChunkless);
  }

  private void toggleTrickPlayBar() {
    if (isTrickPlaybarShowing) {
      playerView.hideController();
    } else {
      playerView.showController();
    }
  }

  @Override
  public boolean onKeyLongPress(int keyCode, KeyEvent event) {
    return super.onKeyLongPress(keyCode, event);
  }

  /**
   * Get the UX expected trick mode if sequencing through the modes with a single media
   * forward or media rewind key.
   *
   * @param currentMode - current trickplay mode
   * @param keyCode - key event with indicated direction
   * @return next TrickMode to set
   */
  private static TrickPlayControl.TrickMode nextTrickMode(TrickPlayControl.TrickMode currentMode, int keyCode) {
    TrickPlayControl.TrickMode value;

    switch (keyCode) {
      case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
        switch (currentMode) {
          case NORMAL:
            value = TrickPlayControl.TrickMode.FF1;
            break;
          case FF1:
            value = TrickPlayControl.TrickMode.FF2;
            break;
          case FF2:
            value = TrickPlayControl.TrickMode.FF3;
            break;
          case FF3:    // FF3 keeps going in FF3
            value = TrickPlayControl.TrickMode.FF3;
            break;
          default:    // FR mode with FF keypress goes back to normal
            value = TrickPlayControl.TrickMode.NORMAL;
            break;
        }
        break;

      case KeyEvent.KEYCODE_MEDIA_REWIND:
        switch (currentMode) {
          case NORMAL:
            value = TrickPlayControl.TrickMode.FR1;
            break;
          case FR1:
            value = TrickPlayControl.TrickMode.FR2;
            break;
          case FR2:
            value = TrickPlayControl.TrickMode.FR3;
            break;
          case FR3:    // FR3 keeps going in FR3
            value = TrickPlayControl.TrickMode.FR3;
            break;
          default:    // FF mode with REW keypress goes back to normal
            value = TrickPlayControl.TrickMode.NORMAL;
            break;
        }
        break;

        default:
          throw new RuntimeException("nextTrickMode() must be on FF or REW button");
    }

    Log.d(TAG, "Trickplay in currentMode: " + currentMode + ", next is: " + value);

    return value;
  }

  // Internals


  // Internal methods

  private void processIntent(Intent intent) {
    String action = intent.getAction();
    uris = new Uri[0];
    currentChannel = 0;
    channelUris = null;

    String[] uriStrings = intent.getStringArrayExtra(URI_LIST_EXTRA);

    initialSeek = intent.getIntExtra(INITIAL_SEEK, C.POSITION_UNSET);

    if (ACTION_VIEW.equals(action)) {
      uris = new Uri[]{intent.getData()};
    } else if (ACTION_VIEW_LIST.equals(action)) {
      uris = parseToUriList(uriStrings);
      channelUris = uris;
      currentChannel = 0;
    } else {
      showToast(getString(R.string.unexpected_intent_action, action));
      finish();
    }

    boolean enableTunneling = getIntent().getBooleanExtra(ENABLE_TUNNELED_PLAYBACK, false);
    exoPlayerFactory.setTunnelingMode(enableTunneling);

    if (uris.length > 0) {
      playUri(uris[0]);
      setIntent(intent);
    }
  }


  // Utilities
  private Uri[] parseToUriList(String[] uriStrings) {
    Uri[] uris;
    uris = new Uri[uriStrings.length];
    for (int i = 0; i < uriStrings.length; i++) {
      uris[i] = Uri.parse(uriStrings[i]);
    }
    return uris;
  }

  private void showError(String message, @Nullable Exception exception) {
    AlertDialog alertDialog = new AlertDialog.Builder(this).create();
    alertDialog.setTitle("Error");
    if (exception != null) {
      message += " - " + exception.getMessage();
    }
    alertDialog.setMessage(message);
    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", (dialog, which) -> dialog.dismiss());
    alertDialog.show();
  }

  private void showToast(String message) {
    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
  }

}
