package com.tivo.exoplayer.demo;// Copyright 2010 TiVo Inc.  All rights reserved.

import static android.media.AudioManager.ACTION_HDMI_AUDIO_PLUG;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
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
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.LivePlaybackSpeedControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.demo.TrackSelectionDialog;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.UnrecognizedInputFormatException;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistTracker;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.trickplay.TrickPlayEventListener;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.ui.TimeBar;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.streamingmediainsights.smiclientsdk.SMIClientSdk;
import com.streamingmediainsights.smiclientsdk.SMIEventCallbackListener;
import com.streamingmediainsights.smiclientsdk.SMISimpleExoPlayer;
import com.tivo.android.utils.SystemUtils;
import com.tivo.exoplayer.library.timebar.controllers.BaseTimeBarViewHandler;
import com.tivo.exoplayer.library.timebar.controllers.DPadToTransportBaseTimeBarViewHandler;
import com.tivo.exoplayer.library.timebar.controllers.HoldTimeChangesSpeedTimeBarViewHandler;
import com.tivo.exoplayer.library.timebar.controllers.ScrubOnlyTimeBarViewHandler;
import com.tivo.exoplayer.library.timebar.controllers.TransportControlHandler;
import com.tivo.exoplayer.library.timebar.views.DualModeTimeBar;
import com.tivo.exoplayer.library.DrmInfo;
import com.tivo.exoplayer.library.GeekStatsOverlay;
import com.tivo.exoplayer.library.OutputProtectionMonitor;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;
import com.tivo.exoplayer.library.SourceFactoriesCreated;
import com.tivo.exoplayer.library.VcasDrmInfo;
import com.tivo.exoplayer.library.WidevineDrmInfo;
import com.tivo.exoplayer.library.errorhandlers.PlaybackExceptionRecovery;
import com.tivo.exoplayer.library.logging.ExtendedEventLogger;
import com.tivo.exoplayer.library.metrics.ManagePlaybackMetrics;
import com.tivo.exoplayer.library.metrics.PlaybackMetrics;
import com.tivo.exoplayer.library.metrics.PlaybackMetricsManagerApi;
import com.tivo.exoplayer.library.tracks.SyncVideoTrackSelector;
import com.tivo.exoplayer.library.tracks.TrackInfo;
import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import com.tivo.exoplayer.library.util.AccessibilityHelper;

import java.util.ArrayList;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

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
public class ViewActivity extends AppCompatActivity {

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
  public static final String ACTION_SEEK = "com.tivo.exoplayer.action.SEEK_TO";
  public static final String ACTION_HEADERS = "com.tivo.exoplayer.action.SET_HEADERS";

  // Intent data
  public static final String ENABLE_TUNNELED_PLAYBACK = "enable_tunneled_playback";
  public static final String URI_LIST_EXTRA = "uri_list";
  public static final String CHUNKLESS_PREPARE = "chunkless";
  public static final String ENABLE_ASYNC_RENDER = "enable_async_renderer";
  public static final String INITIAL_SEEK = "start_at";
  public static final String SEEK_TO = "seek_to";
  public static final String SEEK_UNSAFE = "seek_unsafe";
  public static final String SEEK_NEAREST_SYNC = "seek_nearest";
  public static final String START_PLAYING = "start_playing";
  public static final String SHOW_GEEK_STATS = "show_geek";
  public static final String DRM_SCHEME = "drm_scheme";
  public static final String DRM_VCAS_CA_ID = "vcas_ca_id";
  public static final String DRM_VCAS_ADDR = "vcas_addr";
  public static final String DRM_WV_PROXY = "wv_proxy";
  public static final String DRM_VCAS_VUID = "vcas_vuid";
  public static final String LIVE_OFFSET = "live_offset";
  public static final String FAST_RESYNC = "fast_resync";

  public static final String DRM_SCHEME_VCAS = "vcas";
  public static final String DRM_SCHEME_WIDEVINE = "widevine";

