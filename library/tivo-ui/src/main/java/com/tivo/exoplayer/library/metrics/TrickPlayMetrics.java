package com.tivo.exoplayer.library.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.util.Log;

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
    private static final String TAG = "TrickPlayMetrics";

    private long totalRenderedFrames;
    private final TrickPlayControl.TrickMode currentMode;
    private final TrickPlayControl.TrickMode prevMode;
    private float observedPlaybackSpeed;
    private float expectedPlaybackSpeed;
    private long totalElapsedTimeMs;
    private long totalSeekTimeMs;
    private int totalSeekCount;
    private final List<IframeLoadEvent> loadEventList;
    private int totalCanceledLoads;
    private float arithmeticMeanFrameLoadTime;
    private long medianFrameLoadTime;

    static class IframeLoadEvent {
        private final long elapsedRealTimeMs;
        private final long startMediaTimeMs;
        private final long mediaEndTimeMs;
        private final long loadDurationMs;
        private final long bytesLoaded;

        IframeLoadEvent(long elapsedRealTimeMs, MediaSourceEventListener.LoadEventInfo loadEvent, MediaSourceEventListener.MediaLoadData loadData) {
            this.elapsedRealTimeMs = elapsedRealTimeMs;
            startMediaTimeMs = loadData.mediaStartTimeMs;
            mediaEndTimeMs = loadData.mediaEndTimeMs;
            loadDurationMs = loadEvent.loadDurationMs;
            bytesLoaded = loadEvent.bytesLoaded;
        }

        float getLoadEventBps() {
            return (bytesLoaded * 8_000.0f) / loadDurationMs;
        }

        boolean isInPlayedRange(long firstMediaTimeMs, long lastMediaTime) {
            return startMediaTimeMs >= firstMediaTimeMs && mediaEndTimeMs <= lastMediaTime;
        }
    }

    public TrickPlayMetrics(TrickPlayControl.TrickMode currentMode, TrickPlayControl.TrickMode prevMode) {
        this.currentMode = currentMode;
        this.prevMode = prevMode;
        loadEventList = new ArrayList<>();
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

    public void incrLoadCancels() {
        totalCanceledLoads++;
    }


    void setExpectedPlaybackSpeed(float speed) {
        expectedPlaybackSpeed = speed;
    }

    void recordLoadedIframeInfo(IframeLoadEvent iframeLoadEvent) {
        loadEventList.add(iframeLoadEvent);
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
     * Total wall clock time elapsed in {@link #getCurrentMode()} for the trick-play session.
     *
     * @return time in milli-seconds
     */
    public long getTotalElapsedTimeMs() {
        return totalElapsedTimeMs;
    }

    /**
     * Total time spent on "seek" operations.  Reverse VTP and non-visual trick-play both use repeated seek
     * operations followed by waiting for a short amount of time for a rendered frame.
     *
     * @return time in milli-seconds
     */
    public long getTotalSeekTimeMs() {
        return totalSeekTimeMs;
    }

    /**
     * Total number of "seek" operations issued.  Seeks move reverse VTP in the reverse direction, a
     * seek is productive if it results in a frame render.  So the ratio of {@link #getRenderedFramesCount()} /
     * {@link #getTotalSeekCount()} is the productivity of the "visual" aspect of reverse VTP.
     *
     * For forward VTP this number is only non-zero if the advance key is used to jump.
     *
     * @return number of seek operations
     */
    public int getTotalSeekCount() {
        return totalSeekCount;
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
     * Return the average amount of time it took to download an trick play frame.  This includes
     * frames that are possibly never rendered (as there is buffering in the forward direction)
     *
     * The Mean can be misleading, if there are lots of outliers, seeing how close this value is
     * to the Median and the Mode.
     *
     * @return average time in MS
     */
    public float getArithmeticMeanFrameLoadTime() {
        return arithmeticMeanFrameLoadTime;
    }

    /**
     * Return the median value in the set of frame load times.
     *
     * @return median load time in MS
     */
    public long getMedianFrameLoadTime() {
        return medianFrameLoadTime;
    }

    /**
     * Canceled loads are caused by seeks after loading has started.  These are wasted loads.
     *
     * @return count of load cancels.
     */
    public int getTotalCanceledLoads() {
        return totalCanceledLoads;
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
        trickplayMetrics.put("totalElapsedTime", getTotalElapsedTimeMs());
        trickplayMetrics.put("totalSeekTimeMs", getTotalSeekTimeMs());
        trickplayMetrics.put("totalSeekCount", getTotalSeekCount());
        trickplayMetrics.put("arithmeticMeanFrameLoadTime", getArithmeticMeanFrameLoadTime());
        trickplayMetrics.put("medianFrameLoadTime", getMedianFrameLoadTime());
        trickplayMetrics.put("totalCanceledLoadCount", getTotalCanceledLoads());
        trickplayMetrics.put("renderedFramesCount", getRenderedFramesCount());

        return trickplayMetrics;
    }

    void updateOnSessionEnd(PlaybackStats playbackStats, AnalyticsListener.EventTime startEventTime, AnalyticsListener.EventTime endEventTime) {
        super.updateValuesFromStats(playbackStats, startEventTime.realtimeMs);
        totalElapsedTimeMs = playbackStats.getTotalElapsedTimeMs();
        totalSeekTimeMs = playbackStats.getTotalSeekTimeMs();
        totalSeekCount = playbackStats.totalSeekCount;

        long positionDeltaMs = endEventTime.currentPlaybackPositionMs - startEventTime.currentPlaybackPositionMs;
        observedPlaybackSpeed = (float) positionDeltaMs / (float) totalElapsedTimeMs;

        Collections.sort(loadEventList, (o1, o2) -> (int) (o1.loadDurationMs - o2.loadDurationMs));
        int middle = (loadEventList.size() + 1) / 2;
        medianFrameLoadTime = loadEventList.size() > 0 ? loadEventList.get(middle).loadDurationMs : C.TIME_UNSET;


        float totalTime = 0.0f;
        for (IframeLoadEvent event : loadEventList) {
            totalTime += event.loadDurationMs;
//            Log.d(TAG, String.valueOf(event.loadDurationMs));
        }

        arithmeticMeanFrameLoadTime =  loadEventList.size() > 0 ? totalTime / loadEventList.size() : Float.MAX_VALUE;
    }
}
