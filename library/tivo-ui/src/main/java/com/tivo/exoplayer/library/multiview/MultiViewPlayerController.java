package com.tivo.exoplayer.library.multiview;

import static com.google.android.exoplayer2.C.ROLE_FLAG_TRICK_PLAY;
import static com.google.android.exoplayer2.C.WIDEVINE_UUID;
import static com.google.android.exoplayer2.Format.NO_VALUE;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
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
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.video.VideoSize;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;
import com.tivo.exoplayer.library.source.ExtendedMediaSourceFactory;
import com.tivo.exoplayer.library.tracks.SyncVideoTrackSelector;

/**
 * Encapsulates a single {@link com.google.android.exoplayer2.ExoPlayer} and it's factory in the
 * context of a set of players in a {@link MultiExoPlayerView}
 */
public class MultiViewPlayerController implements Player.Listener {
  public static final String TAG = "MultiViewPlayerController";

  /**
   * The default duration of media that the player will attempt to ensure is buffered at all
   * times, in milliseconds.
   */
  private static final int MIN_BUFFER_MS = 12_000;
  private static final int MAX_BUFFER_MS = 12_000;

  private final SimpleExoPlayerFactory exoPlayerFactory;
  private final MultiExoPlayerView.GridLocation gridLocation;   // Location in the multiplayer view (row/column)
  private final MultiPlayerAudioFocusManager audioFocusManager;
  private boolean selected;           // If this player is "selected" (has focus)
  @Nullable private MultiExoPlayerView.OptimalVideoSize optimalVideoSize;
  private boolean useQuickSelect;


  /**
   * Logs useful info on playback state for the player in the cell
   */
  private class MultiViewDebugLogging implements AnalyticsListener {
    private final MultiExoPlayerView.GridLocation gridLocation;

    public MultiViewDebugLogging(MultiExoPlayerView.GridLocation gridLocation) {
      this.gridLocation = gridLocation;
    }

    @Override
    public void onVideoSizeChanged(@NonNull EventTime eventTime, VideoSize videoSize) {
      Log.d(TAG, gridLocation + " - videoSizeChanged " + videoSize.width + "x" + videoSize.height);
    }

    @Override
    public void onSurfaceSizeChanged(@NonNull EventTime eventTime, int width, int height) {
      Log.d(TAG, gridLocation + " - surfaceSizeChanged " + width + "x" + height);
    }

    @Override
    public void onAudioEnabled(@NonNull EventTime eventTime, @NonNull DecoderCounters decoderCounters) {
      Log.d(TAG, gridLocation + " - audioEnabled");
    }

    @Override
    public void onAudioDecoderInitialized(@NonNull EventTime eventTime, @NonNull String decoderName, long initializedTimestampMs,
        long initializationDurationMs) {
      Log.d(TAG, gridLocation + " - audioDecoderInitialized " + decoderName);
    }

    @Override
    public void onAudioInputFormatChanged(@NonNull EventTime eventTime, @NonNull Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
      Log.d(TAG, gridLocation + " - audioInputFormatChanged " + Format.toLogString(format));
    }
  }

  MultiViewPlayerController(SimpleExoPlayerFactory.Builder builder, boolean selected, MultiExoPlayerView.GridLocation gridLocation,
      MultiPlayerAudioFocusManager audioFocusManager) {
    this.selected = selected;
    this.gridLocation = gridLocation;
    this.audioFocusManager = audioFocusManager;

    builder.setTrackSelectorFactory((context, trackSelectionFactory) -> new MultiViewTrackSelector(context, trackSelectionFactory));

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

  public void setOptimalVideoSize(MultiExoPlayerView.OptimalVideoSize optimalSize) {
    optimalVideoSize = optimalSize;
    Log.d(TAG, gridLocation + " - setOptimalVideoSize " + optimalSize.width + "x" + optimalSize.height);
  }

  public MultiExoPlayerView.GridLocation getGridLocation() {
    return gridLocation;
  }

  public void releasePlayer() {
    exoPlayerFactory.releasePlayer();
  }


  public void playMediaItem(boolean fastResync, MediaItem currentItem) {
    Log.d(TAG, gridLocation + " - playMediaItem " + mediaItemDebugString(currentItem) + " fastResync=" + fastResync);
    MultiViewTrackSelector trackSelector = (MultiViewTrackSelector) exoPlayerFactory.getTrackSelector();
    assert trackSelector != null;
    trackSelector.setEnableTrackFiltering(fastResync || useQuickSelect);
    trackSelector.setOptimalVideoSize(optimalVideoSize);
    trackSelector.setGridLocation(gridLocation);

    // Start playing, if our player is selected or if we already have focus.  Otherwise
    // the selected player needs to wait for audio focus before beginning playback.
    //
    boolean playWhenReady = audioFocusManager.hasAudioFocus() || !selected;
    exoPlayerFactory.playMediaItems(C.POSITION_UNSET, playWhenReady, currentItem);
  }

  // Internal APIs

  private String mediaItemDebugString(@NonNull MediaItem currentItem) {
    if (currentItem.playbackProperties == null) {
      return currentItem.mediaId;
    } else {
      return currentItem.mediaId + " " + currentItem.playbackProperties.uri;
    }
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
   * by external clients
   *
   * @param selected true if the player is selected
   */
  void setSelected(boolean selected) {
    boolean changed = selected != this.selected;
    SimpleExoPlayer player = exoPlayerFactory.getCurrentPlayer();
    Format audioFormat = player != null ? player.getAudioFormat() : null;
    if (MultiViewTrackSelector.isSupportedAudioFormatForVolumeMute(audioFormat)) {
      Log.d(TAG, gridLocation + "setSelected - Use volume mute selected=" + selected + " audioFormat=" + audioFormat);
      exoPlayerFactory.setAudioEnabled(true);
      if (changed && selected) {
        player.setVolume(1.0f);
      } else if (changed) {
        player.setVolume(0.0f);
      }
    } else {
      Log.d(TAG, gridLocation + "setSelected - Use renderer disable selected=" + selected + " audioFormat=" + audioFormat);
      changed = exoPlayerFactory.setAudioEnabled(selected);
    }
    Log.d(TAG, gridLocation + " - setSelected " + selected + " changed=" + changed);
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
    return player;
  }
}
