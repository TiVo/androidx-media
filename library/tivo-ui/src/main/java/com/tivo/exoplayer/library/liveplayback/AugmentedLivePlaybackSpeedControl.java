package com.tivo.exoplayer.library.liveplayback;

import androidx.annotation.NonNull;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.LivePlaybackSpeedControl;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.util.Log;

/**
 * Delegate for {@link LivePlaybackSpeedControl} that augments the {@link com.google.android.exoplayer2.DefaultLivePlaybackSpeedControl}
 * functionality by turning off Live Offset adjustment when playback position significantly deviates from live.
 */
public class AugmentedLivePlaybackSpeedControl implements LivePlaybackSpeedControl {

    /**
     * Live Adjustment is only enabled when the position (as implied by call to {@link #setTargetLiveOffsetOverrideUs(long)})
     * is less than this difference from the {@link MediaItem.LiveConfiguration#targetOffsetMs}
     *
     * Set to value that is accommodates where trickplay forward most likely will exit.  Note a seek within this
     * position will adjust back to live, the value must be large enough to cover where trickplay will exit for
     * origins that do not update the trickplay track in real time.
     */
    public static long MAX_LIVE_OFFSET_DELTA_MS = 120 * 1_000;   // 120s

    private final String TAG = "AugmentedLivePlaybackSpeedControl";

    private final LivePlaybackSpeedControl delegate;
    private MediaItem.LiveConfiguration lastLiveConfiguration = MediaItem.LiveConfiguration.UNSET;
    private boolean enableLiveAdjustment;

    public AugmentedLivePlaybackSpeedControl(LivePlaybackSpeedControl upstreamLivePlaybackSpeedControl) {
        delegate = upstreamLivePlaybackSpeedControl;
        enableLiveAdjustment = true;
    }

    @Override
    public void setLiveConfiguration(@NonNull MediaItem.LiveConfiguration liveConfiguration) {
        updateEnableFromLiveConfiguration(liveConfiguration);
        delegate.setLiveConfiguration(liveConfiguration);
    }

    /**
     * This method is called from ExoPlayer internally to "override" the ideal target from the {@link MediaItem.LiveConfiguration} when
     * a seek occurs (which of course changes the position from the original starting live point ({@link SimpleExoPlayer#seekToDefaultPosition()})
     * In Google's ExoPlayer version the only way to restore the original is a {@link MediaItem.LiveConfiguration}, and to have the
     * bug fix for https://github.com/google/ExoPlayer/issues/11050
     * <p></p>
     * For our implementation this is not enough, as trick-play forward can exits short of live, and the desired action is to remove the
     * override just like if  ({@link SimpleExoPlayer#seekToDefaultPosition()}) was called, and to disable live offset adjustment completely
     * when we seek back from the live point significantly.   Net behavior is:
     * 1) we simply do not accept an override that is more than {@link #MAX_LIVE_OFFSET_DELTA_MS} ms greater than the the target
     *
     * @param liveOffsetUs override target live offset value from ExoPlayer internal seek.
     */
    @Override
    public void setTargetLiveOffsetOverrideUs(long liveOffsetUs) {
        updateEnableFromLiveOffsetOverride(liveOffsetUs);
        delegate.setTargetLiveOffsetOverrideUs(enableLiveAdjustment ? C.TIME_UNSET : liveOffsetUs);
    }

    /**
     * If we are not actively managing the Live Offset, no need to take note of
     * re-buffering.
     */
    @Override
    public void notifyRebuffer() {
        if (enableLiveAdjustment) {
            delegate.notifyRebuffer();
        }
    }

    /**
     * Only allow live adjustment if {@link #enableLiveAdjustment} is true
     *
     * @param liveOffsetUs The current live offset, in microseconds.
     * @param bufferedDurationUs The duration of media that's currently buffered, in microseconds.
     * @return 1.0f if adjustment disabled, else the delegate value
     */
    @Override
    public float getAdjustedPlaybackSpeed(long liveOffsetUs, long bufferedDurationUs) {
        return enableLiveAdjustment ? delegate.getAdjustedPlaybackSpeed(liveOffsetUs, bufferedDurationUs) : 1.0f;
    }

    /**
     * Override to return based on {@link #enableLiveAdjustment}
     *
     * @return delegate or C.TIME_UNSET if live offset adjustment is disabled.
     */
    @Override
    public long getTargetLiveOffsetUs() {
        return enableLiveAdjustment ? delegate.getTargetLiveOffsetUs() : C.TIME_UNSET;
    }


    private void updateEnableFromLiveOffsetOverride(long liveOffsetUs) {
        boolean previousEnable = enableLiveAdjustment;
        if (liveOffsetUs == C.TIME_UNSET) {
            enableLiveAdjustment = true;        // re-enable on reset.
            Log.d(TAG, "live offset override removed, enabling live edge adjustment");
        } else if (lastLiveConfiguration.targetOffsetMs != C.TIME_UNSET){
            enableLiveAdjustment = liveOffsetInRange(liveOffsetUs);
            if (previousEnable != enableLiveAdjustment) {
                if (previousEnable) {
                    Log.d(TAG, "live offset override " + liveOffsetUs + " out of live range, disabling adjustment");
                } else {
                    Log.d(TAG, "live offset override " + liveOffsetUs + " returned to live range, enabling adjustment");
                }
            }
        }
    }

    private boolean liveOffsetInRange(long liveOffsetUs) {
        return (C.usToMs(liveOffsetUs) - lastLiveConfiguration.targetOffsetMs) <= MAX_LIVE_OFFSET_DELTA_MS;
    }

    private void updateEnableFromLiveConfiguration(@NonNull MediaItem.LiveConfiguration liveConfiguration) {
        if (liveConfiguration.targetOffsetMs != C.TIME_UNSET) {
            if (! liveConfiguration.equals(lastLiveConfiguration)) {
                Log.d(TAG, "liveConfiguration changed: old targetOffset: " + lastLiveConfiguration.targetOffsetMs
                    + " new targetOffset: " + liveConfiguration.targetOffsetMs + " enabling live edge adjustment");
                enableLiveAdjustment = true;
                lastLiveConfiguration = liveConfiguration;
            }
        }
    }

}
