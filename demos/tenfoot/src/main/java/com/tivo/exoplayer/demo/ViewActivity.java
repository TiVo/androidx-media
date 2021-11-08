package com.tivo.exoplayer.demo;// Copyright 2010 TiVo Inc.  All rights reserved.

import static android.media.AudioManager.ACTION_HDMI_AUDIO_PLUG;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.CaptioningManager;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.demo.TrackSelectionDialog;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.UnrecognizedInputFormatException;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.trickplay.TrickPlayEventListener;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.ui.TimeBar;
import com.tivo.exoplayer.library.DrmInfo;
import com.tivo.exoplayer.library.GeekStatsOverlay;
import com.tivo.exoplayer.library.OutputProtectionMonitor;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;
import com.tivo.exoplayer.library.SourceFactoriesCreated;
import com.tivo.exoplayer.library.VcasDrmInfo;
import com.tivo.exoplayer.library.WidevineDrmInfo;
import com.tivo.exoplayer.library.errorhandlers.PlaybackExceptionRecovery;
import com.tivo.exoplayer.library.errorhandlers.UnsupportedVideoFormatsException;
import com.tivo.exoplayer.library.logging.ExtendedEventLogger;
import com.tivo.exoplayer.library.metrics.ManagePlaybackMetrics;
import com.tivo.exoplayer.library.metrics.PlaybackMetricsManagerApi;
import com.tivo.exoplayer.library.tracks.TrackInfo;
import java.io.File;
import java.io.IOException;
import com.tivo.exoplayer.library.util.AccessibilityHelper;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

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
  private DrmInfo drmInfo;

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
  public static final String START_PLAYING = "start_playing";
  public static final String SHOW_GEEK_STATS = "show_geek";
  public static final String DRM_SCHEME = "drm_scheme";
  public static final String DRM_VCAS_CA_ID = "vcas_ca_id";
  public static final String DRM_VCAS_ADDR = "vcas_addr";
  public static final String DRM_WV_PROXY = "wv_proxy";
  public static final String DRM_VCAS_VUID = "vcas_vuid";

  public static final String DRM_SCHEME_VCAS = "vcas";
  public static final String DRM_SCHEME_WIDEVINE = "widevine";

  protected Uri[] uris;

  private int currentChannel;
  private Uri[] channelUris;

  private boolean isTrickPlaybarShowing = false;

  private boolean isAudioRenderOn = true;
  private PlaybackMetricsManagerApi statsManager;


  private class ScrubHandler implements TimeBar.OnScrubListener {

    private final TrickPlayControl control;

    ScrubHandler(TrickPlayControl control) {
      this.control = control;
    }

    @Override
    public void onScrubStart(TimeBar timeBar, long position) {
      control.setTrickMode(TrickPlayControl.TrickMode.SCRUB);
    }

    @Override
    public void onScrubMove(TimeBar timeBar, long position) {
      control.scrubSeek(position);
    }

    @Override
    public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
      control.setTrickMode(TrickPlayControl.TrickMode.NORMAL);
    }
  }

  private @Nullable ScrubHandler currentScrubHandler;

  private OutputProtectionMonitor outputProtectionMonitor;

  private GeekStatsOverlay geekStats;
  private AccessibilityHelper accessibilityHelper;

  private TimeBar timeBar;

  private Uri currentUri;

  // Activity lifecycle
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.d(TAG, "onCreate() called");

    android.util.Log.i(TAG, SimpleExoPlayerFactory.VERSION_INFO);
    Context context = getApplicationContext();

    SimpleExoPlayerFactory.initializeLogging(context, DEFAULT_LOG_LEVEL);
    exoPlayerFactory = new SimpleExoPlayerFactory.Builder(context)
            .setPlaybackErrorHandlerListener((error, status) -> {
              switch (status) {
                case IN_PROGRESS:
                  Log.d(TAG, "playerErrorProcessed() - error: " + error.getMessage() + " status: " + status);
                  if (PlaybackExceptionRecovery.isBehindLiveWindow(error)) {
                    Toast.makeText(getApplicationContext(), "Recovering Behind Live Window", Toast.LENGTH_LONG).show();
                  } else if (PlaybackExceptionRecovery.isPlaylistStuck(error)) {
                    Toast.makeText(getApplicationContext(), "Retrying stuck playlist", Toast.LENGTH_LONG).show();
                  } else {
                    Toast.makeText(getApplicationContext(), error.getMessage(), Toast.LENGTH_LONG).show();
                  }
                  break;

                case SUCCESS:
                  Log.d(TAG, "playerErrorProcessed() - recovered from " + error.getMessage());
                  break;

                case WARNING:
                  if (error.type == ExoPlaybackException.TYPE_RENDERER) {
                    @RendererCapabilities.FormatSupport int formatSupport = error.rendererFormatSupport;
                    String reason = "format: " + Format.toLogString(error.rendererFormat) + ", ";
                    switch (formatSupport) {
                      case RendererCapabilities.FORMAT_EXCEEDS_CAPABILITIES:
                        reason = "Exceeds Capabilities";
                        break;
                      case RendererCapabilities.FORMAT_HANDLED:
                        break;
                      case RendererCapabilities.FORMAT_UNSUPPORTED_DRM:
                        reason = "Unsupported DRM";
                        break;
                      case RendererCapabilities.FORMAT_UNSUPPORTED_SUBTYPE:
                        reason = "Unsupported Subtype";
                        break;
                      case RendererCapabilities.FORMAT_UNSUPPORTED_TYPE:
                        reason = "Unsupported Type";
                        break;
                    }
                    ViewActivity.this.showError("No supported video tracks, " + reason, error);

                  } else {
                    ViewActivity.this.showError("Un-excpected playback error", error);
                  }
                  if (statsManager != null) {
                    statsManager.endAllSessions();
                  }
                  break;

                case FAILED:
                  ViewActivity.this.showError("Un-recovered Playback Error", error);
                  if (statsManager != null) {
                    statsManager.endAllSessions();
                  }
                  break;
              }
            })
            .setEventListenerFactory(new SimpleExoPlayerFactory.EventListenerFactory() {
              @Override
              public AnalyticsListener createEventLogger(MappingTrackSelector trackSelector) {
                return new ExtendedEventLogger(trackSelector);
              }
            })
            .setSourceFactoriesCreatedCallback(new SourceFactoriesCreated() {
              @Override
              public void factoriesCreated(@C.ContentType int type, MediaItem.Builder itemBuilder, MediaSourceFactory factory) {
                switch (type) {

                  case C.TYPE_DASH:
                    break;
                  case C.TYPE_HLS:
                    HlsMediaSource.Factory hlsFactory = (HlsMediaSource.Factory) factory;
                    boolean allowChunkless = getIntent().getBooleanExtra(CHUNKLESS_PREPARE, false);
                    hlsFactory.setAllowChunklessPreparation(allowChunkless);
                    break;
                  case C.TYPE_OTHER:
                    break;
                  case C.TYPE_SS:
                    break;
                }
              }
            })
            .setUserAgentPrefix("TenFootDemo")
            .build();

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
    playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
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

    final OutputProtectionMonitor.ProtectionChangedListener opmStateCallback = (isSecure) ->
            Log.i(TAG, "Output protection is: " + (isSecure ? "ON" : "OFF"));

    outputProtectionMonitor = new OutputProtectionMonitor(context, OutputProtectionMonitor.HDCP_1X, opmStateCallback);
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
    DefaultLoadControl.Builder builder = new DefaultLoadControl.Builder();
    builder.setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
            1000,   // Faster channel change
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS);
    SimpleExoPlayer player = exoPlayerFactory.createPlayer(false, false, builder);
    player.setAudioAttributes(AudioAttributes.DEFAULT,true);

    TrickPlayControl trickPlayControl = exoPlayerFactory.getCurrentTrickPlayControl();
    currentScrubHandler = new ScrubHandler(trickPlayControl);
    timeBar.addListener(currentScrubHandler);

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


    statsManager = new ManagePlaybackMetrics.Builder(player, trickPlayControl).build();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        outputProtectionMonitor.start();
    }

    processIntent(getIntent());
  }

  @Override
  public void onResume() {
    super.onResume();
    Log.d(TAG, "onResume() called");
    IntentFilter filter = new IntentFilter();
    filter.addAction(ACTION_HDMI_AUDIO_PLUG);
  }

   @Override
   public void onPause() {
     super.onPause();
   }

   @Override
   public void onStop() {
     super.onStop();
     if (currentScrubHandler != null) {
       timeBar.removeListener(currentScrubHandler);
     }
     currentScrubHandler = null;
     Log.d(TAG, "onStop() called");
     if (statsManager != null) {
       statsManager.endAllSessions();
     }
     exoPlayerFactory.releasePlayer();

     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        outputProtectionMonitor.stop();
     }

   }

  protected void stopPlaybackIfPlaying() {
    SimpleExoPlayer currentPlayer = exoPlayerFactory.getCurrentPlayer();
    if (currentPlayer != null) {
      int currentState = currentPlayer.getPlaybackState();
      if (currentState == Player.STATE_BUFFERING || currentState == Player.STATE_READY) {
        Log.d(TAG, "Stopping playback with player in state: " + currentState);
        currentPlayer.stop(true);   // stop and reset position and state to idle
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
//            exoPlayerFactory.setRendererState(C.TRACK_TYPE_AUDIO, ! isAudioRenderOn);
//            isAudioRenderOn = ! isAudioRenderOn;

            geekStats.toggleVisible();
            List<TrackInfo> audioTracks = exoPlayerFactory.getAvailableAudioTracks();
            if (audioTracks.size() > 0) {
              DialogFragment dialog =
                  TrackInfoSelectionDialog
                      .createForChoices("Select Audio", audioTracks, exoPlayerFactory);
              dialog.show(getSupportFragmentManager(), null);
            }
            handled = true;
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


    currentUri = uri;
    outputProtectionMonitor.refreshState();
    stopPlaybackIfPlaying();
    long seekTo =  getIntent().getIntExtra(INITIAL_SEEK, C.POSITION_UNSET);
    boolean playWhenReady =  getIntent().getBooleanExtra(START_PLAYING, true);
    Log.d(TAG, "playUri() playUri: '" + uri + "' - chunkless: " + enableChunkless + " initialPos: " + seekTo + " playWhenReady: " + playWhenReady);
    try {
      exoPlayerFactory.playUrl(uri, drmInfo, seekTo, playWhenReady);
    } catch (UnrecognizedInputFormatException e) {
      showError("Can't play URI: " + uri, e);
    }
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

    boolean showGeekStats = getIntent().getBooleanExtra(SHOW_GEEK_STATS, true);
    if (! showGeekStats) {
      geekStats.toggleVisible();
    }

    boolean enableTunneling = getIntent().getBooleanExtra(ENABLE_TUNNELED_PLAYBACK, false);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      exoPlayerFactory.setTunnelingMode(enableTunneling);
    }

    if (DRM_SCHEME_VCAS.equals(getIntent().getStringExtra(DRM_SCHEME))) {
      String vcasAddr = getIntent().getStringExtra(DRM_VCAS_ADDR);
      String vcasCaId = getIntent().getStringExtra(DRM_VCAS_CA_ID);
      String storeDir = "/sdcard/demoVR";
      File vcasStoreDir = getApplicationContext().getExternalFilesDir("VCAS");
      if (! vcasStoreDir.exists()) {
        vcasStoreDir.mkdirs();
      }
      try {
        storeDir = vcasStoreDir.getCanonicalPath();
      } catch (IOException e) {
        Log.e(TAG, "Failed to open VCAS storage directory.", e);
      }

      Log.d(TAG, String.format("Requested Verimatrix DRM with addr:%s CAID:%s storage:%s", vcasAddr, vcasCaId, storeDir));
      drmInfo = new VcasDrmInfo(vcasAddr, vcasCaId, storeDir, true);
    } else if (DRM_SCHEME_WIDEVINE.equals(getIntent().getStringExtra(DRM_SCHEME))) {
      String wvProxy = getIntent().getStringExtra(DRM_WV_PROXY);
      String vuid = getIntent().getStringExtra(DRM_VCAS_VUID);

      Log.d(TAG, String.format("Requested Widevine DRM with addr:%s VUID:%s", wvProxy, vuid));

      Map<String, String> keyRequestProps = new HashMap<String, String>();

      if (vuid != null) {
          keyRequestProps.put( "deviceId", vuid );
      }
      drmInfo = new WidevineDrmInfo(wvProxy, keyRequestProps, true);
    } else {
      drmInfo = new DrmInfo(DrmInfo.CLEAR);
    }


    if (uris.length > 0) {
      setIntent(intent);
      playUri(uris[0]);
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
