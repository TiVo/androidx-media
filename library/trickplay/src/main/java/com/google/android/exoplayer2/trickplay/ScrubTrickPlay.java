// Copyright 2020 TiVo Inc.  All rights reserved.

package com.google.android.exoplayer2.trickplay;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.SystemClock;

/**
 * ScrubTrickPlay is helper class that assists in implementing {@link TrickPlayControl.TrickMode#SCRUB}.
 *
 * In this trick-play mode, the {@link TrickPlayControl#scrubSeek(long)} implements a visual seek mode
 * which renders a frame for calls to the scrubSeek() function.  The preferred UX embodiment is enabling
 * the mode on touch down to a scrub bar then calling scrubSeek() for each position chnage, finally releasing
 * trick-play on touch up by calling {@link TrickPlayControl#setTrickMode(TrickPlayControl.TrickMode)} to set
 * trick-play mode back to {@link TrickPlayControl.TrickMode#NORMAL}
 *
 * Object for this class is created by the {@link TrickPlayController} internally, so no user code creates
 * this class.  To use it with the ExoPlayer UI library, implement the TimeBar.OnScrubListener with code
 * like:
 *
 * <pre>
 *    private class ScrubHandler implements TimeBar.OnScrubListener {
 *
 *      private final TrickPlayControl control;
 *
 *      ScrubHandler(TrickPlayControl control) {
 *        this.control = control;
 *      }
 *
 *      @Override
 *      public void onScrubStart(TimeBar timeBar, long position) {
 *        control.setTrickMode(TrickPlayControl.TrickMode.SCRUB);
 *      }
 *
 *      @Override
 *      public void onScrubMove(TimeBar timeBar, long position) {
 *        control.scrubSeek(position);
 *      }
 *
 *      @Override
 *      public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
 *        control.setTrickMode(TrickPlayControl.TrickMode.NORMAL);
 *      }
 *    }
 *
 * </pre>
 *
 * The demo-tenfoot UI has a sample that does just this.
 */
public class ScrubTrickPlay implements TrickPlayEventListener, Player.Listener {

  private static final String TAG = "ScrubTrickPlay";
  private final SimpleExoPlayer player;
  private final TrickPlayControlInternal control;

  private long lastPosition = C.TIME_UNSET;
  private long lastRenderTime = C.TIME_UNSET;
  private boolean renderPending;
  private SeekParameters savedSeekParamerters;
  private boolean savedPlayWhenReadyState;
  private long lastSeekTime = C.TIME_UNSET;

  ScrubTrickPlay(SimpleExoPlayer player, TrickPlayControlInternal control) {
    this.player = player;
    this.control = control;
  }

  /**
   * Issues a scrub trickplay seek, that is a seek with playWhenReady false intended just to move the
   * position and trigger a first frame render.  Note the seek is not issued, unless forced, if render
   * is still pending for the previous call to scrubSeek().
   *
   * @param positionMs - position to seek to
   * @param forced - force the seek, even if no render since previous seek
   * @return true if the seek was issued (a render is pending)
   */
  boolean scrubSeek(long positionMs, boolean forced) {
    boolean isMoveThreshold;
    if (lastPosition == C.TIME_UNSET) {
      isMoveThreshold = true;
    } else {
      isMoveThreshold = Math.abs(positionMs - lastPosition) > 1000;
    }
    Log.d(TAG, "scrubSeek() - SCRUB called, position: " + positionMs
        + " lastPosition: " + lastPosition
        + " isMoveThreshold: " + isMoveThreshold
        + " renderPending: " + renderPending
        + " forced: " + forced);

    if (isMoveThreshold && (forced || ! renderPending)) {
      executeSeek(positionMs);
    }

    return renderPending;
  }

  /**
   * Enter scrub trickplay mode.  This clears the internal variables, sets current seek parameters
   * to {@link SeekParameters#CLOSEST_SYNC} and prepares for calls to {@link #scrubSeek(long, boolean)})
   */
  void scrubStart() {
    Log.d(TAG, "scrubStart() - current position: " + player.getContentPosition());
    savedPlayWhenReadyState = player.getPlayWhenReady();
    player.setPlayWhenReady(false);
    renderPending = false;
    lastSeekTime = C.TIME_UNSET;
    lastRenderTime = C.TIME_UNSET;
    savedSeekParamerters = player.getSeekParameters();
    player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
    control.addEventListener(this);
    player.addListener(this);
  }

