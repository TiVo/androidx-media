package com.tivo.exoplayer.library.multiview;

import static com.google.android.exoplayer2.C.WIDEVINE_UUID;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
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
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.common.primitives.Ints;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;
import com.tivo.exoplayer.library.source.ExtendedMediaSourceFactory;
import com.tivo.exoplayer.library.tracks.SyncVideoTrackSelector;
import java.util.ArrayList;
import java.util.List;

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
  private static final int MAX_FRAME_RATE = 30;
  private static final int MAX_BITRATE = 3_000_000;

  private final SimpleExoPlayerFactory exoPlayerFactory;
  private final MultiExoPlayerView.GridLocation gridLocation;   // Location in the multiplayer view (row/column)
  private final MultiPlayerAudioFocusManager audioFocusManager;
  private boolean selected;           // If this player is "selected" (has focus)
  @Nullable private MultiExoPlayerView.OptimalVideoSize optimalVideoSize;


  /**
   * Logs useful info on playback state for the player in the cell
   */
  private static class MultiViewDebugLogging implements AnalyticsListener {
    private final MultiExoPlayerView.GridLocation gridLocation;

    public MultiViewDebugLogging(MultiExoPlayerView.GridLocation gridLocation) {
      this.gridLocation = gridLocation;
    }

    @Override
    public void onVideoSizeChanged(EventTime eventTime, VideoSize videoSize) {
      Log.d(TAG, gridLocation + " - videoSizeChanged " + videoSize.width + "x" + videoSize.height);
    }

    @Override
    public void onSurfaceSizeChanged(EventTime eventTime, int width, int height) {
      Log.d(TAG, gridLocation + " - surfaceSizeChanged " + width + "x" + height);
    }

    @Override
    public void onAudioEnabled(EventTime eventTime, DecoderCounters decoderCounters) {
      Log.d(TAG, gridLocation + " - audioEnabled");
    }

    @Override
    public void onAudioDecoderInitialized(EventTime eventTime, String decoderName, long initializedTimestampMs,
        long initializationDurationMs) {
      Log.d(TAG, gridLocation + " - audioDecoderInitialized " + decoderName);
    }

    @Override
    public void onAudioInputFormatChanged(EventTime eventTime, Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
      Log.d(TAG, gridLocation + " - audioInputFormatChanged " + Format.toLogString(format));
    }
  }

  /**
   * Manages ExoPlayer selected tracks for the players in the multiview set.
   */
  private class MultiViewTrackSelector extends SyncVideoTrackSelector {

    public MultiViewTrackSelector(Context context, ExoTrackSelection.Factory trackSelectionFactory) {
      super(context, trackSelectionFactory);
    }

    @Nullable
    @Override
    protected ExoTrackSelection.Definition selectVideoTrack(TrackGroupArray groups, int[][] formatSupport,
        int mixedMimeTypeAdaptationSupports, Parameters params, boolean enableAdaptiveTrackSelection) throws ExoPlaybackException {
      ExoTrackSelection.Definition definition = super.selectVideoTrack(groups, formatSupport, mixedMimeTypeAdaptationSupports, params,
          enableAdaptiveTrackSelection);

      List<Integer> filteredTrackIndices = new ArrayList<>();

      if (definition != null && optimalVideoSize != null) {
        for (int selectedTrack : definition.tracks) {
          Format format = definition.group.getFormat(selectedTrack);
          if (optimalVideoSize.meetsOptimalSize(format) && (format.roleFlags & C.ROLE_FLAG_TRICK_PLAY) == 0) {
            filteredTrackIndices.add(selectedTrack);
          }
        }
        if (!filteredTrackIndices.isEmpty()) {
          definition = new ExoTrackSelection.Definition(definition.group, Ints.toArray(filteredTrackIndices), definition.type);
        }
      }

      return definition;
    }
  }

  MultiViewPlayerController(SimpleExoPlayerFactory.Builder builder, boolean selected, MultiExoPlayerView.GridLocation gridLocation,
      MultiPlayerAudioFocusManager audioFocusManager) {
    this.selected = selected;
    this.gridLocation = gridLocation;
    this.audioFocusManager = audioFocusManager;

    builder.setTrackSelectorFactory((context, trackSelectionFactory) -> new MultiViewTrackSelector(context, trackSelectionFactory));

    exoPlayerFactory = builder.build();
    exoPlayerFactory.setCurrentParameters(exoPlayerFactory.getCurrentParameters().buildUpon()
        .setMaxVideoFrameRate(MAX_FRAME_RATE)
        .setMaxVideoBitrate(MAX_BITRATE)
        .setExceedVideoConstraintsIfNecessary(true)
        .build());

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

  public void setOptimalVideoSize(MultiExoPlayerView.OptimalVideoSize optimalSize) {
    optimalVideoSize = optimalSize;
    Log.d(TAG, gridLocation + " - setOptimalVideoSize " + optimalSize.width + "x" + optimalSize.height);
    DefaultTrackSelector.Parameters current = exoPlayerFactory.getCurrentParameters();
    exoPlayerFactory.setCurrentParameters(current.buildUpon()
        .setMaxVideoSize(optimalSize.width, optimalSize.height)
        .build());
  }

  public MultiExoPlayerView.GridLocation getGridLocation() {
    return gridLocation;
  }

  public void releasePlayer() {
    exoPlayerFactory.releasePlayer();
  }


  public void playMediaItem(boolean fastResync, MediaItem currentItem) {
    TrackSelector trackSelector = exoPlayerFactory.getTrackSelector();
    if (trackSelector instanceof SyncVideoTrackSelector) {
      SyncVideoTrackSelector syncVideoTrackSelector = (SyncVideoTrackSelector) trackSelector;
      syncVideoTrackSelector.setEnableTrackFiltering(fastResync);
    }

    // Start playing, if our player is selected or if we already have focus.  Otherwise
    // the selected player needs to wait for audio focus before beginning playback.
    //
    boolean playWhenReady = audioFocusManager.hasAudioFocus() || !selected;
    exoPlayerFactory.playMediaItems(C.POSITION_UNSET, playWhenReady, currentItem);
  }

  // Internal APIs

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
   * Only the selected player has audio enabled.  Note, this is an intenal API
   * the API {@link MultiExoPlayerView#setSelectedPlayerView(int)} should be used
   * by external clients
   *
   * @param selected true if the player is selected
   */
  void setSelected(boolean selected) {
    boolean changed = exoPlayerFactory.setAudioEnabled(selected);
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
