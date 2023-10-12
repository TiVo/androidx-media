package com.tivo.exoplayer.library.timebar.controllers;

import android.view.KeyEvent;
import androidx.annotation.NonNull;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.Log;
import com.tivo.exoplayer.library.timebar.views.DualModeTimeBar;

public class DPadToTransportBaseTimeBarViewHandler extends BaseTimeBarViewHandler {
  public static final String TAG = "DPadToTransportBaseTimeBarViewHandler";

  public DPadToTransportBaseTimeBarViewHandler(TrickPlayControl control,
      @NonNull PlayerView playerView,
      @NonNull DualModeTimeBar timeBar, SimpleExoPlayer simpleExoPlayer, int timeMs) {
    super(control, playerView, timeBar, simpleExoPlayer);
    timeBar.setLongPressThreshold(timeMs);
  }

  @Override
  public boolean showForEvent(KeyEvent event) {
    boolean triggered = super.showForEvent(event);
    Log.d(TAG, "showForEvent - playWhenReady: " + player.getPlayWhenReady() + " triggered;" + triggered + " trickMode: " + control.getCurrentTrickMode() + " dualMode: " + getCurrentDualMode() + " event: " + event);
    if (triggered) {
      switch (event.getKeyCode()) {
        case KeyEvent.KEYCODE_DPAD_LEFT:
          control.setTrickMode(TrickPlayControl.TrickMode.FR1);
          setCurrentDualMode(DualModeMode.SCAN);
          break;

        case KeyEvent.KEYCODE_DPAD_RIGHT:
          control.setTrickMode(TrickPlayControl.TrickMode.FF1);
          setCurrentDualMode(DualModeMode.SCAN);
          break;

        case KeyEvent.KEYCODE_DPAD_CENTER:
          handleDpadCenter();
          break;

        default:
          triggered = false;
      }
    }
    return triggered;
  }

  /**
   * Long press of a horizontal direction D-Pad key, in PLAYING mode triggers transtion to
   * trick-play in the indicated direction and first speed.
   *
   * @param timeBar the active {@link DualModeTimeBar}
   * @param event   the event (repeated) that is triggering the long press.
   */
  @Override
  public void handleLongPress(DualModeTimeBar timeBar, KeyEvent event) {
    Log.d(TAG, "handleLongPress - playWhenReady: " + player.getPlayWhenReady() + " trickMode: " + control.getCurrentTrickMode() + " dualMode: " + getCurrentDualMode() + " event: " + event);
    KeyEvent lastDownKeyEvent = timeBar.getLastDownKeyEvent();
    if (lastDownKeyEvent != null && getCurrentDualMode() == DualModeMode.PLAYING) {
      switch (lastDownKeyEvent.getKeyCode()) {
        case KeyEvent.KEYCODE_DPAD_LEFT:
          control.setTrickMode(TrickPlayControl.TrickMode.FR1);
          break;

        case KeyEvent.KEYCODE_DPAD_RIGHT:
          control.setTrickMode(TrickPlayControl.TrickMode.FF1);
          break;
      }
    }
  }

  @Override
  public boolean handlePress(DualModeTimeBar timeBar, KeyEvent event) {
    TrickPlayControl.TrickMode currentTrickMode = control.getCurrentTrickMode();
    boolean handled = false;
    TrickPlayControl.TrickMode nextTrickMode = null;
    Log.d(TAG, "handlePress - playWhenReady: " + player.getPlayWhenReady() + " currentTrickMode: " + currentTrickMode + " dualMode: " + getCurrentDualMode() + " event: " + event);

    // Handle events based on the current dual mode

    switch (getCurrentDualMode()) {
      case SCAN:
        if (event.getRepeatCount() == 0) {
          nextTrickMode = trickModeForEvent(event);
          if (nextTrickMode != null) {
            if (nextTrickMode == TrickPlayControl.TrickMode.NORMAL) {   // DPAD L/R or Center exited trickplay
              setCurrentDualMode(DualModeMode.SCRUB_TIMED);
            }
            handled = true;
          }
        }
        break;

      case PLAYING:
        if (event.getRepeatCount() == 0) {
          nextTrickMode = trickModeForEvent(event);
          if (nextTrickMode != null) {    // L/R would enter trickplay, Center either no change or SCRUB to normal
            if (nextTrickMode != TrickPlayControl.TrickMode.NORMAL) {
              setCurrentDualMode(DualModeMode.SCAN);
            }
            handled = true;
          }
        }
        break;

      case SCRUB_TIMED:
        switch (event.getKeyCode()) {
          case KeyEvent.KEYCODE_DPAD_LEFT:
          case KeyEvent.KEYCODE_DPAD_RIGHT:
            control.setTrickMode(TrickPlayControl.TrickMode.SCRUB);
        }
        break;
      case SCRUB:
      case NONE:
        break;

      default:
        throw new IllegalStateException("Unexpected value: " + getCurrentDualMode());
    }

    if (nextTrickMode != null) {
      control.setTrickMode(nextTrickMode);
    }

    return handled || super.handlePress(timeBar, event);
  }

  @Override
  public void longPressEnded(DualModeTimeBar timeBar) {
    // do nothing, remains in trickplay even after release.
    Log.d(TAG, "longPressEnded - playWhenReady: " + player.getPlayWhenReady() + " mode: " + control.getCurrentTrickMode());
  }
}
