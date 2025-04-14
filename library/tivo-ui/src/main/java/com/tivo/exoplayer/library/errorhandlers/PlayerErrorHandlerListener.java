package com.tivo.exoplayer.library.errorhandlers;// Copyright 2010 TiVo Inc.  All rights reserved.

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;

/**
 * Implement this interface to monitor playback errors processed by the {@link DefaultExoPlayerErrorHandler}
 * recovered or not.
 *
 * <p>You can either use the {@link SimpleExoPlayerFactory} to create your player, as it provides a signature to provide this
 * call back and a set of useful error handlers.  This is the preferred method.</
 *
 * <p>Alternatively, just create a {@link DefaultExoPlayerErrorHandler} passing it your instance of this interface
 * and the set the list of {@link PlaybackExceptionRecovery}'s you want to enable.<
 */
public interface PlayerErrorHandlerListener {

  enum HandlingStatus {
    /** Recovery in progress */
    IN_PROGRESS,

    /** Recovery successful */
    SUCCESS,

    /** No recovery, but non-fatal error */
    WARNING,

    /** Recovery Failed. */
    FAILED,

    /** Aborted, playback MediaItem changed and recovery was abandon */
    ABANDONED
  }

  /**
   * The {@link DefaultExoPlayerErrorHandler} handles playback errors reported via the
   * {@link Player.Listener#onPlayerError(PlaybackException)} by calling
   * {@link PlaybackExceptionRecovery} implementations in turn until one
   * accepts the challenge to recover the error, if none do this method is called with status FAILED
   *
   * This method is called first when an {@link PlaybackExceptionRecovery} instance accepts recovering
   * the error (with status IN_PROGRESS) then again each time the handler attempts recovery with:
   * IN_PROGRESS.  The recover HandlingStatus values are as follows:
   *
   * <ul>
   *   <li>IN_PROGRESS &mdash; reports the ExoPlaybackException that is activly being recovered, this will
   *   be called repeatedly until the error is either recovered or recovery fails.</li>
   *   <li>SUCCESS &mdash; report that the error has been fully recovered from</li>
   *   <li>FAILED &mdash; report that the error recovery failed or was not possible. Playback has stopped at
   *   this point, same as reported directly via {@link Player.Listener#onPlayerError(PlaybackException)}</li>
   *   <li>WARNING &mdash; reported for issues that degrade playback but the player is not stopped.  Callee
   *   can choose to abort playback (with {@link Player#stop()}) and report the error</li>
   * </ul>
   *
   * Deprecated in favor of {@link #playerErrorProcessed(PlaybackException, HandlingStatus, Player)}
   * @param error the actual reported {@link ExoPlaybackException}
   * @param status, {@link HandlingStatus} for the error
   */
  @Deprecated
  default void playerErrorProcessed(PlaybackException error, HandlingStatus status) {}

  /**
   * The {@link DefaultExoPlayerErrorHandler} handles playback errors reported via the
   * {@link com.google.android.exoplayer2.Player.Listener#onPlayerError(PlaybackException)} by calling
   * {@link PlaybackExceptionRecovery} implementations in turn until one
   * accepts the challenge to recover the error, if none do this method is called with status FAILED
   * <p>
   * This method is called first when an {@link PlaybackExceptionRecovery} instance accepts recovering
   * the error (with status IN_PROGRESS) then again each time the handler attempts recovery with:
   * IN_PROGRESS.  The recover HandlingStatus values are as follows:
   *
   * <ul>
   *   <li>IN_PROGRESS &mdash; reports the ExoPlaybackException that is activly being recovered, this will
   *   be called repeatedly until the error is either recovered or recovery fails.</li>
   *   <li>SUCCESS &mdash; report that the error has been fully recovered from, error may be null</li>
   *   <li>FAILED &mdash; report that the error recovery failed or was not possible. Playback has stopped at
   *   this point, same as reported directly via {@link Player.Listener#onPlayerError(PlaybackException)}</li>
   *   <li>WARNING &mdash; reported for issues that degrade playback but the player is not stopped.  Callee
   *   can choose to abort playback (with {@link Player#stop()}) and report the error</li>
   *   <li>ABANDONED &mdash; reported when the playback media item changes and the error recovery is aborted</li>
   * </ul>
   *
   * @param status,       {@link HandlingStatus} for the error
   * @param error         the actual reported {@link ExoPlaybackException}
   * @param failingPlayer the player that failed
   */
  default void playerErrorProcessed(@Nullable PlaybackException error, HandlingStatus status, Player failingPlayer) {
    playerErrorProcessed(error, status);
  }
}
