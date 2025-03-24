package com.tivo.exoplayer.library.multiview;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.video.VideoSize;

/**
 * Listener interface for player events in a MultiView context.
 */
public interface MultiViewPlayerListener {
    default void onVideoSizeChanged(int multiviewIndex, VideoSize videoSize) { }
    default void onMediaItemTransition(int multiviewIndex, @Nullable MediaItem mediaItem,
        @Player.MediaItemTransitionReason int reason) { }
    default void onPlayerError(int multiviewIndex, PlaybackException error) { }
    default void onTimelineChanged(int multiviewIndex, Timeline timeline,
        @Player.TimelineChangeReason int reason) { }
    default void onTracksChanged(int multiviewIndex, TrackGroupArray trackGroups,
        TrackSelectionArray trackSelections) { }
    default void onRenderedFirstFrame(int multiviewIndex) { }
    default void onPlaybackStateChanged(int multiviewIndex, @Player.State int playbackState) { }
    default void onEvents(int multiviewIndex, Player player, Player.Events events) { }
}