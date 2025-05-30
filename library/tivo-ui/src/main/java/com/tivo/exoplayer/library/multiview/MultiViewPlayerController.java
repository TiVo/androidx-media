package com.tivo.exoplayer.library.multiview;

import static com.google.android.exoplayer2.C.WIDEVINE_UUID;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.drm.DummyExoMediaDrm;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.video.VideoSize;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;
import com.tivo.exoplayer.library.logging.ExtendedEventLogger;
import com.tivo.exoplayer.library.source.ExtendedMediaSourceFactory;

/**
 * Encapsulates a single {@link com.google.android.exoplayer2.ExoPlayer} and it's factory in the
 * context of a set of players in a {@link MultiExoPlayerView}
 */
public class MultiViewPlayerController {
  public static final String TAG = "MultiViewPlayerController";

  /**
   * The default duration of media that the player will attempt to ensure is buffered at all
   * times, in milliseconds.
   */
  private static final int MIN_BUFFER_MS = 12_000;
  private static final int MAX_BUFFER_MS = 12_000;

  private final SimpleExoPlayerFactory exoPlayerFactory;
  private final GridLocation gridLocation;   // Location in the multiplayer view (row/column)
  private final MultiPlayerAudioFocusManagerApi audioFocusManager;
  private boolean selected;           // If this player is "selected" (has focus)
  @Nullable private OptimalVideoSize optimalVideoSize;
  private boolean useQuickSelect;
  @Nullable private MultiViewPlayerListener playerEventListener;
  @Nullable private MultiViewPlayerListenerAdapter playerListenerAdapter; // Store adapter reference

  private MultiPlayerAccessibilityHelper accessibilityHelper;

  // Setter for the event listener
  public void setMultiViewPlayerListener(@Nullable MultiViewPlayerListener listener) {
    this.playerEventListener = listener;
    
    // If we already have a player, add the listener adapter to it
    SimpleExoPlayer player = exoPlayerFactory.getCurrentPlayer();
    if (player != null) {
      // Remove any existing listener first
      if (playerListenerAdapter != null) {
        player.removeListener(playerListenerAdapter);
        playerListenerAdapter = null;
      }
      
      // Add new listener if provided
      if (listener != null) {
        playerListenerAdapter = new MultiViewPlayerListenerAdapter(gridLocation, listener);
        player.addListener(playerListenerAdapter);
      }
    }
  }

  /**
   * Logs useful info on playback state for the player in the cell
   */
  private class MultiViewDebugLogging implements AnalyticsListener {
    private final GridLocation gridLocation;
    private final String analyticsTag;

    public MultiViewDebugLogging(GridLocation gridLocation) {
      this.gridLocation = gridLocation;
      this.analyticsTag = "MultiViewAnalytics-" + gridLocation.getViewIndex();
    }

    @Override
    public void onVideoSizeChanged(@NonNull EventTime eventTime, VideoSize videoSize) {
      Log.d(analyticsTag, "videoSizeChanged " + videoSize.width + "x" + videoSize.height);
    }

    @Override
    public void onSurfaceSizeChanged(@NonNull EventTime eventTime, int width, int height) {
      Log.d(analyticsTag, "surfaceSizeChanged " + width + "x" + height);
    }

    @Override
    public void onAudioEnabled(@NonNull EventTime eventTime, @NonNull DecoderCounters decoderCounters) {
      Log.d(analyticsTag, "audioEnabled");
    }

    @Override
    public void onAudioDecoderInitialized(@NonNull EventTime eventTime, @NonNull String decoderName, long initializedTimestampMs,
        long initializationDurationMs) {
      Log.d(analyticsTag, "audioDecoderInitialized " + decoderName);
    }

    @Override
    public void onAudioInputFormatChanged(@NonNull EventTime eventTime, @NonNull Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
      Log.d(analyticsTag, "audioInputFormatChanged " + Format.toLogString(format));
    }