  public static final String ENABLE_TRUSTREME_LOGGING = "enable_trustreme_logging";

  private static final String SCRUB_BEHAVIOR = "scrub_behavior";
  public static final String BEHAVIOR_SCRUB_ONLY = "scrub_only";
  public static final String BEHAVIOR_SCRUB_LP_VTP = "scrub_lp_vtp";
  public static final String BEHAVIOR_SCRUB_DPAD_MORPH = "scrub_dpad_morph";

  protected Uri[] uris;

  private int currentChannel;
  private Uri[] channelUris;

  private boolean isTrickPlaybarShowing = false;

  private boolean isAudioRenderOn = true;
  private PlaybackMetricsManagerApi statsManager;
  private Toast errorRecoveryToast;

  private @Nullable BaseTimeBarViewHandler timeBarViewHandler;

  private OutputProtectionMonitor outputProtectionMonitor;

  private GeekStatsOverlay geekStats;
  private AccessibilityHelper accessibilityHelper;

  private TimeBar timeBar;

  private Uri currentUri;

  private PlayerControlView playerControlView;
  private TransportControlHandler transportControlHandler;

  private static boolean isSMIPlayer;
  private static boolean isSmiClientInitialized = false;
  private static SimpleExoPlayer player;

  /**
   * Allows injecting HTTP Headers into all the requests made by the player
   */
  private @Nullable HttpDataSource.Factory httpDataSourceFactory;

  private void initSmiClientIfNeeded(Context context) throws IOException {
    if (isSmiClientInitialized == true) {
      return;
    }
    StringBuilder sb = new StringBuilder();
    InputStream is = getAssets().open("smi_init.json");

    BufferedReader br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
    if (br != null) {
      String str;
      while ((str = br.readLine()) != null) {
        sb.append(str);
      }
      br.close();
      try {
        SMIClientSdk.initialize(context, sb.toString());

        // Initialize the callback. Starting from ExoPlayer 2.12.x, SMI SDK
        // requires global addCallBackEventListener.
        SMIClientSdk.addCallBackEventListener(new SMIEventCallbackListener() {

          @Override
          public void onEventCallback(String category, String value) {
            // The category - sessionIds, networkmetric and bmqsstatus.
            // We are interested only in sessionIds and bmqsstatus
            if (category.equals("sessionids")) {
              String identifyMe = SystemUtils.getHSNT();
              SMIClientSdk.outboundMessage(player, "DeviceIdentity", identifyMe);
              Log.d(TAG, value);
              Log.d(TAG, "DeviceIdentity: " + identifyMe);
            }
          }
        });
        isSmiClientInitialized = true;
      } catch (Exception e) {
        Log.d(TAG, "SMI SDK is disabled in release candidate");
      }
    }
  }
  // Activity lifecycle
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.d(TAG, "onCreate() called");

    android.util.Log.i(TAG, SimpleExoPlayerFactory.VERSION_INFO);
    Context context = getApplicationContext();

    SimpleExoPlayerFactory.initializeLogging(context, DEFAULT_LOG_LEVEL);

    isSMIPlayer = getIntent().getBooleanExtra(ENABLE_TRUSTREME_LOGGING, false);

