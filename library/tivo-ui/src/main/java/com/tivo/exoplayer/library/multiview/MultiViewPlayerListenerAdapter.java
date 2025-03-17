package com.tivo.exoplayer.library.multiview;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.video.VideoSize;

/**
 * Adapter class to delegate Player.Listener events to MultiViewPlayerListener with grid index.
 */
public class MultiViewPlayerListenerAdapter implements Player.Listener {
    public static final String TAG = "MultiViewPlayerListenerAdapter";

    private final MultiExoPlayerView.GridLocation gridLocation;
    private final MultiExoPlayerView.MultiViewPlayerListener multiViewListener;

    public MultiViewPlayerListenerAdapter(MultiExoPlayerView.GridLocation gridLocation,
                                           MultiExoPlayerView.MultiViewPlayerListener multiViewListener) {
        this.gridLocation = gridLocation;
        this.multiViewListener = multiViewListener;
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
        if (multiViewListener == null) {
            Log.d(TAG, "onVideoSizeChanged: multiViewListener is null");
            return;
        }
        multiViewListener.onVideoSizeChanged(gridLocation.getViewIndex(), videoSize);
    }

    @Override
    public void onMediaItemTransition(@Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason) {
        if (multiViewListener == null) {
            Log.d(TAG, "onMediaItemTransition: multiViewListener is null");
            return;
        }        
        multiViewListener.onMediaItemTransition(gridLocation.getViewIndex(), mediaItem, reason);
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        if (multiViewListener == null) {
            Log.d(TAG, "onPlayerError: multiViewListener is null");
            return;
        }
        multiViewListener.onPlayerError(gridLocation.getViewIndex(), error);
    }

    @Override
    public void onTimelineChanged(@NonNull Timeline timeline, @Player.TimelineChangeReason int reason) {
        if (multiViewListener == null) {
            Log.d(TAG, "onTimelineChanged: multiViewListener is null");
            return;
        }
        multiViewListener.onTimelineChanged(gridLocation.getViewIndex(), timeline, reason);
    }

    @Override
    public void onTracksChanged(@NonNull TrackGroupArray trackGroups, @NonNull TrackSelectionArray trackSelections) {
        if (multiViewListener == null) {
            Log.d(TAG, "onTracksChanged: multiViewListener is null");
            return;
        }
        multiViewListener.onTracksChanged(gridLocation.getViewIndex(), trackGroups, trackSelections);
    }

    @Override
    public void onRenderedFirstFrame() {
        if (multiViewListener == null) {
            Log.d(TAG, "onRenderedFirstFrame: multiViewListener is null");
            return;
        }
        multiViewListener.onRenderedFirstFrame(gridLocation.getViewIndex());
    }

    @Override
    public void onPlaybackStateChanged(@Player.State int playbackState) {
        if (multiViewListener == null) {
            Log.d(TAG, "onPlaybackStateChanged: multiViewListener is null");
            return;
        }
        multiViewListener.onPlaybackStateChanged(gridLocation.getViewIndex(), playbackState);
    }

    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        if (multiViewListener == null) {
            Log.d(TAG, "onEvents: multiViewListener is null");
            return;
        }
        multiViewListener.onEvents(gridLocation.getViewIndex(), player, events);
    }
}