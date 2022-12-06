package com.tivo.exoplayer.library.logging;

import android.util.Log;

import com.google.android.exoplayer2.LivePlaybackSpeedControl;
import com.google.android.exoplayer2.MediaItem;

public class LoggingLivePlaybackSpeedControl implements LivePlaybackSpeedControl
{
    final LivePlaybackSpeedControl livePlaybackSpeedControlDelegate;
    float lastAdjustedPlaybackSpeed = 0.0f;
    long lastTargetLiveOffset = 0;
    final String TAG = "LoggingLivePlaybackSpeedControl";

    public LoggingLivePlaybackSpeedControl(LivePlaybackSpeedControl livePlaybackSpeedControl) {
        livePlaybackSpeedControlDelegate = livePlaybackSpeedControl;
    }
    @Override
    public void setLiveConfiguration(MediaItem.LiveConfiguration liveConfiguration) {
        livePlaybackSpeedControlDelegate.setLiveConfiguration(liveConfiguration);
    }

    @Override
    public void setTargetLiveOffsetOverrideUs(long liveOffsetUs) {
        livePlaybackSpeedControlDelegate.setTargetLiveOffsetOverrideUs(liveOffsetUs);
    }

    @Override
    public void notifyRebuffer() {
        livePlaybackSpeedControlDelegate.notifyRebuffer();
        Log.d(TAG, "after rebuffer notify.");
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
        if (adjustedPlaybackSpeed != lastAdjustedPlaybackSpeed) {
            long currentTargetLiveOffsetUs = livePlaybackSpeedControlDelegate.getTargetLiveOffsetUs();
            //
            Log.d(TAG, String.format("adjustedPlaybackSpeed %9.7f, liveOffset %3.2fs, currentTargetLiveOffset %3.2fs, buffer %5.1fs",
                adjustedPlaybackSpeed,
                liveOffsetUs / 1_000_000.0,
                currentTargetLiveOffsetUs / 1_000_000.0,
                bufferedDurationUs / 1_000_000.0));

            lastAdjustedPlaybackSpeed = adjustedPlaybackSpeed;
        }
        return adjustedPlaybackSpeed;
    }

    @Override
    public long getTargetLiveOffsetUs() {
        long targetLiveOffsetUs = livePlaybackSpeedControlDelegate.getTargetLiveOffsetUs();
        if (targetLiveOffsetUs != lastTargetLiveOffset) {
            Log.d(TAG, String.format("targetLiveOffset %3.2fs",
                    targetLiveOffsetUs / 1_000_000.0));
            lastTargetLiveOffset = targetLiveOffsetUs;
        }
        return targetLiveOffsetUs;
    }
}
