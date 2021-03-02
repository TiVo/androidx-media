package com.tivo.exoplayer.library.metrics;

import java.util.Map;

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
    private float expectedPlaybackSpeed;

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

    void setExpectedPlaybackSpeed(float speed) {
        expectedPlaybackSpeed = speed;
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

    /**
     * Expected (target) playback rate for this trick-play session.  Note this is only really meaninful
     * for single TrickMode session.  TODO - if we want multi-mode will need more complicate weighted average
     *
     * @return float expected speed for the current mode ({@link #currentMode} )
     */
    public float getExpectedPlaybackSpeed() {
        return expectedPlaybackSpeed;
    }

    /**
     * The trick-play mode this set of metrics cover.
     *
     * @return the current mode for this set of metrics
     */
    public TrickPlayControl.TrickMode getCurrentMode() {
        return currentMode;
    }

    /**
     * The trickplay mode active (or NORMAL if none) just before this set of metrics
     *
     * @return previous trick play mode.
     */
    public TrickPlayControl.TrickMode getPrevMode() {
        return prevMode;
    }

    /**
     * True if these metrics are inside of a larger trick-play sequence (ie change from FF1 to FF2)
     * @return
     */
    public boolean isIntraTrickPlayChange() {
        return TrickPlayControl.directionForMode(prevMode) != TrickPlayControl.TrickPlayDirection.NONE && TrickPlayControl.directionForMode(currentMode) != TrickPlayControl.TrickPlayDirection.NONE;
    }
    /**
     * Extend to add TrickPlay specific metrics.
     *
     * @return
     */
    @Override
    public Map<String, Object> getMetricsAsMap() {
        Map<String, Object> trickplayMetrics = super.getMetricsAsMap();
        trickplayMetrics.put("prevMode", prevMode.toString());
        trickplayMetrics.put("currentMode", currentMode.toString());
        trickplayMetrics.put("expectedTrickPlaySpeed", getExpectedPlaybackSpeed());
        trickplayMetrics.put("observedTrickPlaySpeed", getObservedPlaybackSpeed());
        trickplayMetrics.put("renderedFramesCount", getRenderedFramesCount());

        return trickplayMetrics;
    }

    void updateOnSessionEnd(PlaybackStats playbackStats, AnalyticsListener.EventTime startEventTime, AnalyticsListener.EventTime endEventTime) {
        super.updateValuesFromStats(playbackStats, startEventTime.realtimeMs);

        long positionDeltaMs = endEventTime.currentPlaybackPositionMs - startEventTime.currentPlaybackPositionMs;
        long timeDeltaMs = endEventTime.realtimeMs - startEventTime.realtimeMs;
        observedPlaybackSpeed = (float) positionDeltaMs / (float) timeDeltaMs;
    }
}
