package com.tivo.exoplayer.library.metrics;

import androidx.annotation.Nullable;

import java.util.Map;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.util.Clock;

/**
 * Produces the required metrics from the TiVo OI Dashboard.  The PlaybackSessionManager expect an instance (or
 * subclass) of this class for each call to
 */
public class PlaybackMetrics {
    private long startingTimestamp;
    private @Nullable PlaybackMetrics previousMetrics;
    private int profileShiftCount;
    private Map<Format, Long> timeInVideoFormat;
    private long droppedFramesCount;
    private float avgVideoBitrate;
    private float avgNetworkBitrateMbps;


    /**
     * Called by the {@link ManagePlaybackMetrics} to initialize a new set of PlaybackMetrics.
     *
     * This call is used to manage metric values that must be "reset" on each event, so with this call,
     * these per-event values can be snapshot and just the deltas reported.
     *
     * @param currentElapsedTime - long time of creation (from {@link Clock#elapsedRealtime()}
     * @param previousMetrics - the previous active metrics, or null if none
     */
    void initialize(long currentElapsedTime, @Nullable PlaybackMetrics previousMetrics) {
        startingTimestamp = currentElapsedTime;
        this.previousMetrics = previousMetrics;
    }

    /**
     * Updates the metrics values from the PlaybackStats since the last initialize call. After this call
     * this set of PlaybackMetrics will cover the range from the last call to this method or {@link #initialize(long, PlaybackMetrics)}
     *
     * @param playbackStats used to compute the metric values
     * @param currentElapsedTime current {@link Clock#elapsedRealtime()}
     */
    void updateValuesFromStats(PlaybackStats playbackStats, long currentElapsedTime) {
        // TODO compute values
        startingTimestamp = currentElapsedTime;

        timeInVideoFormat = ManagePlaybackMetrics.getTimeInFormat(playbackStats, currentElapsedTime);
        avgVideoBitrate = bpsToMbps(playbackStats.getMeanVideoFormatBitrate());
        avgNetworkBitrateMbps = bpsToMbps(playbackStats.getMeanBandwidth());
        profileShiftCount = playbackStats.videoFormatHistory.size();
        droppedFramesCount = playbackStats.totalDroppedFrames;
    }

    private float bpsToMbps(int meanVideoFormatBitrate) {
        return (float) (meanVideoFormatBitrate / (1024.0 * 1024.0));
    }

    /**
     * Number of number of decoded video frames dropped.  Frames are dropped because they
     * are decoded to late (more than 30ms past the current render time) for their time to render.
     *
     * @return count of dropped frames since the session began
     */
    public long getDroppedFramesCount() {
        return droppedFramesCount;
    }

    /**
     * The Average Video bitrate is the computed from the {@link Format#bitrate}, which is the AVERAGE_BANDWIDTH for
     * an HLS variant where available, or BANDWIDTH (the peak).  This is more sustinctly the varaints bitrate (as it
     * includes all playable renditions (audio, video and captions).
     *
     * The average computation is a weighted average, taking the time the format was used for playback (that is
     * eliminating paused and buffering times) into account.
     *
     * @return average video bitrate, in mbps, since the session began
     */
    public float getAvgVideoBitrate() {
        return avgVideoBitrate;
    }

    public float getAvgNetworkBandwidth() {
        return avgNetworkBitrateMbps;
    }

    /**
     * Returns the number of times the current playing video format changed.
     *
     * @return number of video profile changes since the session began
     */
    public int getProfileShiftCount() {
        return profileShiftCount;
    }

    /**
     * Return a Map with the total time spent in each of the {@link Format}'s
     * TODO - move this to ExoPlayer's core playback stats and make it only playing time
     *
     * @return Map of video {@link Format} objects with the total time (including not playing) in each
     */
    public Map<Format, Long> getTimeInVideoFormat() {
        return timeInVideoFormat;
    }
}
