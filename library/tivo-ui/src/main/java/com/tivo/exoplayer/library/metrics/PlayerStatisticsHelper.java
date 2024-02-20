package com.tivo.exoplayer.library.metrics;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.util.Log;
import static java.lang.Math.max;

/**
 * Helper class to manage accumulation of metrics that are not part
 * of {@link com.google.android.exoplayer2.analytics.PlaybackStats} but instead managed by the
 * player core, for example {@link DecoderCounters}.
 */
public class PlayerStatisticsHelper implements AnalyticsListener {

    private static final String TAG = "PlayerStatisticsHelper";

    /** DecoderCounters values for any period not in trickplay */
    private final DecoderCounters cumulativeVideoDecoderCounters = new DecoderCounters();

    /** Tracking if we are in trickplay or not*/
    private boolean inTrickPlay = false;

    /** DecoderCounters values during trickplay, reset at the end of each trickplay */
    @Nullable private DecoderCounters countersDuringCurrentTrickPlay;

    public PlayerStatisticsHelper() {}

    // Implement AnalyticsListener

    /**
     * Called with the current value of the {@link DecoderCounters}, before a subsequent
     * enable would reset them.   Merge the values into the active set of DecoderCounters, which is
     * either the trickplay values or regular playback values.
     *
     * @param eventTime The event time.
     * @param decoderCounters {@link DecoderCounters} that were updated by the renderer.
     */
    @Override
    public void onVideoDisabled(EventTime eventTime, DecoderCounters decoderCounters) {
        mergeCounterValues(decoderCounters);
    }

    // Internal API

    /**
     * Merge in the current{@link DecoderCounters} values into our running set.
     *
     * <p>This method is called before the video decoder creates a fresh {@link DecoderCounters} object,
     * this happens during decoder disable()/enable().</p>
     *
     * @param current The current {@link DecoderCounters} for video
     */
    void mergeCounterValues(DecoderCounters current) {
        cumulativeVideoDecoderCounters.merge(current);
        Log.d(TAG, "mergeCounterValues, videoFramesRendered: " + cumulativeVideoDecoderCounters.renderedOutputBufferCount +
            " after capturing snapshot renderedOutputBufferCount: " + current.renderedOutputBufferCount );
    }

    /**
     * Update the video frames rendered while playing video, excluding the ones encountered during trickplay
     * This is called each time the {@link PlaybackMetricsManagerApi#updateFromCurrentStats(PlaybackMetrics)}
     * API method is called to fill in values from {@link com.google.android.exoplayer2.analytics.PlaybackStats}
     *
     * @param current The current {@link DecoderCounters} for video
     * @return The number of video frames rendered snapshot
     */
    int getVideoFramesRendered(DecoderCounters current) {
        DecoderCounters countersSnapShot = new DecoderCounters();
        countersSnapShot.merge(cumulativeVideoDecoderCounters);

        if (!inTrickPlay){
            countersSnapShot.merge(current);
        }

        Log.d(TAG, "getVideoFramesRendered, videoFramesRendered: " + countersSnapShot.renderedOutputBufferCount +
            " Captured snapshot renderedOutputBufferCount: " + cumulativeVideoDecoderCounters.renderedOutputBufferCount +
            " and current renderedOutputBufferCount: " + current.renderedOutputBufferCount);
        return countersSnapShot.renderedOutputBufferCount;
    }

    /**
     * This method takes a snapsnhot of the counters at the start of trick-play,
     * that value is simply {@link #cumulativeVideoDecoderCounters} + `current`
     * then saves this counter snapshot away for {@link #exitTrickPlay(DecoderCounters)}
     *
     * @param current value of {@link DecoderCounters} at the time of this call.
     */
    void enterTrickPlay(DecoderCounters current) {
        inTrickPlay = true;
        countersDuringCurrentTrickPlay = new DecoderCounters();
        //countersDuringCurrentTrickPlay.merge(current);
    }

    /**
     * On exit from trickplay, reduces the value of the {@link #cumulativeVideoDecoderCounters} by the
     * change in the {@link DecoderCounters} that occurred as the during the last trickplay session
     *
     * @param current The current {@link DecoderCounters} snapshot at the stop of trickplay
     */
    public void exitTrickPlay(DecoderCounters current) {
        inTrickPlay = false;
        assert countersDuringCurrentTrickPlay != null;
        countersDuringCurrentTrickPlay.merge(invertDecoderCounters(current));
        cumulativeVideoDecoderCounters.merge(countersDuringCurrentTrickPlay);
        Log.d(TAG, "exitTrickPlay, Captured snapshot renderedOutputBufferCount: " + cumulativeVideoDecoderCounters.renderedOutputBufferCount +
            " and current renderedOutputBufferCount: " + current.renderedOutputBufferCount);
        countersDuringCurrentTrickPlay = null;     // no longer in a trickplay session.
    }

    /**
     * Simply returns the inverse of the source {@link DecoderCounters}.  Note
     * {@link DecoderCounters#maxConsecutiveDroppedBufferCount} is not altered, this value does not lend itself
     * TODO figure out how to deal with non-monotonic increasing values (like maxConsecutiveDroppedBufferCount)
     *
     * <p>This can be used with {@link DecoderCounters#merge(DecoderCounters)} to produce subtraction
     * </p>
     * @param source counters to invert,  source is not altered.
     * @return the inverse of the source
     */
    @VisibleForTesting
    static DecoderCounters invertDecoderCounters(DecoderCounters source) {
        DecoderCounters inverted = new DecoderCounters();
        inverted.decoderInitCount -= source.decoderInitCount;
        inverted.decoderReleaseCount -= source.decoderReleaseCount;
        inverted.inputBufferCount -= source.inputBufferCount;
        inverted.skippedInputBufferCount -= source.skippedInputBufferCount;
        inverted.renderedOutputBufferCount -= source.renderedOutputBufferCount;
        inverted.skippedOutputBufferCount -= source.skippedOutputBufferCount;
        inverted.droppedBufferCount -= source.droppedBufferCount;
        inverted.droppedToKeyframeCount -= source.droppedToKeyframeCount;
        inverted.totalVideoFrameProcessingOffsetUs -= source.totalVideoFrameProcessingOffsetUs;
        inverted.videoFrameProcessingOffsetCount -= source.videoFrameProcessingOffsetCount;
        Log.d(TAG, "invertDecoderCounters, videoFramesRendered: " + inverted.renderedOutputBufferCount);
        return inverted;
    }
}