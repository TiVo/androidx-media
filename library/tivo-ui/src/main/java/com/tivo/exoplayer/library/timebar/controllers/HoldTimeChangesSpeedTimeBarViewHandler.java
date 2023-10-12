package com.tivo.exoplayer.library.timebar.controllers;

import android.view.KeyEvent;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.tivo.exoplayer.library.timebar.views.DualModeTimeBar;
import java.util.HashMap;
import java.util.Map;

import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.Log;

/**
 * This handler uses long-press from SCRUB mode to trigger entry into SCAN mode, the longer the DPAD arrow
 * button is held the faster the trick-play speed becomes.  Releasing the D-PAD enters play mode, but trick-play
 * bar remains focused so left or right clicks re-enter SCRUB mode stepping while paused.
 *
 */
public class HoldTimeChangesSpeedTimeBarViewHandler extends BaseTimeBarViewHandler {

  private static final String TAG = "HoldTimeChangesSpeedTimeBarViewHandler";
  private final Map<Integer, TrickPlayControl.TrickMode>[] modeByHoldTime = new Map[3];
  private final int[] holdThresholds = new int[]{1_500, 3_000, 3_800};

  public HoldTimeChangesSpeedTimeBarViewHandler(TrickPlayControl control, PlayerView playerView, DualModeTimeBar timeBar,
      SimpleExoPlayer simpleExoPlayer) {
    super(control, playerView, timeBar, simpleExoPlayer);
    timeBar.setLongPressThreshold(holdThresholds[0]);

    Map<Integer, TrickPlayControl.TrickMode> fxnTime = new HashMap<>();
    fxnTime.put(KeyEvent.KEYCODE_DPAD_LEFT, TrickPlayControl.TrickMode.FR1);
    fxnTime.put(KeyEvent.KEYCODE_DPAD_RIGHT, TrickPlayControl.TrickMode.FF1);
    modeByHoldTime[0] = fxnTime;

    fxnTime = new HashMap<>();
    fxnTime.put(KeyEvent.KEYCODE_DPAD_LEFT, TrickPlayControl.TrickMode.FR2);
    fxnTime.put(KeyEvent.KEYCODE_DPAD_RIGHT, TrickPlayControl.TrickMode.FF2);
    modeByHoldTime[1] = fxnTime;

    fxnTime = new HashMap<>();
    fxnTime.put(KeyEvent.KEYCODE_DPAD_LEFT, TrickPlayControl.TrickMode.FR3);
    fxnTime.put(KeyEvent.KEYCODE_DPAD_RIGHT, TrickPlayControl.TrickMode.FF3);
    modeByHoldTime[2] = fxnTime;
  }

  @Override
  public boolean showForEvent(KeyEvent event) {
    Log.d(TAG, "showForEvent - playWhenReady: " + player.getPlayWhenReady() + " trickMode: " + control.getCurrentTrickMode() + " dualMode: " + getCurrentDualMode() + " event: " + event);
    boolean triggered = super.showForEvent(event);
    if (triggered) {
      switch (event.getKeyCode()) {
        case KeyEvent.KEYCODE_DPAD_LEFT:
          setCurrentDualMode(DualModeMode.SCAN);
          control.setTrickMode(TrickPlayControl.TrickMode.FR1);
          triggered = true;
          break;

        case KeyEvent.KEYCODE_DPAD_RIGHT:
          setCurrentDualMode(DualModeMode.SCAN);
          control.setTrickMode(TrickPlayControl.TrickMode.FF1);
          triggered = true;
          break;

        case KeyEvent.KEYCODE_DPAD_CENTER:
          handleDpadCenter();
          triggered = true;
          break;
      }
    } else {
      setCurrentDualMode(DualModeMode.NONE);
    }

    return triggered;
  }

  private void setFastPlayForTimeHeld(long timeHeld, int keyCode) {
    int highestMatchingThreshold = -1;
    for (long threshold : holdThresholds) {
      if (timeHeld >= threshold) {
        highestMatchingThreshold++;
      }
    }
    if (highestMatchingThreshold >= 0) {
      TrickPlayControl.TrickMode mode = modeByHoldTime[highestMatchingThreshold].get(keyCode);
      TrickPlayControl.TrickMode currentTrickMode = control.getCurrentTrickMode();
      if (currentTrickMode != mode) {
        Log.d(TAG, "setting trickplay mode " + mode + " holdTime: " + timeHeld + " playWhenReady: " + player.getPlayWhenReady());
        control.setTrickMode(mode);
      }
    }
  }

  // LongPressEventListener

  @Override
  public void handleLongPress(DualModeTimeBar dualModeTimeBar, KeyEvent event) {
    KeyEvent lastDownKeyEvent = dualModeTimeBar.getLastDownKeyEvent();
    if (lastDownKeyEvent != null) {
      setFastPlayForTimeHeld(dualModeTimeBar.getCurrentHoldTime(), lastDownKeyEvent.getKeyCode());
    }
  }

  @Override
  public void longPressEnded(DualModeTimeBar timeBar) {
    Log.d(TAG, "longPressEnded - playWhenReady: " + player.getPlayWhenReady() + " mode: " + control.getCurrentTrickMode());
    control.setTrickMode(TrickPlayControl.TrickMode.NORMAL);
    setCurrentDualMode(DualModeMode.NONE);
  }
}
