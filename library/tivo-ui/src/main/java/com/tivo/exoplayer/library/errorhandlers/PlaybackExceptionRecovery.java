package com.tivo.exoplayer.library.errorhandlers;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.tivo.exoplayer.library.DefaultExoPlayerErrorHandler;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;

/**
 * ExoPlayer library errorhandler package contains a number of error handler classes implementing
 * this interface that can retry and possibly recover from various player errors reported
 * via {@link Player.EventListener#onPlayerError(ExoPlaybackException)}.
 *
 * The general flow is the client's onPlayerError() handler calls {@link #recoverFrom(ExoPlaybackException)}
 * and the error handler returns true if it can handle the error then it initiates recovery.
 *
 * The client (for {@link SimpleExoPlayerFactory} this is the {@link DefaultExoPlayerErrorHandler}) calls
 * back the {@link #checkRecoveryCompleted()} for player state change events or at anytime to check if
 * recovery has completed. If the playback is abandon (channel change, etc) call {@link #cancelRecovery()}
 * to reset the handlers state.
 */
public interface PlaybackExceptionRecovery {

    // Useful ExoPlayer error triage utilities

    /**
     * Check if the error was cause by BehindLiveWindowException.
     *
     * @param e the Exception to check
     * @return true if it is SOURCE error BehindLiveWindowException
     */
    static boolean isBehindLiveWindow(ExoPlaybackException e) {
        if (e.type != ExoPlaybackException.TYPE_SOURCE) {
            return false;
        }
        Throwable cause = e.getSourceException();
        while (cause != null) {
            if (cause instanceof BehindLiveWindowException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Recovery flow starts when this handler is called with an exception and returns true indicating
     * it will handle the error.  This error handler then becomes an active error handler
     *
     * In the {@link DefaultExoPlayerErrorHandler} implementation the error handlers are kept in an ordered list
     * and the first one returning true stops the rest of the handlers on the list from being
     * called and becomes the designated handler for this error occurrence
     *
     * @param e the {@link ExoPlaybackException} signaled
     * @return true to stop the recovery chain (assumes this handler recovered or has begun recovery for the error)
     */
    boolean recoverFrom(ExoPlaybackException e);

    /**
     * Called from {@link Player.EventListener} methods for playback state transition while this
     * error handler is an active error handler.
     *
     * @return true if this error handler has recovered, default returns false
     */
    default boolean checkRecoveryCompleted() {return false;}

    /**
     * Abort any recovery in progress and reset internal state.  This is called
     * if playback changes completely (the MediaSource changes)
     *
     * The default dose nothing.
     */
    default void abortRecovery() {}

    /**
     * If this handler has returned true for {@link #recoverFrom(ExoPlaybackException)} and the
     * handler is still attempting recovery, that is either:
     * <ol>
     *     <li>{@link #checkRecoveryCompleted()} is still returning false</li>
     *     <li>{@link #isRecoveryFailed()} is not returning false</li>
     * </ol>
     *
     * @return true if the handler is actively trying to recover
     */
    default boolean isRecoveryInProgress() {return false;}

    /**
     * Return the error being handled or that failed to be handled or null
     * if the last call to {@link #checkRecoveryCompleted()} returned
     * true (that is that the error was handled)
     *
     * @return the current error, if recovery is in progress or failed.
     */
    @Nullable
    ExoPlaybackException currentErrorBeingHandled();

    /**
     * The handler has given up on recovery
     *
     * @return true if the handler is giving up on recovering
     */
    default boolean isRecoveryFailed() {return false;}

    ;

    /**
     * Called when the client destroys the player, use this to release any listeners the error handler
     * may have
     */
    default void releaseResources() {};
}
