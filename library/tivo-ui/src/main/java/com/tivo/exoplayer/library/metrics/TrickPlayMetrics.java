package com.tivo.exoplayer.library.metrics;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.decoder.DecoderCounters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
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
public class TrickPlayMetrics extends AbstractBasePlaybackMetrics {
    private static final String TAG = "TrickPlayMetrics";

    private long totalRenderedFrames;
    private final TrickPlayControl.TrickMode currentMode;
    private final TrickPlayControl.TrickMode prevMode;
    private float observedPlaybackSpeed;
    private float expectedPlaybackSpeed;
    private final List<IframeLoadEvent> loadEventList;
    private int totalCanceledLoads;
    private float arithmeticMeanFrameLoadTime;
    private long medianFrameLoadTime;
    private Format lastPlayedFormat;
    private long firstFrameRender = C.TIME_UNSET;
    private float avgFramesPerSecond;
    private long initialTrickPlayStartDelay;

    /**
     * Reason for trick play ending, either USER_ENDED or an ERROR are the only possible reasons
     *
     * @return reason trick play ended
     */
    public PlaybackMetrics.EndReason getEndReason() {
        return getEndedWithError() != null ? PlaybackMetrics.EndReason.ERROR : PlaybackMetrics.EndReason.USER_ENDED;
    }

    static class IframeLoadEvent {
        private final long elapsedRealTimeMs;
        private final long startMediaTimeMs;
        private final long mediaEndTimeMs;
        private final long loadDurationMs;
        private final long bytesLoaded;

