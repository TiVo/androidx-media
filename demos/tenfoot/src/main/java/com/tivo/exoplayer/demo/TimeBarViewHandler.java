package com.tivo.exoplayer.demo;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.HashMap;
import java.util.Map;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ui.TimeBar;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;

public class TimeBarViewHandler implements TimeBar.OnScrubListener,
        PlayerControlView.ProgressUpdateListener,
        Player.Listener, DualModeTimeBar.PressEventHandler {

    public static final String TAG = "TimeBarViewHandler";
    private static final boolean HOLD_SWITCHES_MODE = false;
    private final TrickPlayControl control;
    private final View currentTime;
    private final ViewGroup currentTimeHolder;
    private final View pauseButton;
    private long durationMs = C.TIME_UNSET;
    private final View timeBar;
    private final Map<Integer, TrickPlayControl.TrickMode>[] modeByHoldTime = new Map[3];
    private int[] holdThresholds;
    private SimpleExoPlayer player;

    TimeBarViewHandler(TrickPlayControl control, PlayerView playerView) {
        this.control = control;
        if (HOLD_SWITCHES_MODE) {
            holdThresholds = new int[] {1_500, 3_000, 3_800};
        } else {
            holdThresholds = new int[] {2_000};
        }
        pauseButton = playerView.findViewById(R.id.exo_pause);
        currentTime = playerView.findViewById(R.id.exo_position);
        currentTimeHolder = playerView.findViewById(R.id.current_time_holder);
        timeBar = playerView.findViewById(R.id.exo_progress);
        if (timeBar instanceof DualModeTimeBar) {
            DualModeTimeBar dualModeTimeBar = (DualModeTimeBar) this.timeBar;
            dualModeTimeBar.setLongPressThreshold(holdThresholds[0]);
            dualModeTimeBar.addLongPressListener(this);
        }

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

    private boolean setFastPlayForTimeHeld(long timeHeld, int keyCode) {
        boolean handled = false;
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
                handled = true;
            }
        }
        return handled;
    }

    // OnScrubListener

    @Override
    public void onScrubStart(TimeBar timeBar, long position) {
        Log.d(TAG, "onScrubStart() - " + position + " currentMode: " + control.getCurrentTrickMode() + " playbackState: " + player.getPlaybackState());
        control.setTrickMode(TrickPlayControl.TrickMode.SCRUB);
    }

    @Override
    public void onScrubMove(TimeBar timeBar, long position) {
        Log.d(TAG, "onScrubMove() - " + position + " currentMode: " + control.getCurrentTrickMode() + " playbackState: " + player.getPlaybackState());
        control.scrubSeek(position);
    }

    @Override
    public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
        Log.d(TAG, "onScrubStop() - " + position + " canceled: " + canceled + " currentMode: " + control.getCurrentTrickMode() + " playbackState: " + player.getPlaybackState());

        if (control.getCurrentTrickMode() == TrickPlayControl.TrickMode.SCRUB) {
            control.setTrickMode(TrickPlayControl.TrickMode.NORMAL);
        }
    }

    // LongPressEventListener

    @Override
    public boolean handleLongPress(DualModeTimeBar dualModeTimeBar, KeyEvent event) {
        boolean handled = false;
        KeyEvent lastDownKeyEvent = dualModeTimeBar.getLastDownKeyEvent();
        if (lastDownKeyEvent != null) {
            handled = setFastPlayForTimeHeld(dualModeTimeBar.getCurrentHoldTime(), lastDownKeyEvent.getKeyCode());
        }
        return handled;
    }

    @Override
    public boolean handlePress(DualModeTimeBar timeBar, KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (player != null) {
                player.setPlayWhenReady( ! player.getPlayWhenReady());
                return true;
            }
        }
        return false;
    }

    @Override
    public void longPressEnded(DualModeTimeBar timeBar) {
        Log.d(TAG, "longPressEnded - playWhenReady: " + player.getPlayWhenReady() + " mode: " + control.getCurrentTrickMode());
        if (HOLD_SWITCHES_MODE) {
            control.setTrickMode(TrickPlayControl.TrickMode.NORMAL);
        } else {
            Log.d(TAG, "staying in current trickmode: " + control.getCurrentTrickMode());
        }
    }


    // Player.Listener

    @Override
    public void onTimelineChanged(Timeline timeline, int reason) {
        durationMs = 0;
        if (! timeline.isEmpty()) {
            if (timeline.getWindowCount() == 1) {
                // Legacy behavior was to report the manifest for single window timelines only.
                Timeline.Window window = new Timeline.Window();
                timeline.getWindow(0, window);
                durationMs = C.usToMs(window.durationUs);

            }
        }
    }

    // ProgressUpdateListener

    @Override
    public void onProgressUpdate(long position, long bufferedPosition) {
        if (durationMs != C.TIME_UNSET) {
            int width = timeBar.getWidth();
            if (width > 0) {
                float pixelsPerMs = (float) width / durationMs;
                long pixelOffset = (long) (position * pixelsPerMs);
                long xOffset = Util.constrainValue( pixelOffset - (currentTime.getWidth() / 2), 5, width - currentTime.getWidth());
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) currentTimeHolder.getLayoutParams();
//                Log.d(TAG, "progressUpdate - position: " + position + " duration: " + durationMs + " width: " + width + " timeWidth: " + currentTime.getWidth() + " xOffset: " + xOffset);
                lp.leftMargin = (int) xOffset;
                currentTimeHolder.setLayoutParams(lp);
            }
        }
    }

    public void setPlayer(SimpleExoPlayer player) {
        this.player = player;
    }
}
