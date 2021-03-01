package com.tivo.exoplayer.library.metrics;

import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;

/**
 * PlaybackMetrics accumulated during a VTP session.  A VTP session is defined as a period of
 * playback time under a single mode/speed ({@link TrickPlayControl.TrickMode} of VTP.
 *
 * During VTP, regular PlaybackMetrics are suspended and each VTP operation accumulates counters
 * in this object.
 *
 * The metrics measure quantitative performance against the main goals of Trick-Play, which are:
 * <ol>
 *     <li>Progress media time forward at the indicated speed (e.g. at 30x 30 seconds of media should take 1 second)</li>
 *     <li>Render some acceptable set of video frames per-second</li>
 * </ol>
 *
 *
 */
public class TrickPlayMetrics extends PlaybackMetrics {

    private long totalRenderedFrames;
    private final TrickPlayControl.TrickMode currentMode;
    private final TrickPlayControl.TrickMode prevMode;
    private float observedPlaybackSpeed;

    public TrickPlayMetrics(TrickPlayControl.TrickMode currentMode, TrickPlayControl.TrickMode prevMode) {
        this.currentMode = currentMode;
        this.prevMode = prevMode;
    }

    /**
     * Increment the total frames rendered over the life of this metrics set.
     *
     * TODO - based on the render time, we can accumulate stats on the distribution of frame renders (stddev, variance, etc)
     * @param renderTimeUs - time the frame was rendered in microseconds
     */
    void incrRenderedFrames(long renderTimeUs) {
        totalRenderedFrames++;
    }

    /**
     * Total frames rendered during trick-play
     *
     * @return count of frames rendered during trickplay
     */
    public long getRenderedFramesCount() {
        return totalRenderedFrames;
    }

    /**
     * Actual playback rate during trick-play.  This should be nearly equal to the indicated playback
     * speed for the {@link TrickPlayControl.TrickMode} that is the currentMode.
     *
     * If it is less then re-buffering slowed the playback down, this can be validated with {@link #getRebufferCount()}
     *
     * @return floating value of the average observed speed across the whole trickplay event
     */
    public float getObservedPlaybackSpeed() {
        return observedPlaybackSpeed;
    }

    void updateOnSessionEnd(PlaybackStats playbackStats, AnalyticsListener.EventTime startEventTime, AnalyticsListener.EventTime endEventTime) {
        super.updateValuesFromStats(playbackStats, startEventTime.realtimeMs);

        long positionDeltaMs = Math.abs(endEventTime.currentPlaybackPositionMs - startEventTime.currentPlaybackPositionMs);
        long timeDeltaMs = endEventTime.realtimeMs - startEventTime.realtimeMs;
        observedPlaybackSpeed = (float) positionDeltaMs / (float) timeDeltaMs;
    }
}
