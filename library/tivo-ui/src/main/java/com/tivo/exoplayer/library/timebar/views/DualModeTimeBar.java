package com.tivo.exoplayer.library.timebar.views;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.KeyEvent;
import androidx.annotation.Nullable;

import com.tivo.exoplayer.library.R;
import java.util.concurrent.CopyOnWriteArraySet;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ui.DefaultTimeBar;
import com.google.android.exoplayer2.util.Log;

public class DualModeTimeBar extends DefaultTimeBar {
    private static final String TAG = "DualModeTimebar";
    private static final boolean REVERSE_LONG_PRESS_TRIGGER_ENABLED = true;
    private KeyEvent lastDownKeyEvent = null;
    private long longPressThreshold = Long.MAX_VALUE;
    private final CopyOnWriteArraySet<KeyEventHandler> listeners = new CopyOnWriteArraySet<>();

    /**
     * Call-backs to handle KeyEvent's when the DefaultTimeBar has focus.
     */
    public interface KeyEventHandler {

        /**
         * Called for all {@link KeyEvent#ACTION_DOWN} events when the timebar is enabled
         * and the event is not determined to be a long press that is a navigation key
         *
         * @param timeBar active timebar
         * @param event the keyevent
         * @return true if the event was handled and {@link DefaultTimeBar#onKeyDown(int, KeyEvent)} should
         *         <b>not</b> be called.
         */
        boolean handlePress(DualModeTimeBar timeBar, KeyEvent event);

        /**
         * Override this method to respond to the long press, this method is called if the key
         * is held passed the value set to {@link DualModeTimeBar#setLongPressThreshold(long)}.
         * If the time value is not set this method is never called.
         *
         * @param timeBar the focused {@link DualModeTimeBar} that received the event
         * @param event the event received
         */
        default void handleLongPress(DualModeTimeBar timeBar, KeyEvent event) {};
        default void longPressEnded(DualModeTimeBar timeBar) {}
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

    /**
     * Set key down hold time required to trigger long press, default is infinite (no long press)
     *
     * @param timeMs if hold time is greater than this value it is a long press
     */
    public void setLongPressThreshold(long timeMs) {
        longPressThreshold = timeMs;
    }

    public long getCurrentHoldTime() {
        return lastDownKeyEvent == null ? C.TIME_UNSET : lastDownKeyEvent.getEventTime() - lastDownKeyEvent.getDownTime();
    }

    public @Nullable KeyEvent getLastDownKeyEvent() {
        return lastDownKeyEvent;
    }

    public void addPressListener(KeyEventHandler listener) {
        listeners.add(listener);
    }

    public void removePressListener(KeyEventHandler listener) {
        listeners.remove(listener);
    }

    /**
     * The {@link DefaultTimeBar#stopScrubbing(boolean)} method triggers undesirable behavior
     * in the {@link com.google.android.exoplayer2.ui.PlayerControlView} where it seeks to
     * the current displayed scubber position rather than honoring the current displayed frame.
     * Setting cancled to true disables this rouge behavior.
     *
     * @param canceled - ignored
     */
    @Override
    protected void stopScrubbing(boolean canceled) {
        super.stopScrubbing(true);
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
                for (KeyEventHandler listener : listeners) {
                    listener.handleLongPress(this, lastDownKeyEvent);
                }
                handled = true;
            } else {
                for (KeyEventHandler listener : listeners) {
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
        for (KeyEventHandler listener : listeners) {
            listener.longPressEnded(this);
        }
        return true;
    }
}
