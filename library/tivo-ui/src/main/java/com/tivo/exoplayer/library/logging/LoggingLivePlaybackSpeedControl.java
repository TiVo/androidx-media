package com.tivo.exoplayer.library.logging;

import android.annotation.SuppressLint;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.LivePlaybackSpeedControl;
import com.google.android.exoplayer2.MediaItem;

public class LoggingLivePlaybackSpeedControl implements LivePlaybackSpeedControl
{

    public static int TARGET_LIVE_OFFSET_LOG_THRESHOLD = 3_000_000;
    public static boolean LOG_ALL_SPEED_CHANGES = false;

    private final String TAG = "LoggingLivePlaybackSpeedControl";

    enum AdjustmentDirection {LOWER, HIGHER, NONE};

    private final LivePlaybackSpeedControl livePlaybackSpeedControlDelegate;

    /**
     * Tracks changes each call to the playback speed the delegate makes. This value
     * potentially changes in the delegate only on each call to {@link #getAdjustedPlaybackSpeed(long, long)}
     */
    private float lastAdjustedPlaybackSpeed = 0.0f;
    private AdjustmentDirection lastPlaybackSpeedDirection = AdjustmentDirection.NONE;

    /**
     * Track last {@link #getTargetLiveOffsetUs()} value that we logged.  This value is constantly being
     * updated by the delegate relative to the starting {@link #idealTargetLiveOffsetUs} value. Changes in buffering level,
     * rebuffering events and the smoothing function that controls how quickly the speed adjustment value.  We
     * log when the change exceeds {@link #TARGET_LIVE_OFFSET_LOG_THRESHOLD}
     */
    private long lastTargetLiveOffset = C.TIME_UNSET;

    /**
     * track value in delegate, the idealTargetLiveOffsetUs is what was set from the call to
     * {@link #setLiveConfiguration(MediaItem.LiveConfiguration)} or overriden by the {@link #setTargetLiveOffsetOverrideUs(long)}
     *
     * After each of these calls. {@link #getTargetLiveOffsetUs()} will return  idealTargetLiveOffsetUs
     */
    private long idealTargetLiveOffsetUs = C.TIME_UNSET;

    /**
     * Construct this {@link LivePlaybackSpeedControl} wrapper class with the specified delegate
     *
     * @param livePlaybackSpeedControl - delegate performs the acutal Live Playback adjustment decisions
     */
    public LoggingLivePlaybackSpeedControl(LivePlaybackSpeedControl livePlaybackSpeedControl) {
        livePlaybackSpeedControlDelegate = livePlaybackSpeedControl;
    }

    // Implement LivePlaybackSpeedControl by delegating and optionally logging

    /**
     * Override to capture the {@link #idealTargetLiveOffsetUs} value
     *
     * @param liveConfiguration The {@link MediaItem.LiveConfiguration} as defined by the media.
     */
    @Override
    public void setLiveConfiguration(MediaItem.LiveConfiguration liveConfiguration) {
        long initialTargetLiveOffset = livePlaybackSpeedControlDelegate.getTargetLiveOffsetUs();

        livePlaybackSpeedControlDelegate.setLiveConfiguration(liveConfiguration);

        logLiveConfigurationUpdate(initialTargetLiveOffset);
    }

    /**
     * Override to capture the {@link #idealTargetLiveOffsetUs} value
     *
     * This method is called when the player seeks during live playback
     *
     * TODO - a new "delegate" should veto this change and stop adjustment until we return to the live edge
     *
     * @param liveOffsetUs override live offset target value, or {@code C.TIME_UNSET} to cancel override
     */
    @Override
    public void setTargetLiveOffsetOverrideUs(long liveOffsetUs) {
        Log.d(TAG, String.format("setTargetLiveOffsetOverrideUs(%3.2f), idealTargetLiveOffsetUs was: %3.2fs",
            liveOffsetUs / 1_000_000.0,
            idealTargetLiveOffsetUs / 1_000_000.0
        ));
        livePlaybackSpeedControlDelegate.setTargetLiveOffsetOverrideUs(liveOffsetUs);
        idealTargetLiveOffsetUs = livePlaybackSpeedControlDelegate.getTargetLiveOffsetUs();
        lastTargetLiveOffset = idealTargetLiveOffsetUs;
    }

    @Override
    public void notifyRebuffer() {
        long previousTargetLiveOffset = livePlaybackSpeedControlDelegate.getTargetLiveOffsetUs();
        livePlaybackSpeedControlDelegate.notifyRebuffer();
        logForNotifyRebuffer(previousTargetLiveOffset);
    }

