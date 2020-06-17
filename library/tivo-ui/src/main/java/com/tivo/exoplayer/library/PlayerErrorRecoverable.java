// Copyright 2020 TiVo Inc.  All rights reserved.
package com.tivo.exoplayer.library;

import com.google.android.exoplayer2.SimpleExoPlayer;

/**
 * PlaybackExceptionRecovery handlers call the implementor of this interface to get
 * player state in the event of errors and request error recovery.
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
   * playback (e.g. call {@link SimpleExoPlayer#retry()})
   */
  void retryPlayback();
}
