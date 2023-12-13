package com.tivo.exoplayer.library.errorhandlers;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.util.Log;
import com.tivo.exoplayer.library.tracks.SyncVideoTrackSelector;

/**
 * Handle NO_PCM_AUDIO errors when tuning to a channel without PCM audio when the Synced Playback feature is enabled.
 * <p></p>
 * If the {@link SyncVideoTrackSelector} detects that there are no Synced Playback compatible audio tracks,
 * namely PCM audio (AAC), then it throws a {@link PlaybackException} with error code ERROR_CODE_SYNC_VIDEO_FAILED_NO_PCM_AUDIO.
 * <p></p>
 * This handler is invoked to attempt to recover from this condition.
 * It does so by disabling track filtering on the track selector and invoking {@link PlayerErrorRecoverable#retryPlayback()}.
 * <p></p>
 * This handler will retry once.
 */
public class NoPcmAudioErrorHandler implements PlaybackExceptionRecovery {
    private static final String TAG = "NoPcmAudioErrorHandler";

    private final PlayerErrorRecoverable playerErrorRecoverable;
    private final SyncVideoTrackSelector syncVideoTrackSelector;

    @Nullable private PlaybackException currentError;

    public NoPcmAudioErrorHandler(PlayerErrorRecoverable playerErrorRecoverable, SyncVideoTrackSelector syncVideoTrackSelector) {
        this.syncVideoTrackSelector = syncVideoTrackSelector;
        this.playerErrorRecoverable = playerErrorRecoverable;

    }

    // Implement PlaybackExceptionRecovery
    @Override
    public boolean recoverFrom(PlaybackException e) {
        boolean canRecover = e.errorCode == SyncVideoTrackSelector.ERROR_CODE_SYNC_VIDEO_FAILED_NO_PCM_AUDIO;

        if (canRecover) {
            Log.i(TAG, "Got NoPcmAudio exception, retry playback");
            syncVideoTrackSelector.setEnableTrackFiltering(false);
            playerErrorRecoverable.retryPlayback();

            currentError = e;
        }
        return canRecover;
    }

    @Override
    public boolean checkRecoveryCompleted() {
        currentError = null;
        return true;
    }

    @Override
    public boolean isRecoveryInProgress() {
        return currentError != null;
    }

    @Override
    public boolean isRecoveryFailed() {
        return false;
    }

    @Override
    public @Nullable PlaybackException currentErrorBeingHandled() {
        return currentError;
    }

    @Override
    public void releaseResources() {
        Log.i(TAG, "releaseResources() - player released, aborting any recovery.  currentError = " + currentError);
        abortRecovery();
    }

    @Override
    public void abortRecovery() {
        currentError = null;
    }

}
