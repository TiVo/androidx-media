// Copyright 2020 TiVo Inc.  All rights reserved.

package com.google.android.exoplayer2.trickplay;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
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
public class ScrubTrickPlay implements TrickPlayEventListener {

  private static final String TAG = "ScrubTrickPlay";
  private final SimpleExoPlayer player;
  private final TrickPlayControlInternal control;

  private long lastPosition = C.TIME_UNSET;
  private long lastRenderTime = C.TIME_UNSET;
  private boolean renderPending;
  private SeekParameters savedSeekParamerters;
  private boolean savedPlayWhenReadyState;

  ScrubTrickPlay(SimpleExoPlayer player, TrickPlayControlInternal control) {
    this.player = player;
    this.control = control;
  }

  boolean scrubSeek(long positionMs) {
    boolean isMoveThreshold;
    if (lastPosition == C.TIME_UNSET) {
      isMoveThreshold = true;
    } else {
      isMoveThreshold = Math.abs(positionMs - lastPosition) > 1000;
    }
    Log.d(TAG, "scrubSeek() - SCRUB called, position: " + positionMs + " lastPosition: " + lastPosition + " isMoveThreshold: " + isMoveThreshold + " renderPending: " + renderPending);


    if (isMoveThreshold && ! renderPending) {
      lastPosition = positionMs;
      Log.d(TAG, "scrubSeek() - issue seek, position: " + lastPosition);
      renderPending = true;
      player.seekTo(lastPosition);
    }
    return renderPending;
  }

  void scrubStart() {
    Log.d(TAG, "scrubStart()");
    savedPlayWhenReadyState = player.getPlayWhenReady();
    player.setPlayWhenReady(false);
    renderPending = false;
    savedSeekParamerters = player.getSeekParameters();
    player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
    control.addEventListener(this);

  }

  void scrubStop() {
    Log.d(TAG, "scrubStop() - current position: " + player.getContentPosition());
    control.removeEventListener(this);
    if (savedSeekParamerters != null) {
      player.setSeekParameters(savedSeekParamerters);
    }
    player.setPlayWhenReady(savedPlayWhenReadyState);
  }

  @Override
  public void trickFrameRendered(long frameRenderTimeUs) {
    if (lastRenderTime == C.TIME_UNSET) {
      Log.d(TAG, "trickFrameRendered() - position: " + C.usToMs(frameRenderTimeUs));
      lastRenderTime = SystemClock.DEFAULT.elapsedRealtime();
    } else {
      long renderDeltaTime = SystemClock.DEFAULT.elapsedRealtime() - lastRenderTime;
      lastRenderTime = SystemClock.DEFAULT.elapsedRealtime();
      Log.d(TAG, "trickFrameRendered() - position: " + C.usToMs(frameRenderTimeUs) + ", delta time: " + renderDeltaTime);
    }
    renderPending = false;
  }
}
