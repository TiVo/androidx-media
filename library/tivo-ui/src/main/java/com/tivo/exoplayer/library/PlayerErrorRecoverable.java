// Copyright 2020 TiVo Inc.  All rights reserved.
package com.tivo.exoplayer.library;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.google.android.exoplayer2.SimpleExoPlayer;

/**
 * PlaybackExceptionRecovery handlers call the implementor of this interface to get
 * player state in the event of errors and request error recovery.
 *
 * For clients that do not use {@link SimpleExoPlayerFactory} whatever object manages
 * the player instance must implement this top use {@link DefaultExoPlayerErrorHandler}
 * standalone.
 */
public interface PlayerErrorRecoverable {

  /**
   * Updates track selection to prefer tunneling or regular video pipeline,
   * this can be called before the player is created.  If the player is
   * created it will force an immediate track selection.
   *
   * Note, this setting persists across player create/destroy
   *
   * @param enableTunneling - true to prefer tunneled decoder (if available)
   */
  void setTunnelingMode(boolean enableTunneling);

  /**
   * Return true if the player actively playing in tunneling mode.
   *
   * @return true if tunneling mode and playing (state not IDLE)
   */
  boolean isTunnelingMode();

  /**
   * Request attempt to recover from payback errors.  This may simply restart
   * playback (e.g. call {@link SimpleExoPlayer#retry()}) without reseting the
   * current position
   */
  void retryPlayback();

  /**
   * Retry playback like {@link #retryPlayback()} except this will reset the position
   * to the default position (starting at 0 for VOD and the live offset for live). Note
   * the state is left (current timeline, etc)
   */
  void resetAndRetryPlayback();

  /**
   * Returns the current active {@link SimpleExoPlayer}, if any
   *
   * @return the current player or null if none
   */
  @Nullable SimpleExoPlayer getCurrentPlayer();
}
