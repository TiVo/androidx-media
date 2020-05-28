package com.tivo.exoplayer.demo;// Copyright 2010 TiVo Inc.  All rights reserved.

import static android.media.AudioManager.ACTION_HDMI_AUDIO_PLUG;
import static android.media.AudioManager.ACTION_HEADSET_PLUG;
import static android.media.AudioManager.EXTRA_AUDIO_PLUG_STATE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
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
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.demo.TrackSelectionDialog;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.trickplay.TrickPlayEventListener;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ui.TimeBar;
import com.google.android.exoplayer2.util.Util;
import com.tivo.exoplayer.library.GeekStatsOverlay;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;
import com.tivo.exoplayer.library.tracks.TrackInfo;
import java.util.List;
import java.util.Locale;

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
  private @Nullable CaptioningManager.CaptioningChangeListener captionChangeListener;

  public ViewActivity() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      captionChangeListener = new CaptioningManager.CaptioningChangeListener() {
        @Override
        public void onEnabledChanged(boolean enabled) {
          exoPlayerFactory.setCloseCaption(enabled, null);
        }
      };
    }
  }
  private TimeBar timeBar;

  // Activity lifecycle

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Context context = getApplicationContext();

    SimpleExoPlayerFactory.initializeLogging(context, DEFAULT_LOG_LEVEL);
    exoPlayerFactory = new SimpleExoPlayerFactory(context);

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
    Locale current = Locale.getDefault();

    CaptioningManager captioningManager = null;
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT && captionChangeListener != null) {
      captioningManager = (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
      captioningManager.addCaptioningChangeListener(captionChangeListener);
      exoPlayerFactory.setCloseCaption(captioningManager.isEnabled(), current.getLanguage());
    }

    exoPlayerFactory.setPreferredAudioLanguage(current.getLanguage());

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

    if (intent.getAction().equals(ACTION_GEEK_STATS)) {
      geekStats.toggleVisible();
    } else {
      processIntent(intent);
    }
   }

  @Override
  public void onStart() {
    super.onStart();
    SimpleExoPlayerFactory.initializeLogging(getApplicationContext(), DEFAULT_LOG_LEVEL);

    SimpleExoPlayer player = exoPlayerFactory.createPlayer(true, false);

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
    processIntent(getIntent());
  }

  @Override
  public void onResume() {
    super.onResume();
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
     exoPlayerFactory.releasePlayer();
   }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (playerView != null) {
      playerView.setControllerVisibilityListener(null);
      playerView = null;
      exoPlayerFactory = null;
      geekStats = null;
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

          // TODO chunkless should come from a properties file (so we can switch it when it's supported)
          boolean enableChunkless = getIntent().getBooleanExtra(CHUNKLESS_PREPARE, false);
          exoPlayerFactory.playUrl(nextChannel, enableChunkless);
        }
      }
    }

    return handled || playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
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

    if (!Util.checkCleartextTrafficPermitted(uris)) {
      uris = new Uri[0];
      showToast(getString(R.string.error_cleartext_not_permitted));
    }

    boolean enableTunneling = getIntent().getBooleanExtra(ENABLE_TUNNELED_PLAYBACK, false);
    exoPlayerFactory.setTunnelingMode(enableTunneling);

    if (uris.length > 0) {
      Log.d(TAG, "calling playUrl - " + uris[0]);
      // TODO chunkless should come from a properties file (so we can switch it when it's supported)
      boolean enableChunkless = getIntent().getBooleanExtra(CHUNKLESS_PREPARE, false);
      exoPlayerFactory.playUrl(uris[0], enableChunkless);
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

  private void showToast(String message) {
    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
  }

}