    @Override
    public void onDroppedVideoFrames(@NonNull EventTime eventTime, int droppedFrames, long elapsedMs) {
      // Use INFO level for MultiView tags since we don't have operational frame drop 
      // metrics for MultiView playback yet. This ensures we capture diagnostic 
      // frame drop information for MultiView.
      String message = String.format("dropped %d frames in %d ms", droppedFrames, elapsedMs);
      Log.i(analyticsTag, message);
    }

  }

  MultiViewPlayerController(SimpleExoPlayerFactory.Builder builder, GridLocation gridLocation, MultiPlayerAudioFocusManagerApi audioFocusManager, MultiPlayerAccessibilityHelper accessibilityHelper) {
    this.gridLocation = gridLocation;
    this.audioFocusManager = audioFocusManager;
    this.accessibilityHelper = accessibilityHelper;

    builder.setTrackSelectorFactory((context, trackSelectionFactory) -> new MultiViewTrackSelector(context, trackSelectionFactory));
    builder.setEventListenerFactory((trackselector) -> new ExtendedEventLogger(trackselector, "EventLogger-" + gridLocation.getViewIndex()));
    builder.setBandwidthMeterFactory(new SimpleExoPlayerFactory.BandwidthMeterFactory() {
      @Override
      public DefaultBandwidthMeter createBandwidthMeter(Context context) {
        return new DefaultBandwidthMeter.Builder(context).build();
      }
    });

    exoPlayerFactory = builder.build();

    // setup to only use L3 DRM (L1 only supports maybe two views)
    ExtendedMediaSourceFactory mediaSourceFactory = exoPlayerFactory.getMediaSourceFactory();
    assert mediaSourceFactory != null;
    mediaSourceFactory.setExoMediaDrmProvider(uuid -> {
      try {
        FrameworkMediaDrm mediaDrm = FrameworkMediaDrm.newInstance(uuid);
        if (WIDEVINE_UUID.equals(uuid)) {
          mediaDrm.setPropertyString("securityLevel", "L3");
        }
        return mediaDrm;
      } catch (UnsupportedDrmException e) {
        return new DummyExoMediaDrm();
      }
    });
  }

  public void setQuickAudioSelect(boolean useQuickSelect) {
    this.useQuickSelect = useQuickSelect;
  }

  public void setOptimalVideoSize(OptimalVideoSize optimalSize) {
    optimalVideoSize = optimalSize;
    Log.d(TAG, gridLocation + " - setOptimalVideoSize " + optimalSize.width + "x" + optimalSize.height);
  }

  public GridLocation getGridLocation() {
    return gridLocation;
  }

  public void releasePlayer() {
    exoPlayerFactory.releasePlayer();
  }

  public void playMediaItem(boolean fastResync, MediaItem currentItem) {
    Log.i(TAG, "playMediaItem() " + gridLocation + " - playMediaItem " + mediaItemDebugString(currentItem) + " fastResync=" + fastResync);
    MultiViewTrackSelector trackSelector = (MultiViewTrackSelector) exoPlayerFactory.getTrackSelector();
    assert trackSelector != null;
    trackSelector.setEnableTrackFiltering(fastResync || useQuickSelect);
    trackSelector.setOptimalVideoSize(optimalVideoSize);
    trackSelector.setGridLocation(gridLocation);

    playMediaItemInternal(currentItem);
  }

  /**
   * Returns true if this player is selected, that is it is the current player
   * with audio focus
   *
   * @return true if this player is selected
   */
  public boolean isSelected() {
    return selected;
  }

  // Internal APIs

  private String mediaItemDebugString(@NonNull MediaItem currentItem) {
    if (currentItem.playbackProperties == null) {
      return currentItem.mediaId;
    } else {
      return currentItem.mediaId + " " + currentItem.playbackProperties.uri;
    }
  }

