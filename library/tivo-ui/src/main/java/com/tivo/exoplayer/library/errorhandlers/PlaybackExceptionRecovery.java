package com.tivo.exoplayer.library.errorhandlers;

import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_UNSPECIFIED;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.video.MediaCodecVideoDecoderException;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistTracker;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;

/**
 * ExoPlayer library errorhandler package contains a number of error handler classes implementing
 * this interface that can retry and possibly recover from various player errors reported
 * via {@link Player.Listener#onPlayerError(PlaybackException)}.
 *
 * The general flow is the client's onPlayerError() handler calls {@link #recoverFrom(PlaybackException)}
 * and the error handler returns true if it can handle the error then it initiates recovery.
 *
 * The client (for {@link SimpleExoPlayerFactory} this is the {@link DefaultExoPlayerErrorHandler}) calls
 * back the {@link #checkRecoveryCompleted()} for player state change events or at anytime to check if
 * recovery has completed. If the playback is abandon (channel change, etc) call {@link #abortRecovery()}
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
    static boolean isBehindLiveWindow(PlaybackException e) {
        return e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW;
    }

    /**
     * Check if the error was cause by HlsPlaylistTracker.PlaylistStuckException.
     *
     * @param e the Exception to check
     * @return true if it is SOURCE error HlsPlaylistTracker.PlaylistStuckException
     */
    static boolean isPlaylistStuck(PlaybackException e) {
        return e.errorCode == ERROR_CODE_IO_UNSPECIFIED
            && isSourceErrorOfType(e, HlsPlaylistTracker.PlaylistStuckException.class);

        }

    static boolean isSourceErrorOfType(PlaybackException e, Class<?> type) {
        if (! (e instanceof ExoPlaybackException)) {
            return false;
        }
        ExoPlaybackException playbackException = (ExoPlaybackException) e;
        if (playbackException.type != ExoPlaybackException.TYPE_SOURCE) {
            return false;
        }
        Throwable cause = playbackException.getSourceException();
        while (cause != null) {
            if (type.isAssignableFrom(cause.getClass())) {      // the cause is or extends the type
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Check if the error was cause by MediaCodecVideoDecoderException.
     *
     * @param e the Exception to check
     * @return true if it is RENDERER error MediaCodecVideoDecoderException with cause as IllegalStateException
     */
    static boolean isMediaCodecErrorRecoverable(PlaybackException e) {
        if (!(e instanceof ExoPlaybackException) ) {
            return false;
        }
        if (!(e.getCause() instanceof MediaCodecVideoDecoderException)) {
            return false;
        }
        ExoPlaybackException playbackException = (ExoPlaybackException) e;
        if (playbackException.type != ExoPlaybackException.TYPE_RENDERER) {
            return false;
        }
        Throwable cause = playbackException.getRendererException();
        while (cause != null) {
            if (cause instanceof java.lang.IllegalStateException) {
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
     * @param e the {@link PlaybackException} signaled
     * @return true to stop the recovery chain (assumes this handler recovered or has begun recovery for the error)
     */
    boolean recoverFrom(PlaybackException e);

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
     * If this handler has returned true for {@link #recoverFrom(PlaybackException)} and the
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
    PlaybackException currentErrorBeingHandled();

    /**
     * The handler has given up on recovery
     *
     * @return true if the handler is giving up on recovering
     */
    default boolean isRecoveryFailed() {return false;}

    ;

    /**
     * Called when the client destroys the player, use this to release any references to Android
     * resources (Context based) the error recovery handler may have.  Player listeners have already been
     * cleaned up by {@link Player#release()}
     */
    default void releaseResources() {};
}
