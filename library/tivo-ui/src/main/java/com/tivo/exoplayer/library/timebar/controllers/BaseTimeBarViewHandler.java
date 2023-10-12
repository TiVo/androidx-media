package com.tivo.exoplayer.library.timebar.controllers;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.trickplay.TrickPlayEventListener;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ui.TimeBar;
import com.google.android.exoplayer2.util.Log;
import com.tivo.exoplayer.library.timebar.views.DualModeTimeBar;
import com.tivo.exoplayer.library.R;

/**
 * Manages base plumbing to handle key actions for the {@link TimeBar} view.   The only actions taken are
 * scrub operations based on call back from the {@link com.google.android.exoplayer2.ui.DefaultTimeBar}'s
 * {@link com.google.android.exoplayer2.ui.TimeBar.OnScrubListener}
 */
public abstract class BaseTimeBarViewHandler implements DualModeTimeBar.KeyEventHandler, PlayerControlView.ProgressUpdateListener, Player.Listener,
    TrickPlayEventListener {
  private static final String TAG = "BaseTimeBarViewHandler";

  /** timeout set when entering PLAYING mode with the timebar focused */
  public static int SHOW_TIMEOUT_MS_WHEN_PLAYING = 4000;

  /** timeout set for trickplay when it ends in the overshoot correction mode (SCRUB_TIMED) */
  public static int TIMEOUT_MS_SCRUB_TO_PLAYING = 3000;

  /** When an explict non-key driven event transitons to PLAYING (e.g. trickplay end of live) */
  public static int SHOW_TIMEOUT_MS_WHEN_EXIT_DUALMODE = 2000;

  /** timeout set for SCRUB, SCAN and focus lost on the time bar, keeps controls visible till explict hide */
  public static int SHOW_TIMEOUT_MS_KEEP_VISIBLE = -1;

  /** disables all the control show timers for debug */
  public static boolean DISABLE_SHOW_TIMEOUT = false;


  protected final View currentTime;
  protected final ViewGroup currentTimeHolder;
  protected final DualModeTimeBar timeBar;
  protected final TrickPlayControl control;
  protected final SimpleExoPlayer player;
  protected final PlayerView playerView;
  protected View jumpToStart;
  protected View jumpForward;
  private long durationMs = C.TIME_UNSET;

  private final ControllerShowTimeoutManager timeoutManager;

  private final ScrubEventHandler scrubEventHandler;

  /**
   * Indicate the current DualMode mode the UI is in
   */
  enum DualModeMode {
    /** SCRUB is player in paused state stepping frame by frame with each touchbar/dpad move */
    SCRUB,
    /** SCRUB_TIMED like SCRUB, but the {@link #TIMEOUT_MS_SCRUB_TO_PLAYING} is active to time out to */
    SCRUB_TIMED,
    /** SCAN is player in a fast playback trickplay mode, other than {@link com.google.android.exoplayer2.trickplay.TrickPlayControl.TrickMode#SCRUB} */
    SCAN,
    /** PLAYING is not paused, and not trickplay active (normal playback) */
    PLAYING,
    /** NONE is initial state, dual mode not yet active */
    NONE
  }

  private DualModeMode currentDualMode = DualModeMode.NONE;

  /**
   * Manages the {@link PlayerView}'s controller show timeout to control when
   * (or if) the controls are hidden automatically and the manual show/hide
   * method {@link #toggleTrickPlayBar()}
   *
   * The {@link PlayerView} auto hides the controls if all of the below are true:
   *   1. state is {@link Player#STATE_READY} or {@link Player#STATE_BUFFERING}
   *   2. not paused ({@link Player#getPlayWhenReady()} is true
   *   3. {@link PlayerView#getControllerShowTimeoutMs()} passes without any key activity
   *
   * This class handles setting timeout values for {@link PlayerView#getControllerShowTimeoutMs()} based
   * on the Dual Mode state transitions and restoring the
   */
  protected class ControllerShowTimeoutManager implements PlayerControlView.VisibilityListener, View.OnFocusChangeListener {
    private boolean isTrickPlaybarShowing;
    private int timeoutBeforeForcedShow = -1;
    private int timeoutBeforeFocusLost = -1;

    /**
     * Show/Hide the player controls without changing any state in the handlers.  For example
     * the INFO key may do this.
     */
    public void toggleTrickPlayBar() {
      if (isTrickPlaybarShowing) {
        if (timeoutBeforeForcedShow != -1) {
          setControllerShowTimeoutMs(timeoutBeforeForcedShow);
        }
        timeoutBeforeForcedShow = -1;
        playerView.hideController();
      } else {
        timeoutBeforeForcedShow = playerView.getControllerShowTimeoutMs();
        setControllerShowTimeoutMs(SHOW_TIMEOUT_MS_KEEP_VISIBLE);
        playerView.showController();
      }
    }

    private void setControllerShowTimeoutMs(int timeoutMs) {
      Log.d(TAG, "setControllerShowTimeoutMs() - timeoutMs: " + timeoutMs + " isVisible: " + isTrickPlaybarShowing);
      if (DISABLE_SHOW_TIMEOUT || ! isTrickPlaybarShowing) {
        playerView.setControllerShowTimeoutMs(-1);
      } else {
        playerView.setControllerShowTimeoutMs(timeoutMs);
      }
    }

    /**
     * Update state if focus for the timeBar changes.
     * When the focus leaves the timeBar for any other control in the trick-play overlay
     * the auto-hide timer timeout is set to infinite to diable the timeout
     *
     * @param v The view whose state has changed.
     * @param hasFocus The new focus state of v.
     */
    @Override
    public void onFocusChange(View v, boolean hasFocus) {
      Log.d(TAG, "onFocusChange - hasFocus: " + hasFocus + " timeoutBeforeFocusLost: " + timeoutBeforeFocusLost + " trickMode: " + control.getCurrentTrickMode() + " dualMode: " + getCurrentDualMode() + " timeBar: " + v);
      if (hasFocus) {
        if (timeoutBeforeFocusLost != -1) {
          setControllerShowTimeoutMs(timeoutBeforeFocusLost);
        }
        timeoutBeforeFocusLost = -1;
      } else {
        timeoutBeforeFocusLost = playerView.getControllerShowTimeoutMs();
        setControllerShowTimeoutMs(SHOW_TIMEOUT_MS_KEEP_VISIBLE);
      }
    }

    /**
     * Update our state when the tricklplay bar controls show/hide.
     *
     * This method is called when the controls UI visibility changes ({@link PlayerView#hideController()} or
     * {@link PlayerView#showController()} was called, or a timeout auto-hides the controls).  We toggle
     * to pause mode on initial visible and the trickplay bar gains focus
     *
     * @param visibility The new visibility. Either {@link View#VISIBLE} or {@link View#GONE}.
     */
    @Override
    public void onVisibilityChange(int visibility) {
      Log.d(TAG, "onVisibilityChange - visibility: " + visibility + " playWhenReady: " + player.getPlayWhenReady() + " trickMode: " + control.getCurrentTrickMode() + " dualMode: " + getCurrentDualMode());
      isTrickPlaybarShowing = visibility == View.VISIBLE;
      if (!isTrickPlaybarShowing) {
        onControlsHiding();
      } else {
        onControlsShowing();
      }
    }

    /**
     * Update the controller show timeout based on the new DualMode mode.  See the
     * descritions for each timeout for details.
     *
     * @param currentDualMode - value before the update
     * @param nextDualMode - value after
     */
    public void dualModeModeChanged(DualModeMode currentDualMode, DualModeMode nextDualMode) {
      if (currentDualMode != nextDualMode) {
        Log.d(TAG, "dualModeModeChanged() - from: " + currentDualMode + " to: " + nextDualMode);
        switch (nextDualMode) {
          case SCRUB:
            setControllerShowTimeoutMs(SHOW_TIMEOUT_MS_KEEP_VISIBLE);
            break;
          case SCRUB_TIMED:
            setControllerShowTimeoutMs(TIMEOUT_MS_SCRUB_TO_PLAYING);
            break;
          case SCAN:
            setControllerShowTimeoutMs(SHOW_TIMEOUT_MS_KEEP_VISIBLE);
            break;
          case PLAYING:
            setControllerShowTimeoutMs(SHOW_TIMEOUT_MS_WHEN_PLAYING);
            break;
          case NONE:
            setControllerShowTimeoutMs(SHOW_TIMEOUT_MS_WHEN_EXIT_DUALMODE);
            break;
        }
      }
    }
  }

  /**
   * The {@link TimeBar} calls this listener to handle scrub behavior.
   *
   * KeyEvents are only delivered by the {@link DualModeTimeBar} to the super
   * if the {@link DualModeTimeBar.KeyEventHandler} methods return false (indicating
   * the event was not consumed).
   *
   */
  private class ScrubEventHandler implements TimeBar.OnScrubListener {
    @Override
    public void onScrubStart(TimeBar timeBar, long position) {
      boolean allowScrubStart = isAllowScrubStart();
      Log.d(TAG, "onScrubStart() - " + position + " trickMode: " + control.getCurrentTrickMode() + " dualMode: " + getCurrentDualMode() + " allowScrubStart:" + allowScrubStart);
      if (allowScrubStart) {
        control.setTrickMode(TrickPlayControl.TrickMode.SCRUB);
      }
    }

    @Override
    public void onScrubMove(TimeBar timeBar, long position) {
      if (control.getCurrentTrickMode() == TrickPlayControl.TrickMode.SCRUB) {
        Log.d(TAG, "onScrubMove() - " + position + " dualMode: " + getCurrentDualMode());
        control.scrubSeek(position);
      }
    }

    @Override
    public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
      Log.d(TAG, "onScrubStop() - " + position + " canceled: " + canceled + " trickMode: " + control.getCurrentTrickMode() + " dualMode: " + getCurrentDualMode());
      scrubStopped(canceled);
    }
  }

  public BaseTimeBarViewHandler(TrickPlayControl control, @NonNull PlayerView playerView, @NonNull DualModeTimeBar timeBar, SimpleExoPlayer simpleExoPlayer) {
    this.playerView = playerView;
    currentTime = playerView.findViewById(R.id.exo_position);
    currentTimeHolder = playerView.findViewById(R.id.current_time_holder);
    initializeJumpButtons(playerView);
    this.control = control;
    this.timeBar = timeBar;
    player = simpleExoPlayer;
    player.addListener(this);
    scrubEventHandler = new ScrubEventHandler();
    timeBar.addListener(scrubEventHandler);
    timeBar.addPressListener(this);
    timeoutManager = new ControllerShowTimeoutManager();
    timeBar.setOnFocusChangeListener(timeoutManager);
    playerView.setControllerVisibilityListener(timeoutManager);
  }

  public void playerDestroyed() {
    Log.d(TAG, "playerDestroyed()");
    player.removeListener(this);
    timeBar.removeListener(scrubEventHandler);
    timeBar.removePressListener(this);
    playerView.setControllerVisibilityListener(null);
    control.removeEventListener(this);
  }


  protected void initializeJumpButtons(@NonNull PlayerView playerView) {
    jumpToStart = playerView.findViewById(R.id.goto_start);
    if (jumpToStart != null) {
      jumpToStart.setOnClickListener(v -> {
        if (player != null) {
          player.seekTo(0);
        }
      });
    }
    jumpForward = playerView.findViewById(R.id.goto_live);
    if (jumpForward != null) {
      jumpForward.setOnClickListener(v -> {
        if (player != null) {
          player.seekToDefaultPosition();
        }
      });
    }
  }

  /**
   * Handle translating TrickPlay mode changes in to Dual Mode mode changes.
   *
   * @param newMode - the trickplay mode currently being played
   * @param prevMode - the previous mode before the change
   */
  @Override
  public void trickPlayModeChanged(TrickPlayControl.TrickMode newMode, TrickPlayControl.TrickMode prevMode) {
    Log.d(TAG, "trickPlayModeChanged - from: " + prevMode + " to: " + newMode + " currentDualMode: " + currentDualMode + " playWhenReady: " + player.getPlayWhenReady() + " controlVisible: " + playerView.isControllerVisible());

    TrickPlayControl.TrickPlayDirection prevDirection = TrickPlayControl.directionForMode(prevMode);
    switch (TrickPlayControl.directionForMode(newMode)) {

      case FORWARD:
      case REVERSE:
        setCurrentDualMode(DualModeMode.SCAN);
        break;
      case NONE:
        if (prevDirection == TrickPlayControl.TrickPlayDirection.SCRUB) {
          setCurrentDualMode(DualModeMode.PLAYING);
        } else if (prevDirection == TrickPlayControl.TrickPlayDirection.FORWARD || prevDirection == TrickPlayControl.TrickPlayDirection.REVERSE) {
          setCurrentDualMode(DualModeMode.SCRUB_TIMED);
        }
        break;
      case SCRUB:
        if (getCurrentDualMode() != DualModeMode.SCRUB_TIMED) {   // allow scrub operations, but with the timeout to playing
          setCurrentDualMode(DualModeMode.SCRUB);
        }
        break;
    }
  }

  /**
   * Show/Hide the player controls without changing any state in the handlers.  For example
   * the INFO key may do this.
   */
  public void toggleTrickPlayBar() {
    timeoutManager.toggleTrickPlayBar();
  }

  /**
   * Called before the player controls are visible with the KeyDown KeyEvent that may bring them
   * to visible.
   *
   * This super method checks conditions if the subclasses should handle the event or not:
   *  1. the timeBar is not focused (if it is, the event would be sent to the {@link DualModeTimeBar.KeyEventHandler})
   *  2. It is not a repeat down event (which again would be handled by the {@link DualModeTimeBar.KeyEventHandler}))
   *
   * Subclasses will decide which events trigger showing the controls and set the initial
   * dualmode state and trickplay state.
   *
   * @param event key down event that triggered initial show of the player controls
   * @return true if the subclass should evaluate showing the controls and setting initial state
   */
  @CallSuper
  public boolean showForEvent(KeyEvent event) {
    return event.getRepeatCount() == 0 && ! timeBar.hasFocus();
  }

  /**
   * Determines what states will allow the timebar to enter scrubbing state
   *
   * @return true if scrubbing allowed.
   */
  protected boolean isAllowScrubStart() {
    DualModeMode mode = getCurrentDualMode();
    return mode == DualModeMode.SCRUB_TIMED || mode == DualModeMode.SCRUB;
  }

  /**
   * Called when scrubbing was stopped.
   *
   * @param canceled if stopped because of user action
   */
  protected void scrubStopped(boolean canceled) {
    if (control.getCurrentTrickMode() == TrickPlayControl.TrickMode.SCRUB) {
      control.setTrickMode(TrickPlayControl.TrickMode.NORMAL);
    }
  }

  /**
   * Exit the current dual-mode trickplay and return to play mode.
   */
  protected void exitToPlayback() {
    setCurrentDualMode(DualModeMode.PLAYING);
    control.setTrickMode(TrickPlayControl.TrickMode.NORMAL);
    player.setPlayWhenReady(true);
  }


  /**
   * Allways leaves in playing mode, disable listeners when not visible
   */
  @CallSuper
  protected void onControlsHiding() {
    control.removeEventListener(this);
    if (getCurrentDualMode() != DualModeMode.NONE) {
      control.setTrickMode(TrickPlayControl.TrickMode.NORMAL);
      player.setPlayWhenReady(true);
      setCurrentDualMode(DualModeMode.NONE);
    }
  }

  protected void onControlsShowing() {
    control.addEventListener(this);
  }


  protected void setCurrentDualMode(DualModeMode nextDualMode) {
    timeoutManager.dualModeModeChanged(getCurrentDualMode(), nextDualMode);
    currentDualMode = nextDualMode;
  }

  protected DualModeMode getCurrentDualMode() {
    return currentDualMode;
  }

  /**
   * Base handler deals with events common to all possible UX variants, it must be called if
   * the sub-class UX varaint does not handle and consume the KeyEvent.
   * <p></p>
   * The {@link DualModeTimeBar.KeyEventHandler} is called from the time bar when it is focused, if the
   * method returns false then the super {@link com.google.android.exoplayer2.ui.DefaultTimeBar} will
   * start or stop scrubbing (SCRUB mode) and call back the {@link ScrubEventHandler}'s
   * <p></p>
   * Event's handled by this method are:
   * <p></p>
   * 1. DPAD_UP when the trickplay bar is focused starts playback and hides the controller, basically
   *    releases focus from the player controls, this is always consumed
   * 2. DPAD_CENTER toggles play/pause state while a Dual Mode is active, this is never consumed to allow
   *    the timebar to exit scrubbing
   * 3. DPAD_LEFT/RIGHT - allowed to propagate to the timebar to trigger scrub start if dualmode mode allows.
   *
   * @param timeBar active timebar
   * @param event the keyevent
   * @return true if this method handles the event, blocks event propagation
   */
  @Override
  @CallSuper
  public boolean handlePress(DualModeTimeBar timeBar, KeyEvent event) {
    Log.d(TAG, "handlePress - playWhenReady: " + player.getPlayWhenReady() + " controlVisible: " + playerView.isControllerVisible() + " trickMode: " + control.getCurrentTrickMode() + " dualMode: " + getCurrentDualMode()
        + " event: " + event);
    boolean handled = false;
    if (timeBar.isFocused()) {
      switch (event.getKeyCode()) {
        case KeyEvent.KEYCODE_DPAD_UP:      // UP with TP bar focused exits and hide TP bar
          exitToPlayback();
          handled = true;
          break;

        case KeyEvent.KEYCODE_DPAD_CENTER: // CENTER toggles play/pause. toggling to play, sets timeout for hiding the TP bar
          handleDpadCenter();
          // NOTE handled = false, this allows the TimeBar to cancel SCRUBing if needed.
          break;

        case KeyEvent.KEYCODE_DPAD_LEFT:  // consume (handled set true) if we don't allow scrubbing to start.
        case KeyEvent.KEYCODE_DPAD_RIGHT:
          handled = ! isAllowScrubStart();
      }
    }
    return handled;
  }

  /**
   * Converts the D-Pad key (L/R) into matching MEDIA key and delegates to
   * {@link TransportControlHandler} to match MEDIA key handing.
   *
   * @param event KeyEvent if navigation converted to MEDIA key
   * @return new TrickMode or null if no change (not handled)
   */
  protected TrickPlayControl.TrickMode trickModeForEvent(KeyEvent event) {
    TrickPlayControl.TrickMode currentTrickMode = control.getCurrentTrickMode();
    Log.d(TAG, "trickModeForEvent() - trickMode: " + currentTrickMode + " dualMode: " + getCurrentDualMode() + " event: " + event);
    TrickPlayControl.TrickMode nextTrickMode = null;
    switch (event.getKeyCode()) {
      case KeyEvent.KEYCODE_DPAD_LEFT:
        nextTrickMode = TransportControlHandler.nextTrickMode(currentTrickMode, KeyEvent.KEYCODE_MEDIA_REWIND);
        break;

      case KeyEvent.KEYCODE_DPAD_RIGHT:
        nextTrickMode = TransportControlHandler.nextTrickMode(control.getCurrentTrickMode(), KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
        break;

      case KeyEvent.KEYCODE_DPAD_CENTER:
        nextTrickMode = TrickPlayControl.TrickMode.NORMAL;
        break;
    }
    return nextTrickMode == currentTrickMode ? null : nextTrickMode;
  }

  /**
   * The D-PAD CENTER (select) key toggles the current Dual Mode and pause/play.
   * <p></p>
   * On entry the state is {@link DualModeMode#NONE}. All the other Dual Mode modes are also possible, the transition
   * to {@link Player#getPlayWhenReady()} true (playing) to/from false (paused) is based on the Dual Mode mode
   * tranition.
   * <p></p>
   * When paused there are two possible scrubbing states, {@link DualModeMode#SCRUB} they differ by the timeout applied
   * <p></p>
   * The transition alters the controls auto-hide timer, see {@link ControllerShowTimeoutManager#dualModeModeChanged(DualModeMode, DualModeMode)}
   * method description.
   *
   */
  protected void handleDpadCenter() {
    Log.d(TAG, "handleDpadCenter - playWhenReady: " + player.getPlayWhenReady() + " controlVisible: " + playerView.isControllerVisible()
        + " hideTimer: " + playerView.getControllerShowTimeoutMs() + " trickMode: " + control.getCurrentTrickMode() + " dualMode: " + getCurrentDualMode());

    switch (getCurrentDualMode()) {
      case SCRUB:
      case SCRUB_TIMED:
        player.play();     // D-Pad Center in SCRUB mode (or implied SCRUB, SCRUB_TIMED) un-pauses
        setCurrentDualMode(DualModeMode.PLAYING);
        break;

      case SCAN:
        player.pause();
        setCurrentDualMode(DualModeMode.SCRUB_TIMED);
        break;

      case PLAYING:
      case NONE:
        player.pause();     // D-Pad Center pauses playback, any L/R move will start trickplay SCRUB mode.
        setCurrentDualMode(DualModeMode.SCRUB);
        break;
    }
  }

  @Override
  public void onTimelineChanged(Timeline timeline, int reason) {
    if (timeline.getWindowCount() == 1) {   // support single window timelines only
      Timeline.Window window = timeline.getWindow(0, new Timeline.Window());
      if (window.getDurationMs() != durationMs) {
        Log.d(TAG, "onTimelineChanged() duration updated - isPlaceholder: " + window.isPlaceholder
            + " durationMs: " + window.getDurationMs()
            + " isEmpty(): " + timeline.isEmpty());
      }
      durationMs = window.getDurationMs();
    } else {
      durationMs = C.TIME_UNSET;
      Log.d(TAG, "no window in update, reason: " + reason);
    }
  }

  @Override
  public void onProgressUpdate(long position, long bufferedPosition) {
    if (durationMs != C.TIME_UNSET) {
      int width = timeBar.getWidth();
      if (width > 0) {
        currentTimeHolder.setVisibility(View.VISIBLE);
        float pixelsPerMs = (float) width / durationMs;
        long pixelOffset = (long) (position * pixelsPerMs);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) currentTimeHolder.getLayoutParams();
        long xCenter = currentTimeHolder.getWidth() / 2;
        Log.d(TAG, "onProgressUpdate - position: " + position + " duration: " + durationMs + " width: " + width + " timeWidth: " + currentTime.getWidth() + " xCenter: " + xCenter + " pixelOffset: " + pixelOffset);
        if (pixelOffset > xCenter) {
          lp.leftMargin = (int) ((int) pixelOffset - xCenter);
        } else {
          lp.leftMargin = (int) pixelOffset;
        }
        currentTimeHolder.setLayoutParams(lp);
      } else {
        currentTimeHolder.setVisibility(View.INVISIBLE);    // hide till position can be determined.
        Log.d(TAG, "onProgressUpdate() timeBar no width - position: " + position );
      }
    } else {
      Log.d(TAG, "onProgressUpdate() no duration - position: " + position );
    }
  }
}
