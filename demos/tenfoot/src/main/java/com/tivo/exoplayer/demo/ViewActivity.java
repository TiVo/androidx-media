package com.tivo.exoplayer.demo;// Copyright 2010 TiVo Inc.  All rights reserved.

import static android.media.AudioManager.ACTION_HDMI_AUDIO_PLUG;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
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
import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
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
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.demo.TrackSelectionDialog;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistTracker;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
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
import com.tivo.exoplayer.library.errorhandlers.DrmLoadErrorHandlerPolicy;
import com.tivo.exoplayer.library.errorhandlers.PlayerErrorHandlerListener;
import com.tivo.exoplayer.library.ima.ImaSDKHelper;
import com.tivo.exoplayer.library.source.MediaItemHelper;
import com.tivo.exoplayer.library.timebar.controllers.BaseTimeBarViewHandler;
import com.tivo.exoplayer.library.timebar.controllers.DPadToTransportBaseTimeBarViewHandler;
import com.tivo.exoplayer.library.timebar.controllers.HoldTimeChangesSpeedTimeBarViewHandler;
import com.tivo.exoplayer.library.timebar.controllers.ScrubOnlyTimeBarViewHandler;
import com.tivo.exoplayer.library.timebar.controllers.TransportControlHandler;
import com.tivo.exoplayer.library.timebar.views.DualModeTimeBar;
import com.tivo.exoplayer.library.GeekStatsOverlay;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;
import com.tivo.exoplayer.library.SourceFactoriesCreated;
import com.tivo.exoplayer.library.errorhandlers.PlaybackExceptionRecovery;
import com.tivo.exoplayer.library.metrics.ManagePlaybackMetrics;
import com.tivo.exoplayer.library.metrics.PlaybackMetrics;
import com.tivo.exoplayer.library.metrics.PlaybackMetricsManagerApi;
import com.tivo.exoplayer.library.tracks.SyncVideoTrackSelector;
import com.tivo.exoplayer.library.tracks.TrackInfo;
import java.io.IOException;
import java.io.BufferedReader;
import com.tivo.exoplayer.library.util.AccessibilityHelper;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import java.util.List;
import java.util.Locale;
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

  public static final Integer DEFAULT_LOG_LEVEL = com.google.android.exoplayer2.util.Log.LOG_LEVEL_ALL;

  // Intents
  public static final String ACTION_VIEW = "com.tivo.exoplayer.action.VIEW";
  public static final String ACTION_VIEW_LIST = "com.tivo.exoplayer.action.VIEW_LIST";
  public static final String ACTION_GEEK_STATS = "com.tivo.exoplayer.action.GEEK_STATS";
  public static final String ACTION_STOP = "com.tivo.exoplayer.action.STOP_PLAYBACK";
  public static final String ACTION_SEEK = "com.tivo.exoplayer.action.SEEK_TO";

  // Intent data
  public static final String ENABLE_TUNNELED_PLAYBACK = "enable_tunneled_playback";
  public static final String URI_LIST_EXTRA = "uri_list";
  public static final String URI_LIST_AS_PLAYLIST = "as_playlist";
  public static final String VAST_URL = "vast_url";
  public static final String CHUNKLESS_PREPARE = "chunkless";
  public static final String ENABLE_ASYNC_RENDER = "enable_async_renderer";
  public static final String INITIAL_SEEK = "start_at";
  public static final String SEEK_TO = "seek_to";
  public static final String SEEK_UNSAFE = "seek_unsafe";
  public static final String SEEK_NEAREST_SYNC = "seek_nearest";
  public static final String START_PLAYING = "start_playing";
  public static final String SHOW_GEEK_STATS = "show_geek";
  public static final String LIVE_OFFSET = "live_offset";
  public static final String FAST_RESYNC = "fast_resync";

  public static final String ENABLE_TRUSTREME_LOGGING = "enable_trustreme_logging";

  private static final String SCRUB_BEHAVIOR = "scrub_behavior";
  public static final String BEHAVIOR_SCRUB_ONLY = "scrub_only";
  public static final String BEHAVIOR_SCRUB_LP_VTP = "scrub_lp_vtp";
  public static final String BEHAVIOR_SCRUB_DPAD_MORPH = "scrub_dpad_morph";

  private PlaybackMetricsManagerApi statsManager;
  private Toast errorRecoveryToast;

  private @Nullable BaseTimeBarViewHandler timeBarViewHandler;

  private GeekStatsOverlay geekStats;
  private AccessibilityHelper accessibilityHelper;

  private TimeBar timeBar;

  private PlayerControlView playerControlView;
  private TransportControlHandler transportControlHandler;

  private static boolean isSMIPlayer;
  private static boolean isSmiClientInitialized = false;
  private static SimpleExoPlayer player;

  private Map<MediaItem.PlaybackProperties, Integer> playbacks = new HashMap<>();
  private boolean usePlaylist;
  private MediaItem[] channelList;
  private int currentChannel;
  private Random nextChannelRamdomizer;
  private ImaSDKHelper imaSdkHelper;
  private long vastPlaybackStartTime = C.TIME_UNSET;

  /**
   * Callback class for when each MediaItem and it's MediaSource is created.  This allows
   * modfying settings on the MediaSourceFactory or cloning and creating an alternate
   * MediaItem
   */
  private class FactoriesCreatedCallback implements SourceFactoriesCreated {
    @Override
    public MediaItem factoriesCreated(@C.ContentType int type, MediaItem item, MediaSourceFactory factory) {
      if (type == C.TYPE_HLS) {
        HlsMediaSource.Factory hlsFactory = (HlsMediaSource.Factory) factory;
        boolean allowChunkless = getIntent().getBooleanExtra(CHUNKLESS_PREPARE, false);
        hlsFactory.setAllowChunklessPreparation(allowChunkless);
      }

      MediaItem.Builder itemBuilder = item.buildUpon();
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
        int liveTargetOffsetMs = (int) (getIntent().getFloatExtra(LIVE_OFFSET, 30.0f) * 1000);
        itemBuilder
            .setLiveTargetOffsetMs(liveTargetOffsetMs);
        if (fast_resync) {
          itemBuilder
              .setLiveMinOffsetMs(liveTargetOffsetMs)
              .setLiveMaxOffsetMs(liveTargetOffsetMs);
        }
      }
      return itemBuilder.build();
    }

    @Override
    public void upstreamDataSourceFactoryCreated(HttpDataSource.Factory upstreamFactory) {
      // TODO other factories then the default
      if (upstreamFactory instanceof DefaultHttpDataSource.Factory) {
        ((DefaultHttpDataSource.Factory) upstreamFactory).setUserAgent("TenFootDemo - [" + SimpleExoPlayerFactory.VERSION_INFO + "]");
      }
    }
  }

  private class PlaybackErrorHandlerCallback implements PlayerErrorHandlerListener {
    @Override
    public void playerErrorProcessed(@Nullable PlaybackException error, HandlingStatus status, Player failingPlayer) {
      switch (status) {
        case IN_PROGRESS:
          assert error != null;
          Log.d(TAG, "playerErrorProcessed() - error: " + error.getMessage() + " status: " + status);
          ViewActivity.this.showErrorRecoveryWhisper(null, error);
          break;

        case SUCCESS:
          Log.d(TAG, "playerErrorProcessed() - recovered");
          ViewActivity.this.showErrorRecoveryWhisper(null, null);
          break;

        case WARNING:
          assert error != null;
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
            // TODO this is ok if it is audio only
//            ViewActivity.this.showError("No supported video tracks, " + reason, error);

          } else {
            ViewActivity.this.showErrorDialogWithRecoveryOption(error, "Un-excpected playback error");
          }
          break;

        case FAILED:
          assert error != null;
          ViewActivity.this.showErrorDialogWithRecoveryOption(error, "Playback Failed");
          break;
      }
    }
  }

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

    if (isSMIPlayer) {
      try {
        initSmiClientIfNeeded(context);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    SimpleExoPlayerFactory.Builder builder = new SimpleExoPlayerFactory.Builder(context)
        .setPlaybackErrorHandlerListener(new PlaybackErrorHandlerCallback())
        .setSourceFactoriesCreatedCallback(new FactoriesCreatedCallback())
        .setTrackSelectorFactory((contextArg, trackSelectionFactory) -> new SyncVideoTrackSelector(contextArg, trackSelectionFactory));

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
    View v = getCurrentFocus();
    if (v != null) {
      String viewName = getResources().getResourceEntryName(v.getId());
      Log.d(TAG, "View " + viewName + " has initial focus");
    }
    setFocusChangeListeners(activityView);

    exoPlayerFactory.getMediaSourceFactory()
        .setDrmHttpDataSourceFactory(
            new DefaultHttpDataSource.Factory()
                .setReadTimeoutMs(2_000)
        )
        .setDrmLoadErrorHandlerPolicy(new DrmLoadErrorHandlerPolicy());
    ImaSDKHelper.DEBUG_MODE_ENABLED = true;

    imaSdkHelper = new ImaSDKHelper.Builder(playerView, exoPlayerFactory.getMediaSourceFactory(), getApplicationContext())
        .setAdProgressListener(new ImaSDKHelper.AdProgressListener() {
          private boolean isSeenStart;

          @Override
          public void onAdError(ImaSDKHelper.AdsConfiguration playingAd, AdErrorEvent adErrorEvent) {
            Log.d(TAG, "IMA Error: " + adErrorEvent.getError().getMessage() + " adsId: " + playingAd.adsId);
          }

          @Override
          public void onAdEvent(ImaSDKHelper.AdsConfiguration playingAd, AdEvent adEvent) {
            long deltaTime = SystemClock.elapsedRealtime() - vastPlaybackStartTime;
            AdEvent.AdEventType eventType = adEvent.getType();
            switch (eventType) {
              case AD_PROGRESS: // PROGRESS is very chatty
                break;

              case PAUSED:
                Log.d(TAG, "IMA Event at " + deltaTime + "(ms) " + eventType + " Ad: " + adEvent.getAd());
                boolean startedPaused = !getIntent().getBooleanExtra(START_PLAYING, true);
                if (startedPaused && ! isSeenStart) {
                  AlertDialog alertDialog = new AlertDialog.Builder(ViewActivity.this).create();
                  alertDialog.setTitle("Ad Paused");
                  StringBuilder message = new StringBuilder();
                  Formatter formatter = new Formatter(message);
                  formatter.format("Ad started in paused state, time to ready %2.3f seconds",  deltaTime / 1000.0f);
                  alertDialog.setMessage(message.toString());
                  alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", (dialog, which) -> {
                    player.setPlayWhenReady(true);
                    dialog.dismiss();
                  });
                  alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel", (dialog, which) -> dialog.dismiss());
                  alertDialog.show();
                }
                break;

              case STARTED:
                isSeenStart = true;
              case RESUMED:
                Log.d(TAG, "IMA Event at " + deltaTime + "(ms) " + eventType + " Ad: " + adEvent.getAd());
                break;

              case LOADED:
                isSeenStart = false;
                Log.d(TAG, "IMA Event at " + deltaTime + "(ms) " + eventType + " Ad Id: " + adEvent.getAd().getAdId());
                break;

              default:
                Ad ad = adEvent.getAd();
                Log.d(TAG, "IMA Event at " + deltaTime + "(ms) " + eventType
                    + " - elapsed(ms): " + deltaTime
                    + " data: " + adEvent.getAdData()
                    + (ad == null ? "" : " Ad Id: " + ad.getAdId())
                    + " adsId: " + playingAd.adsId
                );
            }
          }

          @Override
          public void onAdsCompleted(ImaSDKHelper.AdsConfiguration completedAd, boolean wasAborted) {
            Log.d(TAG, "IMA Event: All Ads completed - adsId: " + completedAd.adsId + " aborted: " + wasAborted);
          }
        })
        .build();
  }

  @Override
 public void onNewIntent(Intent intent) {
    Log.d(TAG, "onNewIntent() - intent: " + intent);
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
        long seekTo = intent.getIntExtra(SEEK_TO, C.POSITION_UNSET);
        boolean nearestSync = intent.getBooleanExtra(SEEK_NEAREST_SYNC, false);
        if (seekTo == C.POSITION_UNSET) {
          Log.e(TAG, "Must specify seek position with --ei " + SEEK_TO + " = n, in ms");
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

      case "mute":
        View mute = playerView.findViewById(R.id.mute_shutter);
        if (mute != null) {
          boolean visible = mute.getVisibility() == View.VISIBLE;
          if (visible) {
            mute.setVisibility(View.INVISIBLE);
          } else {
            mute.setVisibility(View.VISIBLE);
          }
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
    player.setAudioAttributes(AudioAttributes.DEFAULT, true);
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

    // Set the current ExoPlayer into all the objects that need it
    //
    playerView.setPlayer(player);
    geekStats.setPlayer(player, trickPlayControl);
    transportControlHandler.setPlayer(player, trickPlayControl);
    statsManager = new ManagePlaybackMetrics.Builder(player, trickPlayControl).build();

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
  }

  protected void stopPlaybackIfPlaying() {
    SimpleExoPlayer currentPlayer = exoPlayerFactory.getCurrentPlayer();
    if (currentPlayer != null) {
      int currentState = currentPlayer.getPlaybackState();
      if (currentState == Player.STATE_BUFFERING || currentState == Player.STATE_READY) {
        Log.d(TAG, "Stopping playback with player in state: " + currentState);
        player.clearMediaItems();         // reset the current playlist to empty
        player.stop();                    // stop (set playback state to IDLE) and clear the error state
        mediaHealthMetricsTest(false);
      }
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (imaSdkHelper != null) {
      imaSdkHelper.release();
      imaSdkHelper = null;
    }
    if (playerView != null) {
      mediaHealthMetricsTest(false);
      playerView.setControllerVisibilityListener(null);
      playerView = null;
      exoPlayerFactory = null;
      geekStats = null;
    }
  }

  // UI

  /**
   * Handle keys at the activity level. before they are dispatched to the Window
   * (and thus any focused View children thereof.
   * <p>
   * Here global keys (transport control, back, channel up/down) are handled, if they
   * are handled here returns true to stop dipatch to the child views.
   *
   * @param event The key event.
   * @return true or call super to dispatch to child views.
   */
  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    boolean handled = false;
    if (playerView != null && timeBarViewHandler != null) {
      boolean isTrickPlaybarShowing = playerView.isControllerVisible();
//      Log.d(TAG, "dispatchKeyEvent() - isTrickPlaybarShowing: " + isTrickPlaybarShowing + " event: " + event + " focus: " + getCurrentFocus());
      if (exoPlayerFactory.getCurrentPlayer() != null) {
        handled = transportControlHandler.handleFunctionKeys(event);
        handled = handled || processActivityGlobalKey(event);

        // Events not handled by the transport control handler or the other activity level global keys handled below.
        // Initial event with trickplay bar not showing, shows it and pauses playback with bar focused.
        // DPAD up when trickplay bar is focused is essentially the "exit" (there is no focusable component above the bar, so
        // hide control.
        //
        if (!handled && event.getAction() == KeyEvent.ACTION_DOWN) {
          if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (isTrickPlaybarShowing) {
              playerView.hideController();
              handled = true;
            }
          } else if (!isTrickPlaybarShowing && shouldShowTrickPlayContorlsForEvent(event)) {
            playerView.showController();
            playerControlView.requestFocus();
            handled = true;
          }

        }
      }
    }
    if (!handled) {
      handled = super.dispatchKeyEvent(event);
    }
    return handled;
  }

  /**
   * Check if our trickplay bar controller {@link #timeBarViewHandler} indicates the event
   * should trigger showing the trickplay bar unless another higher priority view (like Ad's WebView) has focus.
   *
   * @param event - Event to check
   * @return true if trickplay bar (controller) should transiton to visible and take focus
   */
  private boolean shouldShowTrickPlayContorlsForEvent(KeyEvent event) {
    return (getCurrentFocus() == null || getCurrentFocus() == playerView)   // Focused view is our PlayerView, or nothing.
        && timeBarViewHandler.showForEvent(event);
  }

  private boolean processActivityGlobalKey(KeyEvent event) {
    boolean handled = false;
    Uri nextChannel = null;

    TrickPlayControl trickPlayControl = exoPlayerFactory.getCurrentTrickPlayControl();
    DefaultTrackSelector trackSelector = exoPlayerFactory.getTrackSelector();

    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      int keyCode = event.getKeyCode();

      switch (keyCode) {

        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
          handled = player != null;
          if (handled) {
            player.setPlayWhenReady(!player.getPlayWhenReady());
          }
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
            SyncVideoTrackSelector syncVideoTrackSelector = (SyncVideoTrackSelector) trackSelector;
            audioTracks = new ArrayList<>();
            for (TrackInfo info : allAudioTracks) {
              if (!syncVideoTrackSelector.getEnableTrackFiltering() || SyncVideoTrackSelector.isSupportedAudioFormatForSyncVideo(
                  info.format)) {
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

        case KeyEvent.KEYCODE_CHANNEL_UP:
          handled = channelUpDown(true);
          break;

        case KeyEvent.KEYCODE_CHANNEL_DOWN:
          handled = channelUpDown(false);
          break;

        case KeyEvent.KEYCODE_LAST_CHANNEL:
          Timeline timeline = player.getCurrentTimeline();
          if (!timeline.isEmpty()) {
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
    }

    return handled;
  }

  private boolean channelUpDown(boolean isChannelUp) {
    boolean handled = false;
    playerView.removeCallbacks(doShuffle);
    if (usePlaylist) {
      if (isChannelUp && player.hasNextWindow()) {
        player.seekToNextWindow();
      } else if (! isChannelUp && player.hasPreviousWindow()) {
        player.seekToPreviousWindow();
      } else {
        player.seekToDefaultPosition(0);
      }
      handled = true;
    } else if (channelList != null) {
      int nextChannel = isChannelUp ? currentChannel + 1 : currentChannel - 1;
      currentChannel =  (nextChannel + channelList.length) % channelList.length;
      long seekTo = getIntent().getIntExtra(INITIAL_SEEK, C.POSITION_UNSET);
      boolean playWhenReady = getIntent().getBooleanExtra(START_PLAYING, true);
      exoPlayerFactory.playMediaItems(seekTo, playWhenReady, channelList[currentChannel]);
      handled = true;
    }
    if (handled) {
      Log.d(TAG, "channel " + (isChannelUp ? "up" : "down") + " to " + player.getCurrentMediaItem().playbackProperties.uri);
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
    Log.d(TAG, "processIntent() - intent: " + intent);
    String action = intent.getAction();
    Uri videoUri = intent.getData();
    String vastUrlString = intent.getStringExtra(VAST_URL);

    Uri[] uris = new Uri[0];

    String[] uriStrings = intent.getStringArrayExtra(URI_LIST_EXTRA);

    if (ACTION_VIEW.equals(action) && videoUri != null) {
      uris = new Uri[]{videoUri};
    } else if (ACTION_VIEW_LIST.equals(action)) {
      uris = parseToUriList(uriStrings);
    } else if (vastUrlString == null) {
      showToast(getString(R.string.unexpected_intent_action, action));
      finish();
    }

    boolean showGeekStats = intent.getBooleanExtra(SHOW_GEEK_STATS, true);
    if (!showGeekStats) {
      geekStats.toggleVisible();
    }

    boolean enableTunneling = intent.getBooleanExtra(ENABLE_TUNNELED_PLAYBACK, false);
    exoPlayerFactory.setTunnelingMode(enableTunneling);
    // TODO chunkless should come from a properties file (so we can switch it when it's supported)
    boolean enableChunkless = intent.getBooleanExtra(CHUNKLESS_PREPARE, false);

    showErrorRecoveryWhisper(null, null);

    // TODO this should maybe at least end the metrics session?
    stopPlaybackIfPlaying();

    boolean fast_resync = intent.hasExtra(FAST_RESYNC);
    TrackSelector trackSelector = exoPlayerFactory.getTrackSelector();
    if (trackSelector instanceof SyncVideoTrackSelector) {
      SyncVideoTrackSelector syncVideoTrackSelector = (SyncVideoTrackSelector) trackSelector;
      syncVideoTrackSelector.setEnableTrackFiltering(fast_resync);
    }
    mediaHealthMetricsTest(true);

    long seekTo = intent.getIntExtra(INITIAL_SEEK, C.POSITION_UNSET);
    boolean playWhenReady = intent.getBooleanExtra(START_PLAYING, true);
    Uri vastUrl = null;
    if (vastUrlString != null) {
      Log.d(TAG, "adding VAST URL: " + vastUrlString);
      vastUrl = Uri.parse(vastUrlString);
    } else {  // Else there are no ads in the play request, reset the IMA SDK UI
      imaSdkHelper.reset();
    }

    playbacks.clear();
    usePlaylist = intent.getBooleanExtra(URI_LIST_AS_PLAYLIST, false) && uris.length > 1;
    if (uris.length == 1) {
      Log.d(TAG, "playUri() playUri: '" + uris[0] + "' - chunkless: " + enableChunkless + " initialPos: " + seekTo + " playWhenReady: "
          + playWhenReady);
    } else if (uris.length > 0) {
      Log.d(TAG,
          "playUri() play " + uris.length + " channels, start with: '" + uris[0] + "' - chunkless: " + enableChunkless
              + " usePlaylist: " + usePlaylist + " initialPos: " + seekTo + " playWhenReady: " + playWhenReady);
    } else if (vastUrl != null) {
      Log.d(TAG, "play VAST only with no content");
    }

    MediaItem[] mediaItemsToPlay;

    // Are we just playing a trailer?
    if (vastUrl != null && uris.length == 0) {
      mediaItemsToPlay = new MediaItem[1];
      ImaSDKHelper.AdsConfiguration adsConfiguration =
          new ImaSDKHelper.AdsConfiguration(vastUrl, UUID.randomUUID());
      mediaItemsToPlay[0] = imaSdkHelper.createTrailerMediaItem(player, adsConfiguration)
          .build();
      vastPlaybackStartTime = SystemClock.elapsedRealtime();
    } else {
      byte[] seed = new SecureRandom().generateSeed(20); // 20 bytes of seed
      nextChannelRamdomizer = new Random(new BigInteger(seed).longValue());
      channelList = null;
      mediaItemsToPlay = new MediaItem[uris.length];
      int index = 0;
      for (Uri uri : uris) {
        MediaItem.Builder builder = new MediaItem.Builder();
        builder.setUri(uri);
        if (vastUrl != null) {
          ImaSDKHelper.AdsConfiguration adsConfiguration =
              new ImaSDKHelper.AdsConfiguration(vastUrl, UUID.randomUUID());
          imaSdkHelper.includeAdsWithMediaItem(player, builder, adsConfiguration);
          vastPlaybackStartTime = SystemClock.elapsedRealtime();
        }
        MediaItemHelper.populateDrmPropertiesFromIntent(builder, intent, this);
        mediaItemsToPlay[index++] = builder.build();
      }
    }

    usePlaylist = intent.getBooleanExtra(URI_LIST_AS_PLAYLIST, false) && uris.length > 1;
    if (usePlaylist) {
      exoPlayerFactory.playMediaItems(seekTo, playWhenReady, mediaItemsToPlay);
    } else {
      channelList = mediaItemsToPlay;
      exoPlayerFactory.playMediaItems(seekTo, playWhenReady, mediaItemsToPlay[0]);
    }


    int shuffle_every = intent.getIntExtra("shuffle_every", 0);
    if (shuffle_every > 0) {
      if (usePlaylist) {
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        player.setShuffleModeEnabled(true);
        player.setShuffleOrder(new ShuffleOrder.DefaultShuffleOrder(uris.length));
      }
      doShuffle = () -> shuffleNextChannel(shuffle_every * 1000L);
      playerView.postDelayed(doShuffle, shuffle_every * 1000L);
    }
  }

  private void shuffleNextChannel(long delay) {
    if (player != null) {
      if (usePlaylist) {
        player.seekToNextWindow();
      } else {
        int nextChannelIncrement = nextChannelRamdomizer.nextInt(channelList.length - 1);
        currentChannel = (currentChannel + nextChannelIncrement) % channelList.length;
        long seekTo = getIntent().getIntExtra(INITIAL_SEEK, C.POSITION_UNSET);
        boolean playWhenReady = getIntent().getBooleanExtra(START_PLAYING, true);
        exoPlayerFactory.playMediaItems(seekTo, playWhenReady, channelList[currentChannel]);
      }
      MediaItem currentMediaItem = player.getCurrentMediaItem();
      @Nullable MediaItem.PlaybackProperties next = currentMediaItem == null ? null : currentMediaItem.playbackProperties;
      Integer playedCounter = playbacks.get(next);
      if (playedCounter == null) {
        playedCounter = 0;
      }
      playedCounter++;
      playbacks.put(next, playedCounter);
      Log.d(TAG, "");
      Log.d(TAG, "play next MediaItem: " + (next == null ? "none" : next.uri) + " play count: " + playedCounter);
      playerView.postDelayed(doShuffle, delay);
    } else {
      playerView.removeCallbacks(doShuffle);
    }
  }

  private Runnable doShuffle;

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
    AlertDialog alertDialog = new AlertDialog.Builder(this)
        .setTitle("Error - " + error.errorCode)
        .setMessage(title + " - " + " Playing: " + getNowPlaying() + " Error: " + error.getLocalizedMessage())
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

  private String getNowPlaying() {
    MediaItem mediaItem = player.getCurrentMediaItem();
    String nowPlaying = "none";
    if (mediaItem != null) {
      Uri uri = mediaItem.playbackProperties != null
          ? mediaItem.playbackProperties.uri
          : null;
      nowPlaying = uri != null ? String.valueOf(uri) : mediaItem.mediaId;
    }
    return nowPlaying;
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
