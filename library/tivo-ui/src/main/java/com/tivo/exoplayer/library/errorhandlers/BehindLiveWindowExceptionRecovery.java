package com.tivo.exoplayer.library.errorhandlers;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.util.Log;

public class BehindLiveWindowExceptionRecovery implements PlaybackExceptionRecovery, Player.EventListener {
    private static final String TAG = "BehindLiveWindowExceptionRecovery";

    private final PlayerErrorRecoverable playerErrorRecoverable;

    @Nullable public ExoPlaybackException currentError;
    @Nullable private TrickPlayControl.TrickMode lastTrickPlayMode;

    public BehindLiveWindowExceptionRecovery(PlayerErrorRecoverable playerErrorRecoverable) {
        this.playerErrorRecoverable = playerErrorRecoverable;

    }

    /**
     *
     * @param timeline The latest timeline. Never null, but may be empty.
     * @param reason The {@link Player.TimelineChangeReason} responsible for this timeline change.
     */
    @Override
    public void onTimelineChanged(Timeline timeline, @Player.TimelineChangeReason int reason) {
        // No need to call super, we don't use the deprecated version of this API

        switch (reason) {
            case Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED:
                Log.d(TAG, "Playlist for timeline has changed, cancel error recovery");
                abortRecovery();
                break;

            case Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE:
                if (currentError != null && ! timeline.isEmpty()) {
                    currentError = null;
                    SimpleExoPlayer currentPlayer = playerErrorRecoverable.getCurrentPlayer();
                    Log.i(TAG, "Playlist reloaded, issue seek to 0.");
                    assert currentPlayer != null;      // Not possible
                    currentPlayer.removeListener(this);
                    currentPlayer.seekTo(0);
                    TrickPlayControl trickPlayControl = playerErrorRecoverable.getCurrentTrickPlayControl();
                    if (trickPlayControl != null && lastTrickPlayMode != TrickPlayControl.TrickMode.NORMAL) {
                        trickPlayControl.setTrickMode(lastTrickPlayMode);
                    }
                }
        }
    }

    // Implement PlaybackExceptionRecovery
    @Override
    public boolean recoverFrom(ExoPlaybackException e) {
        boolean canRecover = PlaybackExceptionRecovery.isBehindLiveWindow(e);

        if (canRecover) {
            // BehindLiveWindowException occurs when the current play point causes a fetch for
            // a segment that has expired from the server.  A couple of conditions can cause
            // this:
            //  1. play position + buffered is < 0, that is you have paused long enough taht
            //     the next segment to buffer is ouside of the live window
            //  2. a jump back to the beginning of the window just as the origin removes
            //     the oldest segment (your buffered time is 0 here of course)
            //
            // Neither of these are fatal, nor should any effort be taken to guard against these
            // conditions.  Recovery simply restart playback at the current oldest live position
            // so the minimal (if any) content is skipped.
            //
            //
            SimpleExoPlayer player = playerErrorRecoverable.getCurrentPlayer();
            TrickPlayControl trickPlayControl = playerErrorRecoverable.getCurrentTrickPlayControl();
            assert player != null;
            if (trickPlayControl != null) {
                lastTrickPlayMode = trickPlayControl.getCurrentTrickMode();
            }
            Log.i(TAG, "Got BehindLiveWindowException, save state and call retryPlayback(), state:" +
                    " pos: " + player.getCurrentPosition() + " buffered: " + player.getBufferedPosition() + " trickMode: " + lastTrickPlayMode);

            // issue the seek after the timeline updates
            player.addListener(this);
            playerErrorRecoverable.retryPlayback();


            currentError = e;
        }
        return canRecover;
    }

    @Override
    public boolean checkRecoveryCompleted() {
        return ! isRecoveryInProgress();
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
    public @Nullable ExoPlaybackException currentErrorBeingHandled() {
        return currentError;
    }

    @Override
    public void abortRecovery() {
        currentError = null;
    }

}