  long getLastRenderTime() {
    return lastRenderTime;
  }

  long getTimeSinceLastRender() {
    return renderPending ? Clock.DEFAULT.elapsedRealtime() - lastRenderTime : C.TIME_UNSET;
  }

  long getTimeSinceLastSeekIssued() {
    return lastSeekTime == C.TIME_UNSET ? C.TIME_UNSET : Clock.DEFAULT.elapsedRealtime() - lastSeekTime;
  }

  void scrubStop() {
    Log.d(TAG, "scrubStop() - current position: " + player.getContentPosition());
    control.removeEventListener(this);
    player.removeListener(this);
    if (savedSeekParamerters != null) {
      player.setSeekParameters(savedSeekParamerters);
    }
    player.setPlayWhenReady(savedPlayWhenReadyState);
  }

  // Internal methods

  private void executeSeek(long positionMs) {
    long currentPositionMs = player.getCurrentPosition();
    renderPending = true;
    long delta = positionMs - currentPositionMs;
    Log.d(TAG, "executeSeek() - issue seek, to positionMs: " + positionMs + " currentPositionMs: " + currentPositionMs + " deltaMs: " + delta);
    if (delta < 0) {
      player.setSeekParameters(SeekParameters.PREVIOUS_SYNC);
    } else {
      player.setSeekParameters(SeekParameters.NEXT_SYNC);
    }
    lastSeekTime = SystemClock.DEFAULT.elapsedRealtime();
    player.seekTo(positionMs);
  }

  // TrickPlayEventListener

  @Override
  public void trickFrameRendered(long frameRenderTimeUs) {
    long seekToRenderDelta = C.TIME_UNSET;
    if (lastSeekTime != C.TIME_UNSET) {
      seekToRenderDelta = SystemClock.DEFAULT.elapsedRealtime() - lastSeekTime;
    }
    if (lastRenderTime == C.TIME_UNSET) {
      Log.d(TAG, "trickFrameRendered() - position: " + C.usToMs(frameRenderTimeUs) + ", seekToRender(ms): " + seekToRenderDelta);
      lastRenderTime = SystemClock.DEFAULT.elapsedRealtime();
    } else {
      long renderDeltaTime = SystemClock.DEFAULT.elapsedRealtime() - lastRenderTime;
      lastRenderTime = SystemClock.DEFAULT.elapsedRealtime();
      Log.d(TAG, "trickFrameRendered() - position: " + C.usToMs(frameRenderTimeUs) + ", seekToRender(ms): " + seekToRenderDelta + ", sinceRender(ms): " + renderDeltaTime);
    }
    renderPending = false;
  }

  // Player.Listener

  @Override
  public void onPositionDiscontinuity(Player.PositionInfo oldPosition,
      Player.PositionInfo newPosition,  @Player.DiscontinuityReason int reason) {
    Log.d(TAG, "onPositionDiscontinuity() - reason: " + reason + " newPosition: " + newPosition.positionMs + " oldPosition: " + oldPosition.positionMs
        + " lastPosition: " + lastPosition + " delta: " + (newPosition.positionMs  - lastPosition) + " renderPending: " + renderPending);

    switch (reason) {

      case Player.DISCONTINUITY_REASON_SEEK:
      case Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT:
        lastPosition = newPosition.positionMs;
        break;

      case Player.DISCONTINUITY_REASON_AUTO_TRANSITION:
        lastPosition = C.POSITION_UNSET;
        break;
      case Player.DISCONTINUITY_REASON_REMOVE:
        lastPosition = C.POSITION_UNSET;
        break;
      case Player.DISCONTINUITY_REASON_SKIP:
        lastPosition = C.POSITION_UNSET;
        break;
      case Player.DISCONTINUITY_REASON_INTERNAL:
        lastPosition = C.POSITION_UNSET;
        break;
    }
  }
}