    if(isSMIPlayer == true) {
      try {
        initSmiClientIfNeeded(context);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    SimpleExoPlayerFactory.Builder builder = new SimpleExoPlayerFactory.Builder(context)
            .setPlaybackErrorHandlerListener((error, status) -> {
              switch (status) {
                case IN_PROGRESS:
                  Log.d(TAG, "playerErrorProcessed() - error: " + error.getMessage() + " status: " + status);
                  showErrorRecoveryWhisper(null, error);
                  break;

                case SUCCESS:
                  Log.d(TAG, "playerErrorProcessed() - recovered from " + error.getMessage());
                  showErrorRecoveryWhisper(null, null);
                  break;

                case WARNING:
                  if (error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED) {
                    ExoPlaybackException exoPlaybackException = (ExoPlaybackException) error;
                    @C.FormatSupport int formatSupport = exoPlaybackException.rendererFormatSupport;
                    String reason = "format: " + Format.toLogString(exoPlaybackException.rendererFormat) + ", ";
                    switch (formatSupport) {
                      case C.FORMAT_EXCEEDS_CAPABILITIES:
                        reason = "Exceeds Capabilities";
                        break;
                      case C.FORMAT_HANDLED:
                        break;
                      case C.FORMAT_UNSUPPORTED_DRM:
                        reason = "Unsupported DRM";
                        break;
                      case C.FORMAT_UNSUPPORTED_SUBTYPE:
                        reason = "Unsupported Subtype";
                        break;
                      case C.FORMAT_UNSUPPORTED_TYPE:
                        reason = "Unsupported Type";
                        break;
                    }
                    ViewActivity.this.showError("No supported video tracks, " + reason, error);

                  } else {
                    showErrorDialogWithRecoveryOption(error, "Un-excpected playback error");
                  }
                  break;

                case FAILED:
                  showErrorDialogWithRecoveryOption(error, "Playback Failed");
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
                if (type == C.TYPE_HLS) {
                  HlsMediaSource.Factory hlsFactory = (HlsMediaSource.Factory) factory;
                  boolean allowChunkless = getIntent().getBooleanExtra(CHUNKLESS_PREPARE, false);
                  hlsFactory.setAllowChunklessPreparation(allowChunkless);
                }

                boolean fast_resync = getIntent().hasExtra(FAST_RESYNC);
                if (fast_resync) {
                  DefaultHlsPlaylistTracker.ENABLE_SNTP_TIME_SYNC = true;
                  DefaultHlsPlaylistTracker.ENABLE_SNTP_TIME_SYNC_LOGGING = true;
                  float resyncPercentChange = getIntent().getFloatExtra(FAST_RESYNC, 0.0f) / 100.0f;

                  itemBuilder
                      .setLiveMinPlaybackSpeed(1.0f - resyncPercentChange)
                      .setLiveMaxPlaybackSpeed(1.0f + resyncPercentChange);

                }

                if (getIntent().hasExtra(LIVE_OFFSET)) {
                  int liveTargetOffsetMs = (int) (getIntent().getFloatExtra(LIVE_OFFSET,30.0f) * 1000);
                  itemBuilder
                      .setLiveTargetOffsetMs(liveTargetOffsetMs);
                  if (fast_resync) {
                    itemBuilder
                        .setLiveMinOffsetMs(liveTargetOffsetMs)
                        .setLiveMaxOffsetMs(liveTargetOffsetMs);
                  }
                }
              }

              @Override
              public void upstreamDataSourceFactoryCreated(HttpDataSource.Factory upstreamFactory) {
                // TODO other factories then the default
                if (upstreamFactory instanceof DefaultHttpDataSource.Factory) {
                  ((DefaultHttpDataSource.Factory) upstreamFactory).setUserAgent("TenFootDemo - [" + SimpleExoPlayerFactory.VERSION_INFO + "]");
                }
                ViewActivity.this.httpDataSourceFactory = upstreamFactory;
              }
            })
           .setTrackSelectorFactory(new SimpleExoPlayerFactory.TrackSelectorFactory() {
             @Override
             public DefaultTrackSelector createTrackSelector(Context context, ExoTrackSelection.Factory trackSelectionFactory) {
                return new SyncVideoTrackSelector(context, trackSelectionFactory);
             }
           })
        ;

    if (isSMIPlayer) {
      builder.setAlternatePlayerFactory(new SimpleExoPlayerFactory.AlternatePlayerFactory() {
        @Override
        public SimpleExoPlayer buildSimpleExoPlayer(DefaultRenderersFactory renderersFactory,
            LoadControl loadControl,
            DefaultTrackSelector trackSelector,
            LivePlaybackSpeedControl livePlaybackSpeedControl) {
          return new SMISimpleExoPlayer.Builder(ViewActivity.this, renderersFactory)
              .setTrackSelector(trackSelector)
              .setLoadControl(loadControl)
              // .setLivePlaybackSpeedControl(livePlaybackSpeedControl)
              .build();
        }
      });
    }
    boolean enableAsyncRenderer = getIntent().getBooleanExtra(ENABLE_ASYNC_RENDER, false);
    if (enableAsyncRenderer) {
      builder.setMediaCodecOperationMode(enableAsyncRenderer);
    }

    exoPlayerFactory = builder.build();

    LayoutInflater inflater = LayoutInflater.from(context);
    ViewGroup activityView = (ViewGroup) inflater.inflate(R.layout.view_activity, null);
    View debugView = inflater.inflate(R.layout.stats_for_geeks, null);

    setContentView(activityView);
    activityView.addView(debugView);

    View debugContainer = debugView.findViewById(R.id.geek_stats);
    geekStats = new GeekStatsOverlay(debugContainer);

    playerView = findViewById(R.id.player_view);
    playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
    timeBar = playerView.findViewById(R.id.exo_progress);
    timeBar.setKeyTimeIncrement(2_000);
    playerView.setControllerAutoShow(false);
    playerView.setControllerShowTimeoutMs(-1);

    playerControlView = findViewById(R.id.exo_controller);
    View ccView = playerView.findViewById(R.id.close_captions);
    if (ccView != null) {
      ccView.setOnClickListener(v -> {
        showTrackSelection(exoPlayerFactory.getAvailableTextTracks(), "Select Text");
      });
    }
    View bugView = playerView.findViewById(R.id.bug_icon);
    if (bugView != null) {
      bugView.setOnClickListener(v -> {
        if (geekStats != null) {
          geekStats.toggleVisible();
        }
      });
    }

    transportControlHandler = new TransportControlHandler(playerView, context, false);

    // Subtitle view.
    // set "defaults", which it fetches CaptioningManager
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

    View v = getCurrentFocus();
    if (v != null) {
      String viewName = getResources().getResourceEntryName(v.getId());
      Log.d(TAG, "View " + viewName + " has initial focus");
    }
    setFocusChangeListeners(activityView);
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

      case ACTION_SEEK:
        long seekTo =  intent.getIntExtra(SEEK_TO, C.POSITION_UNSET);
        boolean nearestSync = intent.getBooleanExtra(SEEK_NEAREST_SYNC, false);
        if (seekTo == C.POSITION_UNSET) {
          Log.e(TAG, "Must specify seek position with --ei " +SEEK_TO + " = n, in ms");
        } else {
          TrickPlayControl trickPlayControl = exoPlayerFactory.getCurrentTrickPlayControl();
          SimpleExoPlayer player = exoPlayerFactory.getCurrentPlayer();
          assert player != null;
          SeekParameters savedParams = null;
          if (nearestSync) {
            savedParams = player.getSeekParameters();
            player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
          }
          if (intent.getBooleanExtra(SEEK_UNSAFE, false)) {
            player.seekTo(seekTo);
          } else {
            long targetPositionMs = seekTo;
            if (targetPositionMs != C.TIME_UNSET) {
              targetPositionMs = Math.min(targetPositionMs, trickPlayControl.getLargestSafeSeekPositionMs());
            }
            targetPositionMs = Math.max(targetPositionMs, 0);
            player.seekTo(targetPositionMs);
          }
          if (savedParams != null) {
            player.setSeekParameters(savedParams);
          }
        }
        break;

      case ACTION_HEADERS:
        Bundle newHeaders = intent.getExtras();
        HashMap<String, String> headerSet = new HashMap<>();
        for (String key : newHeaders.keySet()) {
          headerSet.put(key, intent.getStringExtra(key));
        }
        if (httpDataSourceFactory != null) {
          httpDataSourceFactory.setDefaultRequestProperties(headerSet);
        }
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

    player = exoPlayerFactory.createPlayer(false, false, builder);
    player.setAudioAttributes(AudioAttributes.DEFAULT,true);
    TrickPlayControl trickPlayControl = exoPlayerFactory.getCurrentTrickPlayControl();

    String scrubBehavior = getIntent().getStringExtra(SCRUB_BEHAVIOR);
    scrubBehavior = scrubBehavior == null ? "" : scrubBehavior;

    if (timeBar instanceof DualModeTimeBar) {
      DualModeTimeBar dualModeTimeBar = (DualModeTimeBar) timeBar;
      switch (scrubBehavior) {
        case BEHAVIOR_SCRUB_ONLY:
          timeBarViewHandler = new ScrubOnlyTimeBarViewHandler(trickPlayControl, playerView, dualModeTimeBar, player);
          break;

        case BEHAVIOR_SCRUB_LP_VTP:
          timeBarViewHandler = new HoldTimeChangesSpeedTimeBarViewHandler(trickPlayControl, playerView, dualModeTimeBar, player);
          break;

        default:
        case BEHAVIOR_SCRUB_DPAD_MORPH:
          timeBarViewHandler = new DPadToTransportBaseTimeBarViewHandler(trickPlayControl, playerView, dualModeTimeBar, player, 2_000);
          break;
      }
      playerControlView.setProgressUpdateListener(timeBarViewHandler);
    } else {
      Log.d(TAG, "ignore scrub behvior for non-dual mode timebar");
    }

    // If request is for FAST_RECYNC, turn on debug logging in GeekStats
    geekStats.setEnableLiveOffsetLogging(getIntent().hasExtra(FAST_RESYNC));
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
    transportControlHandler.setPlayer(player, trickPlayControl);

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

    if (playerView != null) {
      playerView.onResume();
    }

    IntentFilter filter = new IntentFilter();
    filter.addAction(ACTION_HDMI_AUDIO_PLUG);
  }

   @Override
   public void onPause() {
     super.onPause();
     if (playerView != null) {
       playerView.onPause();
     }
   }

   @Override
   public void onStop() {
     super.onStop();
     if (playerView != null) {
       playerView.onPause();
     }

     if (timeBarViewHandler != null) {
      timeBarViewHandler.playerDestroyed();
     }
     timeBarViewHandler = null;
     playerControlView.setProgressUpdateListener(null);
     transportControlHandler.playerDestroyed();

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
        mediaHealthMetricsTest(false);
      }
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (playerView != null) {
      mediaHealthMetricsTest(false);
      playerView.setControllerVisibilityListener(null);
      playerView = null;
      exoPlayerFactory = null;
      geekStats = null;
    }
  }

