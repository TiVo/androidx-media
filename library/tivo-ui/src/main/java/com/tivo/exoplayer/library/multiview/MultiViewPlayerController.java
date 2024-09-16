package com.tivo.exoplayer.library.multiview;

import static com.google.android.exoplayer2.C.WIDEVINE_UUID;

import android.content.Context;
import android.util.Pair;
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
import com.google.android.exoplayer2.drm.DummyExoMediaDrm;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.source.TrackGroupArray;
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

  private final SimpleExoPlayerFactory exoPlayerFactory;
  private final MultiExoPlayerView.GridLocation gridLocation;   // Location in the multiplayer view (row/column)
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
    protected Pair<ExoTrackSelection.Definition, AudioTrackScore> selectAudioTrack(TrackGroupArray groups, int[][] formatSupport,
        int mixedMimeTypeAdaptationSupports, Parameters params, boolean enableAdaptiveTrackSelection) throws ExoPlaybackException {
      return MultiViewPlayerController.this.selected
          ? super.selectAudioTrack(groups, formatSupport, mixedMimeTypeAdaptationSupports, params, enableAdaptiveTrackSelection)
          : null;
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

    private void selectionStateChanged() {
      invalidate();
    }
  }

  public MultiViewPlayerController(SimpleExoPlayerFactory.Builder builder, boolean selected, MultiExoPlayerView.GridLocation gridLocation) {
    this.selected = selected;
    this.gridLocation = gridLocation;

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

  public void setOptimalVideoSize(MultiExoPlayerView.OptimalVideoSize optimalSize) {
    optimalVideoSize = optimalSize;

    // Declaritive track selection would be the best path, however viewport sizing does not
    // work exactly like we want for this feature.
    // TODO - we may want to optimize bitrate as well
//    DefaultTrackSelector.Parameters current = exoPlayerFactory.getCurrentParameters();
//    exoPlayerFactory.setCurrentParameters(current.buildUpon()
//        .setViewportSize(optimalSize.width, optimalSize.height, false)
//        .build());
  }

  public MultiExoPlayerView.GridLocation getGridLocation() {
    return gridLocation;
  }

  public void setSelected(boolean selected) {
    if (selected != this.selected) {
      TrackSelector trackSelector = exoPlayerFactory.getTrackSelector();
      if (trackSelector instanceof MultiViewTrackSelector) {
        MultiViewTrackSelector multiViewTrackSelector = (MultiViewTrackSelector) trackSelector;
        multiViewTrackSelector.selectionStateChanged();
      }
    }
    this.selected = selected;
  }

  public void handleStop() {
    exoPlayerFactory.releasePlayer();
  }

  public void playMediaItem(boolean fastResync, MediaItem currentItem) {
    TrackSelector trackSelector = exoPlayerFactory.getTrackSelector();
    if (trackSelector instanceof SyncVideoTrackSelector) {
      SyncVideoTrackSelector syncVideoTrackSelector = (SyncVideoTrackSelector) trackSelector;
      syncVideoTrackSelector.setEnableTrackFiltering(fastResync);
    }
    exoPlayerFactory.playMediaItems(C.POSITION_UNSET, true, currentItem);
  }

  public SimpleExoPlayer createPlayer() {
    DefaultLoadControl.Builder builder = new DefaultLoadControl.Builder();
    builder.setBufferDurationsMs(
        DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
        DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
        1000,   // Faster channel change
        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS);

    SimpleExoPlayer player = exoPlayerFactory.createPlayer(true, false, builder);
    player.addAnalyticsListener(new MultiViewDebugLogging(gridLocation));
    player.setAudioAttributes(AudioAttributes.DEFAULT, false);
    return player;
  }
}