    /**
     * Override to log interesting details while adjustment is in progress.
     *
     * Assumptions are:
     * <ol>
     *   <li>This method is called each main player loop (doSomeWork(), called
     *        once every 10ms while playback is active) </li>
     *   <li>The super method will only change playback speed from 1.0 when liveOffset is not at
     *   target and at most once per second ({@link com.google.android.exoplayer2.DefaultLivePlaybackSpeedControl#DEFAULT_MIN_UPDATE_INTERVAL_MS}</li>
     * </ol>
     *
     * The second assumption makes sure logging is not excessive.
     *
     * @param liveOffsetUs The current live offset, in microseconds.
     * @param bufferedDurationUs The duration of media that's currently buffered, in microseconds.
     * @return same value as super method
     */
    @Override
    public float getAdjustedPlaybackSpeed(long liveOffsetUs, long bufferedDurationUs) {
        float adjustedPlaybackSpeed = livePlaybackSpeedControlDelegate.getAdjustedPlaybackSpeed(liveOffsetUs, bufferedDurationUs);
        logLivePlaybackSpeedAdjustments(liveOffsetUs, bufferedDurationUs, adjustedPlaybackSpeed);
        return adjustedPlaybackSpeed;
    }

    @Override
    public long getTargetLiveOffsetUs() {
        return livePlaybackSpeedControlDelegate.getTargetLiveOffsetUs();
    }

    // end of interface LivePlaybackSpeedControl

    // internal private methods


    /**
     * Log when {@link #setLiveConfiguration(MediaItem.LiveConfiguration)} is called.  This call can
     * update the ideal and current live offset targets if the value for {@link MediaItem.LiveConfiguration#targetOffsetMs}
     * changes.
     *
     * @param initialTargetLiveOffset - current value ({@link #getTargetLiveOffsetUs()}) before delegate call
     */
    private void logLiveConfigurationUpdate(long initialTargetLiveOffset) {
        long afterSetLiveConfigTargetLiveOffset = livePlaybackSpeedControlDelegate.getTargetLiveOffsetUs();
        if (initialTargetLiveOffset != afterSetLiveConfigTargetLiveOffset) {
            if (initialTargetLiveOffset == C.TIME_UNSET) {
                Log.i(TAG, String.format("setLiveConfiguration() init idealTargetLiveOffsetUs: %3.2f",
                    afterSetLiveConfigTargetLiveOffset / 1_000_000.0));
            } else {
                Log.i(TAG, String.format("setLiveConfiguration() update idealTargetLiveOffsetUs from: %3.2f to: %3.2f",
                    initialTargetLiveOffset / 1_000_000.0,
                    afterSetLiveConfigTargetLiveOffset / 1_000_000.0));
            }
            idealTargetLiveOffsetUs = livePlaybackSpeedControlDelegate.getTargetLiveOffsetUs();
            lastTargetLiveOffset = idealTargetLiveOffsetUs;
        }
    }

    private void logForNotifyRebuffer(long previousTargetLiveOffset) {
        if (previousTargetLiveOffset != C.TIME_UNSET) {
            long currentTargetLiveOffset = livePlaybackSpeedControlDelegate.getTargetLiveOffsetUs();
            Log.i(TAG, String.format("on rebuffer move targetLiveOffset - previous: %3.2f, current %3.2f, delta %2.2f",
                previousTargetLiveOffset / 1_000_000.0,
                currentTargetLiveOffset / 1_000_000.0,
                (currentTargetLiveOffset - previousTargetLiveOffset) / 1_000_000.0
            ));
        }
    }

