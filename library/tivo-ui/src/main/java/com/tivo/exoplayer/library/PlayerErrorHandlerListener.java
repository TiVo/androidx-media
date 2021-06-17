package com.tivo.exoplayer.library;// Copyright 2010 TiVo Inc.  All rights reserved.

import com.google.android.exoplayer2.ExoPlaybackException;
import com.tivo.exoplayer.library.errorhandlers.PlaybackExceptionRecovery;

/**
 * Implement this interface to monitor playback errors processed by the {@link DefaultExoPlayerErrorHandler}
 * recovered or not.
 *
 * You can either use the {@link SimpleExoPlayerFactory} to create your player, as it provides a signature to provide this
 * call back and a set of useful error handlers.  This is the preferred method.
 *
 * Alternatively, just create a {@link DefaultExoPlayerErrorHandler} passing it your instance of this interface
 * and the set the list of {@link PlaybackExceptionRecovery}'s you want to enable.
 */
public interface PlayerErrorHandlerListener {

  enum HandlingStatus {
    /** Recovery in progress */
    IN_PROGRESS,

    /** Recovery successful */
    SUCCESS,

    /** Recovery Failed. */
    FAILED
  }
  /**
   * The {@link DefaultExoPlayerErrorHandler} handles playback errors reported via the
   * {@link com.google.android.exoplayer2.Player.EventListener#onPlayerError(ExoPlaybackException)} by calling
   * {@link PlaybackExceptionRecovery} implementations in turn until one
   * accepts the challenge to recover the error, if none do this method is called with status FAILED
   *
   * This method is called first when an {@link PlaybackExceptionRecovery} instance accepts recovering
   * the error (with status IN_PROGRESS) then again each time the handler attempts recovery with:
   * IN_PROGRESS, SUCCESS or FAILED if the error is being handled,
   * successfully recovered or failed to be handled.
   *
   * @param error the actual reported {@link ExoPlaybackException}
   * @param status, {@link HandlingStatus} for the error
   */
  void playerErrorProcessed(ExoPlaybackException error, HandlingStatus status);
}