        IframeLoadEvent(long elapsedRealTimeMs, LoadEventInfo loadEvent, MediaLoadData loadData) {
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
     * For trick-play, this value is only really meaningful if the transition is NORMAL to a
     * trick mode, that is not an {@link #isIntraTrickPlayModeChange()}.  For intra-trick mode
     * the time is 0
     *
     * In reverse mode, we count this as time till the first rendered frame, in forward the
     * super class version (time from initial buffering to playback start), for scrub mode 
     * the value is essentially meaningless so returns C.TIME_UNSET
     *
     * @return time to first frame for initial reverse, or time for first buffering for initial forward
     */
    @Override
    public long getInitialPlaybackStartDelay() {
        return initialTrickPlayStartDelay;
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
     * Average (mean) rendered frames per-second.  Computed simply as rendered
     * frames / total time.
     *
     * @return average number of rendered frames per-second
     */
    public float getAvgFramesPerSecond() {
        return avgFramesPerSecond;
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
     *
     * @return true if the change is from inside of larger trickplay sequence
     */
    public boolean isIntraTrickPlayModeChange() {
        return TrickPlayControl.directionForMode(prevMode) != TrickPlayControl.TrickPlayDirection.NONE && TrickPlayControl.directionForMode(currentMode) != TrickPlayControl.TrickPlayDirection.NONE;
    }

    /**
     * Extend to add TrickPlay specific metrics.
     *
     * @return
     */
    @Override
    public Map<String, Object> getMetricsAsMap() {
        Map<String, Object> trickplayMetrics = pruneForMode(super.getMetricsAsMap());
        trickplayMetrics.put("prevMode", prevMode.toString());
        trickplayMetrics.put("currentMode", currentMode.toString());
        trickplayMetrics.put("expectedTrickPlaySpeed", getExpectedPlaybackSpeed());
        trickplayMetrics.put("observedTrickPlaySpeed", getObservedPlaybackSpeed());
        trickplayMetrics.put("arithmeticMeanFrameLoadTime", getArithmeticMeanFrameLoadTime());
        trickplayMetrics.put("medianFrameLoadTime", getMedianFrameLoadTime());
        trickplayMetrics.put("avgFramesPerSecond", getAvgFramesPerSecond());
        trickplayMetrics.put("totalCanceledLoadCount", getTotalCanceledLoads());
        trickplayMetrics.put("renderedFramesCount", getRenderedFramesCount());
        return trickplayMetrics;
    }

    // Package private

    /**
     * Increment the total frames rendered over the life of this metrics set.
     *
     * TODO - based on the render time, we can accumulate stats on the distribution of frame renders (stddev, variance, etc)
     * @param renderPositionUs - media-time (Player.getCurrentPosition) of the rendered frame
     * @param renderTimeMs - time the frame was rendered ({@link android.os.SystemClock#elapsedRealtime()})
     */
    void incrRenderedFrames(long renderPositionUs, long renderTimeMs) {
        if (firstFrameRender == C.TIME_UNSET) {
            firstFrameRender = renderTimeMs;
        }
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
     * Used to connect consecutive TrickPlayMetrics sessions, the Format from the previous
     * trickplay carries forward as starting format for next
     *
     * @return Format - last played format in the session or null if none
     */
    @Nullable
    Format lastPlayedFormat() {
        return lastPlayedFormat;
    }

    // Private

    /**
     * Prune to the trick-play mode.  For example, the "trickPlayCount" is pointless (it is 0, we are in
     * trick-play)
     *
     * @param metricsAsMap original metrics from {@link PlaybackMetrics}
     * @return metrics, pruned to remove ones that no sense for the current trick-play mode
     */
    private Map<String, Object> pruneForMode(Map<String, Object> metricsAsMap) {
        metricsAsMap.remove("trickPlayCount");
        metricsAsMap.remove("totalTrickPlayTimeMs");
        return metricsAsMap;
    }

    /**
     * On session end calculate the value for {@link #getInitialPlaybackStartDelay()}
     *
     * For reverse we just use time to first frame, for forward we use the initial buffering
     * event time.  Since the {@link com.google.android.exoplayer2.analytics.PlaybackStatsListener} for
     * trickplay essentially starts in PLAYBACK_STATE_PLAYING the super.getInitialPlaybackStartDelay() is not valid as
     * a startup delay. So we use the time from the initial PLAYBACK_STATE_BUFFERING (will follow from the track selection)
     * to the next non-buffering state (PLAYBACK_STATE_PLAYING) as initial startup delay
     *
     * Value is always 0 on intra-trick play mode transitions, any rebuffering is still recorded as {@link #getTotalRebufferingTime()}
     * and as slower then expected playback speed realized (for forward)
     *
     * @param playbackStats - stats to examine for state events
     * @param endEventTime - time trickplay ended.
     * @return value to set.
     */
    private long calculateTrickPlayStartDelay(PlaybackStats playbackStats, AnalyticsListener.EventTime endEventTime) {
        long startDelay = C.TIME_UNSET;
        if (isIntraTrickPlayModeChange()) {
            startDelay = 0;
        } else {
            switch (TrickPlayControl.directionForMode(currentMode)) {
                case FORWARD:
                case NONE:
                    startDelay = super.getInitialPlaybackStartDelay();
                    List<PlaybackStats.EventTimeAndPlaybackState> stateHistory = playbackStats.playbackStateHistory;
                    if (startDelay == 0 || startDelay == C.TIME_UNSET) {
                        PlaybackStats.EventTimeAndPlaybackState firstBuffering = null;
                        Iterator<PlaybackStats.EventTimeAndPlaybackState> it = stateHistory.iterator();
                        while (it.hasNext() && firstBuffering == null) {
                            PlaybackStats.EventTimeAndPlaybackState timeAndState =  it.next();
                            if (timeAndState.playbackState == PlaybackStats.PLAYBACK_STATE_BUFFERING) {
                                firstBuffering = timeAndState;
                            }
                        }
                        if (firstBuffering != null) {
                            startDelay = (it.hasNext() ? it.next().eventTime.realtimeMs : endEventTime.realtimeMs) - firstBuffering.eventTime.realtimeMs;
                        }
                    }
                    break;

                case SCRUB:
                    break;

                case REVERSE:
                    startDelay = firstFrameRender == C.TIME_UNSET ? C.TIME_UNSET :
                        firstFrameRender - startingTimestamp;
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + TrickPlayControl.directionForMode(currentMode));
            }

        }
        return startDelay;
    }

    void updateOnSessionEnd(PlaybackStats playbackStats, AnalyticsListener.EventTime startEventTime, AnalyticsListener.EventTime endEventTime) {
        super.updateValuesFromStats(playbackStats, startEventTime.realtimeMs, null, new DecoderCounters());

        lastPlayedFormat = playbackStats.videoFormatHistory.size() > 0
                ? playbackStats.videoFormatHistory.get(playbackStats.videoFormatHistory.size() - 1).format : null;
        initialTrickPlayStartDelay = calculateTrickPlayStartDelay(playbackStats, endEventTime);

        long positionDeltaMs = endEventTime.currentPlaybackPositionMs - startEventTime.currentPlaybackPositionMs;
        observedPlaybackSpeed = (float) positionDeltaMs / (float) getTotalElapsedTimeMs();

        Collections.sort(loadEventList, (o1, o2) -> (int) (o1.loadDurationMs - o2.loadDurationMs));
        int middle = loadEventList.size() / 2;
        medianFrameLoadTime = loadEventList.size() > 0 ? loadEventList.get(middle).loadDurationMs : C.TIME_UNSET;


        float totalTime = 0.0f;
        for (IframeLoadEvent event : loadEventList) {
            totalTime += event.loadDurationMs;
//            Log.d(TAG, String.valueOf(event.loadDurationMs));
        }

        arithmeticMeanFrameLoadTime =  loadEventList.size() > 0 ? totalTime / loadEventList.size() : Float.MAX_VALUE;

        avgFramesPerSecond = ((float)totalRenderedFrames * 1000.0f) / (float)getTotalElapsedTimeMs();
    }
}
