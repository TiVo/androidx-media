// Copyright 2020 TiVo Inc.  All rights reserved.
package com.tivo.exoplayer.library.errorhandlers;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;

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
   * playback and will reset the position to the default position (starting at 0 for VOD
   * and the live offset for live) also the player state (incuding the Timeline) is reset.
   * Note this will result in signalling the
   * {@link Player.EventListener#onTimelineChanged(Timeline, int)} with
   * {@link Player.TimelineChangeReason#TIMELINE_CHANGE_REASON_SOURCE_UPDATE} even though the
   * source URL does not change.
   */
  void retryPlayback();

  /**
   * Retry playback like {@link #retryPlayback()} except this will reset the position
   * to the default position (starting at 0 for VOD and the live offset for live). Note
   * the state is left (current timeline, etc)
   *
   * Deprecation - the move to MediaItem in ExoPlayer 2.12.x prevents doing a prepare() without
   *               reset.  Also the MediaSource itself is not exposed any longer, as the API is
   *               changing to use MediaItem.  Use {@link #retryPlayback()} instead.
   */
  @Deprecated
  void resetAndRetryPlayback();

  /**
   * Returns the current active {@link SimpleExoPlayer}, if any
   *
   * @return the current player or null if none
   */
  @Nullable SimpleExoPlayer getCurrentPlayer();


  /**
   * The current active {@link TrickPlayControl}, if any.
   *
   * @return the TrickPlayControl or null if none was created.
   */
  @Nullable TrickPlayControl getCurrentTrickPlayControl();
}
