package com.tivo.exoplayer.demo;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.Checkable;
import androidx.annotation.Nullable;

import java.util.concurrent.CopyOnWriteArraySet;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ui.DefaultTimeBar;
import com.google.android.exoplayer2.util.Log;

public class DualModeTimeBar extends DefaultTimeBar {
    private static final String TAG = "DualModeTimebar";
    private static final boolean REVERSE_LONG_PRESS_TRIGGER_ENABLED = true;
    private KeyEvent lastDownKeyEvent = null;
    private long longPressThreshold = C.TIME_UNSET;
    private final CopyOnWriteArraySet<PressEventHandler> listeners = new CopyOnWriteArraySet<>();
    private static final int[] CHECKED_STATE_SET = { android.R.attr.state_checked };
    private boolean checked;        // Used to store play/pause state.

    interface PressEventHandler {
        default boolean handlePress(DualModeTimeBar timeBar, KeyEvent event) { return false; }
        default boolean handleLongPress(DualModeTimeBar timeBar, KeyEvent event) { return false; }
        void longPressEnded(DualModeTimeBar timeBar);
    }
    public DualModeTimeBar(Context context) {
        this(context, null);
    }

    public DualModeTimeBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs,0);
    }

    public DualModeTimeBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, null);
    }

    public DualModeTimeBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr, @Nullable AttributeSet timebarAttrs) {
        super(context, attrs, defStyleAttr, timebarAttrs);
        Resources res = context.getResources();

        int bufferedColor = DEFAULT_BUFFERED_COLOR;
        int playedColor = DEFAULT_PLAYED_COLOR;
        int unplayedColor = DEFAULT_UNPLAYED_COLOR;
        int scrubberColor = DEFAULT_SCRUBBER_COLOR;
        Drawable scrubber = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bufferedColor = res.getColor(R.color.tpBufferedColor, context.getTheme());
            playedColor = res.getColor(R.color.tpPlayedColor, context.getTheme());
            unplayedColor = res.getColor(R.color.tpUnplayedColor, context.getTheme());
            scrubberColor = res.getColor(R.color.tpScrubberColor, context.getTheme());
            scrubber = res.getDrawable(R.drawable.ic_scrubber, context.getTheme());
        } else {
            bufferedColor = res.getColor(R.color.tpBufferedColor);
            playedColor = res.getColor(R.color.tpPlayedColor);
            unplayedColor = res.getColor(R.color.tpUnplayedColor);
            scrubberColor = res.getColor(R.color.tpScrubberColor);
            scrubber = res.getDrawable(R.drawable.ic_scrubber);
        }
        setBufferedColor(bufferedColor);
        setPlayedColor(playedColor);
        setUnplayedColor(unplayedColor);
        setScrubberColor(scrubberColor);
        setScrubberDrawable(scrubber);
    }

    public boolean isLongHorizontalNavigationPress(KeyEvent event) {
        boolean isKeyDown = event.getAction() == KeyEvent.ACTION_DOWN;
        int keyCode = event.getKeyCode();
        boolean isHorizontal = false;
        if (REVERSE_LONG_PRESS_TRIGGER_ENABLED) {
            isHorizontal = keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
        } else {
            isHorizontal = keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
        }
        long holdTime = event.getEventTime() - event.getDownTime();
        boolean isHoldTimeThresholdHit = isKeyDown && holdTime >= longPressThreshold;
        Log.d(TAG, "isLongPressActive() - holdTime: " + holdTime + " isKeyDown: " + isKeyDown + " isHorizontal: " + isHorizontal + " isHoldTimeThreshold: " +isHoldTimeThresholdHit);
        return isHorizontal && isHoldTimeThresholdHit;
    }

    public void setLongPressThreshold(long timeMs) {
        longPressThreshold = timeMs;
    }

    public long getCurrentHoldTime() {
        return lastDownKeyEvent == null ? C.TIME_UNSET : lastDownKeyEvent.getEventTime() - lastDownKeyEvent.getDownTime();
    }

    public @Nullable KeyEvent getLastDownKeyEvent() {
        return lastDownKeyEvent;
    }

    public void addLongPressListener(PressEventHandler listener) {
        listeners.add(listener);
    }

    public void removeLongPressListener(PressEventHandler listener) {
        listeners.remove(listener);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyDown - scrubbing: " + scrubbing + " focused: " + isFocused() + " event: " + event);
        boolean handled = false;
        if (isEnabled()) {
            if (isLongHorizontalNavigationPress(event)) {
                lastDownKeyEvent = event;
                if (scrubbing) {
                    stopScrubbing(true);
                }
                for (PressEventHandler listener : listeners) {
                    listener.handleLongPress(this, lastDownKeyEvent);
                }
                handled = true;
            } else if (! scrubbing) {
                for (PressEventHandler listener : listeners) {
                    handled |= listener.handlePress(this, event);
                }
            }
        }

        return handled || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyUp - scrubbing: " + scrubbing + " focused: " + isFocused() + " event: " + event + " lastDownEvent: " + lastDownKeyEvent);
        boolean handled = false;
        if (lastDownKeyEvent != null) {
            handled = resetLongPress();
        }
        return handled || super.onKeyUp(keyCode, event);
    }

    private boolean resetLongPress() {
        lastDownKeyEvent = null;
        for (PressEventHandler listener : listeners) {
            listener.longPressEnded(this);
        }
        return true;
    }
}