  void playMediaItemInternal(MediaItem currentItem) {
    // If we are the selected player, ensure we have audio focus, if not request it.
    if (selected) {
      audioFocusManager.setSelectedPlayer(exoPlayerFactory.getCurrentPlayer());
    }

    // Start playing, if our player is selected or if we already have focus.  Otherwise
    // the selected player needs to wait for audio focus before beginning playback.
    //
    boolean playWhenReady = audioFocusManager.hasAudioFocus() || !selected;
    Log.i(TAG, "playMediaItemInternal() " + gridLocation + " - playWhenReady=" + playWhenReady + " selected=" + selected);
    exoPlayerFactory.playMediaItems(C.POSITION_UNSET, playWhenReady, currentItem);
  }

  /**
   * Stops playback without releasing the player for the player associated with this controller.
   *
   * <p>The external client API for this is {@link MultiExoPlayerView#stopAllPlayerViews()}</p>
   */
  void stopPlayer() {
    Player player = exoPlayerFactory.getCurrentPlayer();
    if (player != null) {
      player.stop();
      player.clearMediaItems();
    }
  }

  /**
   * Only the selected player has audio enabled.  Note, this is an internal API
   * the API {@link MultiExoPlayerView#setSelectedPlayerView(int)} should be used
   * by external clients.
   *
   * <p>When the player is selected, it will have audio on by setting the volume multiplier
   * set to 1.0f (un-muted).  When the player is not selected, the audio volume multipler
   * is set to 0.0f (fully muted).   Additionally, for track formats that do not support
   * audio muting (pass-thru AC3 for example), the renderer is disabled when the
   * player is not selected, otherwise if the format supports volume mute
   * the renderer is left enabled to facilitate more rapid selection change</p>
   *
   * @param selected true if the player is selected
   */
  void setSelected(boolean selected) {
    SimpleExoPlayer player = exoPlayerFactory.getCurrentPlayer();
    if (player == null) {
      Log.e(TAG, gridLocation + " setSelected(" + selected + ") - No player");
      return;
    }
    Format audioFormat = player.getAudioFormat();
    float currentVolume = player.getVolume();
    if (selected) {
      player.setVolume(1.0f);
    } else {
      player.setVolume(0.0f);
    }
    if (MultiViewTrackSelector.isSupportedAudioFormatForVolumeMute(audioFormat) && useQuickSelect) {
      Log.d(TAG, gridLocation + " setSelected(" + selected + ") - Using volume mute/unmute "
          + "current volume: " + currentVolume + " audioFormat=" + audioFormat);
      exoPlayerFactory.setAudioEnabled(true);
    } else {
      Log.d(TAG, gridLocation + " setSelected(" + selected + ") - Using renderer disable "
          + " current volume: " +  currentVolume + " audioFormat=" + audioFormat);
      exoPlayerFactory.setAudioEnabled(selected);
    }
    // Set the close caption settings. Enable only when the global setting is enabled and the player is selected
    if (this.accessibilityHelper != null) {
        boolean closeCaptionEnabled = this.accessibilityHelper.isCloseCaptionEnabled();
        String closeCaptionLanguage = this.accessibilityHelper.getCloseCaptionLanguage();
        exoPlayerFactory.setCloseCaption((closeCaptionEnabled && selected), closeCaptionLanguage);
    }
    this.selected = selected;
  }

  SimpleExoPlayer createPlayer() {
    DefaultLoadControl.Builder builder = new DefaultLoadControl.Builder();
    builder.setBufferDurationsMs(
        MIN_BUFFER_MS,
        MAX_BUFFER_MS,
        1000,   // Faster channel change
        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS);

    SimpleExoPlayer player = exoPlayerFactory.createPlayer(true, false, builder);
    player.addAnalyticsListener(new MultiViewDebugLogging(gridLocation));
    player.setAudioAttributes(AudioAttributes.DEFAULT, false);
    
    // Add the listener adapter if a listener has been set
    if (playerEventListener != null) {
      playerListenerAdapter = new MultiViewPlayerListenerAdapter(gridLocation, playerEventListener);
      player.addListener(playerListenerAdapter);
    }
    
    return player;
  }
}