    private void logLivePlaybackSpeedAdjustments(long liveOffsetUs, long bufferedDurationUs, float adjustedPlaybackSpeed) {
        // After calling the delegate, figure out what changed and log appropriately
        AdjustmentDirection updatedPlaybackSpeedDirection = getPlaybackSpeedDirection(adjustedPlaybackSpeed);

        // figure out what to log and what level
        long currentTargetLiveOffset = livePlaybackSpeedControlDelegate.getTargetLiveOffsetUs();
        boolean speedChanged = adjustedPlaybackSpeed != lastAdjustedPlaybackSpeed;
        boolean playbackSpeedDirectionChanged = updatedPlaybackSpeedDirection != lastPlaybackSpeedDirection;
        boolean logTargetOffsetChange = Math.abs(lastTargetLiveOffset - currentTargetLiveOffset) >
                (playbackSpeedDirectionChanged ?
                    0
                    : TARGET_LIVE_OFFSET_LOG_THRESHOLD);

        // Log live offset changes first, all changes if logging direction else only if over threshold

        if (logTargetOffsetChange) {
            Log.i(TAG, String.format("changed targetLiveOffset from: %3.2f to: %3.2f (delta %3.2f), liveOffset: %3.2f, playbackSpeed %1.3fx, idealTargetLiveOffset %3.2f, buffer %5.1f",
                lastTargetLiveOffset / 1_000_000.0,
                currentTargetLiveOffset / 1_000_000.0,
                (currentTargetLiveOffset - lastTargetLiveOffset) / 1_000_000.0,
                liveOffsetUs / 1_000_000.0,
                adjustedPlaybackSpeed,
                idealTargetLiveOffsetUs / 1_000_000.0,
                bufferedDurationUs / 1_000_000.0));
            lastTargetLiveOffset = currentTargetLiveOffset;
        }

        // Then if playback speed changes to or from unit speed (1.0x)

        if (playbackSpeedDirectionChanged) {
            Log.i(TAG, playbackSpeedAdjustmentDirectionChangedMessage(updatedPlaybackSpeedDirection, lastPlaybackSpeedDirection, adjustedPlaybackSpeed,
                currentTargetLiveOffset, liveOffsetUs, bufferedDurationUs));
        } else if (speedChanged && LOG_ALL_SPEED_CHANGES) {
            Log.d(TAG, playbackSpeedAdjustmentDirectionChangedMessage(updatedPlaybackSpeedDirection, lastPlaybackSpeedDirection, adjustedPlaybackSpeed,
                currentTargetLiveOffset, liveOffsetUs, bufferedDurationUs));
        }

        lastAdjustedPlaybackSpeed = adjustedPlaybackSpeed;
        lastPlaybackSpeedDirection = updatedPlaybackSpeedDirection;
    }

    @SuppressLint("DefaultLocale")
    private String playbackSpeedAdjustmentDirectionChangedMessage(AdjustmentDirection updatedPlaybackSpeedDirection,
        AdjustmentDirection previousPlaybackSpeedDirection,
        float adjustedPlaybackSpeed,
        long currentTargetLiveOffset, long liveOffsetUs, long bufferedDurationUs) {
        String logString = null;
        String action = previousPlaybackSpeedDirection == AdjustmentDirection.NONE ? "intiating" : "continuing";
        switch (updatedPlaybackSpeedDirection) {
            case LOWER:
                logString = String.format("%s slow playback (%1.3fx) while liveOffset %3.2f < targetLiveOffset %3.2f, idealTargetLiveOffset %3.2f, buffer %5.1f",
                    action,
                    adjustedPlaybackSpeed,
                    liveOffsetUs / 1_000_000.0,
                    currentTargetLiveOffset / 1_000_000.0,
                    idealTargetLiveOffsetUs / 1_000_000.0,
                    bufferedDurationUs / 1_000_000.0);
                break;
            case HIGHER:
                logString = String.format("%s fast playback (%1.3fx) while liveOffset %3.2f > targetLiveOffset %3.2f, idealTargetLiveOffset %3.2f, buffer %5.1f",
                    action,
                    adjustedPlaybackSpeed,
                    liveOffsetUs / 1_000_000.0,
                    currentTargetLiveOffset / 1_000_000.0,
                    idealTargetLiveOffsetUs / 1_000_000.0,
                    bufferedDurationUs / 1_000_000.0);
                break;
            case NONE:
                logString = String.format("Adjustment stopped with liveOffset %3.2f, targetLiveOffset %3.2f, idealTargetLiveOffset %3.2f, buffer %5.1f",
                    liveOffsetUs / 1_000_000.0,
                    currentTargetLiveOffset / 1_000_000.0,
                    idealTargetLiveOffsetUs / 1_000_000.0,
                    bufferedDurationUs / 1_000_000.0);

                break;
        }
        return logString;
    }

    private AdjustmentDirection getPlaybackSpeedDirection(float adjustedPlaybackSpeed) {
        if (adjustedPlaybackSpeed > 1.0f) {
            return AdjustmentDirection.HIGHER;
        } else if (adjustedPlaybackSpeed < 1.0f) {
            return AdjustmentDirection.LOWER;
        } else {
            return AdjustmentDirection.NONE;
        }
    }

}