  public static boolean boundedSeekTo(SimpleExoPlayer player,
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

  /**
   * Handle keys at the activity level. before they are dispatched to the Window
   * (and thus any focused View children thereof.
   *
   * Here global keys (transport control, back, channel up/down) are handled, if they
   * are handled here returns true to stop dipatch to the child views.
   *
   * @param event The key event.
   *
   * @return true or call super to dispatch to child views.
   */
  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    boolean handled = false;
    if (playerView != null && timeBarViewHandler != null) {
      boolean isTrickPlaybarShowing = playerView.isControllerVisible();
      Log.d(TAG, "dispatchKeyEvent() - isTrickPlaybarShowing: " + isTrickPlaybarShowing + " event: " + event + " focus: " + getCurrentFocus());
      if (exoPlayerFactory.getCurrentPlayer() != null) {
        handled = transportControlHandler.handleFunctionKeys(event);
        handled = handled || processActivityGlobalKey(event);


        // Events not handled by the transport control handler or the other activity level global keys handled below.
        // Initial event with trickplay bar not showing, shows it and pauses playback with bar focused.
        // DPAD up when trickplay bar is focused is essentially the "exit" (there is no focusable component above the bar, so
        // hide control.
        //
        if (! handled && event.getAction() == KeyEvent.ACTION_DOWN) {
          if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (isTrickPlaybarShowing) {
              playerView.hideController();
              handled = true;
            }
          } else if (!isTrickPlaybarShowing && timeBarViewHandler.showForEvent(event)) {
            playerView.showController();
            playerControlView.requestFocus();
            handled = true;
          }

        }
      }
    }
    if (! handled) {
      handled = super.dispatchKeyEvent(event);
    }
    return handled;
  }

  private boolean processActivityGlobalKey(KeyEvent event) {
    boolean handled = false;
    Uri nextChannel = null;

    TrickPlayControl trickPlayControl = exoPlayerFactory.getCurrentTrickPlayControl();
    DefaultTrackSelector trackSelector = exoPlayerFactory.getTrackSelector();

    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      int keyCode = event.getKeyCode();

      switch (keyCode) {

        case KeyEvent.KEYCODE_INFO:
          if (timeBarViewHandler != null) {
            timeBarViewHandler.toggleTrickPlayBar();
            handled = true;
          }
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

        case KeyEvent.KEYCODE_5:
//            exoPlayerFactory.setRendererState(C.TRACK_TYPE_AUDIO, ! isAudioRenderOn);
//            isAudioRenderOn = ! isAudioRenderOn;

            List<TrackInfo> allAudioTracks = exoPlayerFactory.getAvailableAudioTracks();
              List<TrackInfo> audioTracks = allAudioTracks;
              if (trackSelector instanceof SyncVideoTrackSelector) {
                SyncVideoTrackSelector syncVideoTrackSelector = (SyncVideoTrackSelector) trackSelector;audioTracks = new ArrayList<>();
                for (TrackInfo info : allAudioTracks) {
                  if (!syncVideoTrackSelector.getEnableTrackFiltering() ||SyncVideoTrackSelector.isSupportedAudioFormatForSyncVideo(info.format)) {
                    audioTracks.add(info);
                  }
                }
              }
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
              long targetPositionMs = trickPlayControl.getLargestSafeSeekPositionMs() - 3000;
              if (targetPositionMs != C.TIME_UNSET) {
                targetPositionMs = Math.min(targetPositionMs, trickPlayControl.getLargestSafeSeekPositionMs());
              }
              targetPositionMs = Math.max(targetPositionMs, 0);
              player.seekTo(targetPositionMs);
            }
            handled = true;
            break;

        default:
          break;
      }

      if (nextChannel != null) {
          playUri(nextChannel);
      }
    }

    return handled;
  }


  private void showTrackSelection(List<TrackInfo> availableTextTracks, String s) {
    List<TrackInfo> textTracks = availableTextTracks;
    if (textTracks.size() > 0) {
      DialogFragment dialog =
              TrackInfoSelectionDialog
                      .createForChoices(s, textTracks, exoPlayerFactory);
      dialog.show(getSupportFragmentManager(), null);
    }
  }

  protected void playUri(Uri uri) {
    // TODO chunkless should come from a properties file (so we can switch it when it's supported)
    boolean enableChunkless = getIntent().getBooleanExtra(CHUNKLESS_PREPARE, false);

    showErrorRecoveryWhisper(null, null);
    outputProtectionMonitor.refreshState();
    stopPlaybackIfPlaying();

    boolean fast_resync = getIntent().hasExtra(FAST_RESYNC);
    TrackSelector trackSelector = exoPlayerFactory.getTrackSelector();
    if (trackSelector instanceof SyncVideoTrackSelector) {
      SyncVideoTrackSelector syncVideoTrackSelector = (SyncVideoTrackSelector) trackSelector;
      syncVideoTrackSelector.setEnableTrackFiltering(fast_resync);
    }

    long seekTo =  getIntent().getIntExtra(INITIAL_SEEK, C.POSITION_UNSET);
    boolean playWhenReady =  getIntent().getBooleanExtra(START_PLAYING, true);
    Log.d(TAG, "playUri() playUri: '" + uri + "' - chunkless: " + enableChunkless + " initialPos: " + seekTo + " playWhenReady: " + playWhenReady);
    try {
      exoPlayerFactory.playUrl(uri, drmInfo, seekTo, playWhenReady);
      mediaHealthMetricsTest(true);
    } catch (UnrecognizedInputFormatException e) {
      showError("Can't play URI: " + uri, e);
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


  private void setFocusChangeListeners(View view) {
    view.setOnFocusChangeListener((v, hasFocus) -> {
      String viewId;
      try {
        viewId = getResources().getResourceEntryName(v.getId());
      } catch (Resources.NotFoundException e) {
        viewId = v.toString();
      }
      Log.d(TAG, "View " + viewId + " has focus: " + hasFocus);
    });
    if (view instanceof ViewGroup) {
      ViewGroup viewGroup = (ViewGroup) view;
      for (int i = 0; i < viewGroup.getChildCount(); i++) {
        setFocusChangeListeners(viewGroup.getChildAt(i));
      }
    }
  }

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

  private void showErrorRecoveryWhisper(@Nullable String message, @Nullable PlaybackException error) {
    String text = null;

    if (message != null) {
      text = message;
    } else if (error != null) {
      if (PlaybackExceptionRecovery.isBehindLiveWindow(error)) {
         text = "Recovering Behind Live Window";
      } else if (PlaybackExceptionRecovery.isPlaylistStuck(error)) {
        text = "Retrying stuck playlist";
      } else {
        text = "Retrying - " + error.getErrorCodeName();
      }
    }
    if (text != null) {
      if (errorRecoveryToast == null) {
        errorRecoveryToast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG);
      }
      errorRecoveryToast.setText(text);
      errorRecoveryToast.setDuration(Toast.LENGTH_LONG);
      errorRecoveryToast.show();
    } else if (errorRecoveryToast != null) {
      errorRecoveryToast.cancel();
      errorRecoveryToast = null;
    }
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


  private void showErrorDialogWithRecoveryOption(PlaybackException error, String title) {
    title += " - " + error.getLocalizedMessage();

    AlertDialog alertDialog = new AlertDialog.Builder(this)
        .setTitle("Error - " + error.errorCode)
        .setMessage(title)
        .setPositiveButton("Retry", (dialog, which) -> {
          player.seekToDefaultPosition();
          player.prepare();   // Attempt recovery with simple re-prepare using current MediaItem
          dialog.dismiss();
        })
        .setNegativeButton("Ok", (dialog, which) -> {
          if (statsManager != null) {
            statsManager.endAllSessions();
          }
          player.stop();
          player.clearMediaItems();
          dialog.dismiss();
        })
        .create();
    alertDialog.show();
  }

  private void showToast(String message) {
    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
  }

  private void mediaHealthMetricsTest(boolean enable) {
    if (playerView != null && false) {    // Uncomment for metrics debug
      if (enable) {
        playerView.postDelayed(updatePlaybackMetricsAction, 1000);
      } else {
        playerView.removeCallbacks(updatePlaybackMetricsAction);
      }
    }
  }

  private void updatePlaybackMetrics(){
    if (statsManager != null && playerView != null) {
      PlaybackMetrics playbackMetrics = statsManager.createOrReturnCurrent();

      // Update the PlaybackMetrics container object with latest running state
      statsManager.updateFromCurrentStats(playbackMetrics);
      Log.d(TAG, "updatePlaybackMetrics(): " + new JSONObject(playbackMetrics.getMetricsAsMap()));
      playerView.postDelayed(updatePlaybackMetricsAction, 6000);
    }
  }

  private final Runnable updatePlaybackMetricsAction = () -> updatePlaybackMetrics();


}
