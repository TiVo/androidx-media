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
    }

    @Override
    public float getAdjustedPlaybackSpeed(long liveOffsetUs, long bufferedDurationUs) {
        float adjustedPlaybackSpeed = livePlaybackSpeedControlDelegate.getAdjustedPlaybackSpeed(liveOffsetUs, bufferedDurationUs);
        if (adjustedPlaybackSpeed != lastAdjustedPlaybackSpeed) {
            Log.d(TAG, String.format("adjustedPlaybackSpeed %9.7f liveOffset %5.1fs buffer %5.1fs",
                    adjustedPlaybackSpeed,
                    ((float)liveOffsetUs)/1_000_000.0,
                    ((float)bufferedDurationUs)/1_000_000.0));

            lastAdjustedPlaybackSpeed = adjustedPlaybackSpeed;
        }
        return adjustedPlaybackSpeed;
    }

    @Override
    public long getTargetLiveOffsetUs() {
        long targetLiveOffsetUs = livePlaybackSpeedControlDelegate.getTargetLiveOffsetUs();
        if (targetLiveOffsetUs != lastTargetLiveOffset) {
            Log.d(TAG, "targetLiveOffsetUs " + targetLiveOffsetUs);
            lastTargetLiveOffset = targetLiveOffsetUs;
        }
        return targetLiveOffsetUs;
    }
}
