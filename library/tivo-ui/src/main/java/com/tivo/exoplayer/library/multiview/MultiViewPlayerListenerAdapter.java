package com.tivo.exoplayer.library.multiview;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.video.VideoSize;

/**
 * Adapter class to delegate Player.Listener events to MultiViewPlayerListener with grid index.
 */
public class MultiViewPlayerListenerAdapter implements Player.Listener {
    public static final String TAG = "MultiViewPlayerListenerAdapter";

    private final @NonNull MultiExoPlayerView.GridLocation gridLocation;
    private final @NonNull MultiViewPlayerListener multiViewListener;

    public MultiViewPlayerListenerAdapter(@NonNull MultiExoPlayerView.GridLocation gridLocation,
                                          @NonNull MultiViewPlayerListener multiViewListener) {
        this.gridLocation = gridLocation;
        this.multiViewListener = multiViewListener;
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
        multiViewListener.onVideoSizeChanged(gridLocation.getViewIndex(), videoSize);
    }

    @Override
    public void onMediaItemTransition(@Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason) {
        multiViewListener.onMediaItemTransition(gridLocation.getViewIndex(), mediaItem, reason);
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        multiViewListener.onPlayerError(gridLocation.getViewIndex(), error);
    }

    @Override
    public void onTimelineChanged(@NonNull Timeline timeline, @Player.TimelineChangeReason int reason) {
        multiViewListener.onTimelineChanged(gridLocation.getViewIndex(), timeline, reason);
    }

    @Override
    public void onTracksChanged(@NonNull TrackGroupArray trackGroups, @NonNull TrackSelectionArray trackSelections) {
        multiViewListener.onTracksChanged(gridLocation.getViewIndex(), trackGroups, trackSelections);
    }

    @Override
    public void onRenderedFirstFrame() {
        multiViewListener.onRenderedFirstFrame(gridLocation.getViewIndex());
    }

    @Override
    public void onPlaybackStateChanged(@Player.State int playbackState) {
        multiViewListener.onPlaybackStateChanged(gridLocation.getViewIndex(), playbackState);
    }

    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        multiViewListener.onEvents(gridLocation.getViewIndex(), player, events);
    }
}