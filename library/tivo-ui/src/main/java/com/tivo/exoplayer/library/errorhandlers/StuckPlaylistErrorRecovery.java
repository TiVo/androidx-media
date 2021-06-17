package com.tivo.exoplayer.library.errorhandlers;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.hls.HlsManifest;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistTracker;
import com.google.android.exoplayer2.util.Log;
import com.tivo.exoplayer.library.PlayerErrorRecoverable;

public class StuckPlaylistErrorRecovery implements PlaybackExceptionRecovery, Player.EventListener {
    public static final String TAG = "StuckPlaylistErrorRecovery";
    public static final int ERROR_MAX = 20;
    private final PlayerErrorRecoverable errorRecoverable;

    private int errorCount;
    private @Nullable ExoPlaybackException currentError;
    private long lastWindowStartMs = C.TIME_UNSET;
    private long lastDurationUs = C.TIME_UNSET;
    private String lastManifestUrl = "";

    public StuckPlaylistErrorRecovery(PlayerErrorRecoverable errorRecoverable) {
        this.errorRecoverable = errorRecoverable;
        SimpleExoPlayer player = errorRecoverable.getCurrentPlayer();
        if (player != null) {
            player.addListener(this);
        }
    }

    /**
     * If the error is an {@link HlsPlaylistTracker.PlaylistStuckException} then this code will restart playback
     * (prepare and reset the current playback position to the live point) with the current active playlist.
     * <p>
     * On the first error we set play when ready to false, this prevents playing the same content over and over until we
     * are sure (at the first timeline changed) that recover has found new content.
     *
     * @param exception the {@link ExoPlaybackException} signaled
     * @return true if recovery is being attempted
     */
    @Override
    public boolean recoverFrom(ExoPlaybackException exception) {
        boolean canRecover = exception.type == ExoPlaybackException.TYPE_SOURCE
                && exception.getSourceException() instanceof HlsPlaylistTracker.PlaylistStuckException
                && this.errorCount++ < ERROR_MAX;
        Log.w(TAG, "recoverFrom() - error count " + this.errorCount + " lastPositionMs: " + lastWindowStartMs);

        if (canRecover) {
            errorRecoverable.resetAndRetryPlayback();
            currentError = exception;
        }

        return canRecover;
    }

    @Override
    public boolean checkRecoveryCompleted() {
        boolean recovered = false;
        if (currentError != null) {
            SimpleExoPlayer player = errorRecoverable.getCurrentPlayer();
            if (player != null) {
                int windowIndex = player.getCurrentWindowIndex();
                Timeline timeline = player.getCurrentTimeline();
                Log.d(TAG, "checkRecoveryCompleted() - playerState: " + player.getPlaybackState()
                        + " -" + getTimelineInfo(player));
                if (! timeline.isEmpty()) {
                    Timeline.Window currentWindow = timeline.getWindow(windowIndex, new Timeline.Window());
                    if (isTimelineDataUnchanged(currentWindow)) {
                        Log.d(TAG, "checkRecoveryCompleted() - still stuck, wait for timeout to attempt repair...");
                    } else {
                        Log.d(TAG, "checkRecoveryCompleted() - timeline has updated, recovery completed");
                        clearCurrentError();
                        recovered = true;
                    }
                }
            }
        }
        return recovered;
    }

    @Override
    public boolean isRecoveryInProgress() {
        return currentError != null && errorCount < ERROR_MAX;
    }

    @Override
    public boolean isRecoveryFailed() {
        return currentError != null && errorCount == ERROR_MAX;
    }

    @Override
    public @Nullable ExoPlaybackException currentErrorBeingHandled() {
        return currentError;
    }

    @Override
    public void releaseResources() {
        this.currentError = null;
    }

    @Override
    public void abortRecovery() {
        clearCurrentError();
    }

    private void clearCurrentError() {
        currentError = null;
        errorCount = 0;
        lastWindowStartMs = C.TIME_UNSET;
        lastDurationUs = C.TIME_UNSET;
    }

    @Override
    public void onTimelineChanged(Timeline timeline, int reason) {
        SimpleExoPlayer player = errorRecoverable.getCurrentPlayer();
        if (!timeline.isEmpty() && player != null) {
            int windowIndex = player.getCurrentWindowIndex();
            Timeline.Window currentWindow = timeline.getWindow(windowIndex, new Timeline.Window());

            if (reason == Player.TIMELINE_CHANGE_REASON_PREPARED) {
                Log.d(TAG, "onTimelineChanged() - fresh prepare, errorCount: " + errorCount
                        + " -" + getTimelineInfo(player));
                Object manifest = currentWindow.manifest;
                if (manifest instanceof HlsManifest) {
                    HlsManifest hlsManifest = (HlsManifest) manifest;
                    if (! lastManifestUrl.equals(hlsManifest.masterPlaylist.baseUri)) {
                        lastManifestUrl = hlsManifest.masterPlaylist.baseUri;
                        Log.d(TAG, "onTimelineChanged() - prepare switched manifest, resetting error state");
                        clearCurrentError();
                    }
                } else {
                    Log.w(TAG, "Timeline change not HLS, no support for recovery");
                }

            }

            // If were not in active error recovery, then we keep saving the last window state from the
            // timeline updates, so if it becomes stuck, we know when it changes again (either duration
            // will increase or window start will change) once the timeline update changes this data
            //
            if (currentError == null) {
                lastWindowStartMs = currentWindow.windowStartTimeMs;
                lastDurationUs = currentWindow.durationUs;
            } else {
                if (isTimelineDataUnchanged(currentWindow)) {
                    Log.i(TAG, "onTimelineChanged() - stale timeline detected, seek to " +
                            C.usToMs(currentWindow.durationUs) + " to force stall");
                    player.seekTo(windowIndex, C.usToMs(currentWindow.durationUs));
                } else {
                    Log.i(TAG, "onTimelineChanged() - in error recovery, fresh values detected, seek to default");
                    player.seekTo(windowIndex, C.usToMs(currentWindow.defaultPositionUs));
                }
            }
        }
    }

    private boolean isTimelineDataUnchanged(Timeline.Window currentWindow) {
        return lastDurationUs != C.TIME_UNSET && lastWindowStartMs != C.TIME_UNSET
                && currentWindow.windowStartTimeMs == lastWindowStartMs && currentWindow.durationUs == lastDurationUs;
    }

    private String getTimelineInfo(SimpleExoPlayer player) {
        int windowIndex = player.getCurrentWindowIndex();
        Timeline timeline = player.getCurrentTimeline();
        if (timeline.isEmpty()) {
            return " timeline empty, position: " + player.getCurrentPosition();
        } else {
            Timeline.Window currentWindow = timeline.getWindow(windowIndex, new Timeline.Window());
            return " timeline durationMs: " + C.usToMs(currentWindow.durationUs)
                    + " windwoStartMs: " + currentWindow.windowStartTimeMs
                    + " lastWindowStartMs: " + lastWindowStartMs
                    + " lastDurationMs: " + C.usToMs(lastDurationUs)
                    + " position: " + player.getCurrentPosition();

        }

    }
}
