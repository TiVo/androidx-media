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
    private long totalPlaybackTime;
    private long avgRebufferTime;
    private float rebufferCount;


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

        timeInVideoFormat = PlaybackStatsExtension.getPlayingTimeInFormat(playbackStats, currentElapsedTime);
        avgVideoBitrate = bpsToMbps(playbackStats.getMeanVideoFormatBitrate());
        avgNetworkBitrateMbps = bpsToMbps(playbackStats.getMeanBandwidth());
        profileShiftCount = playbackStats.videoFormatHistory.size();
        droppedFramesCount = playbackStats.totalDroppedFrames;
        totalPlaybackTime = playbackStats.getTotalPlayTimeMs();
        avgRebufferTime = playbackStats.getMeanRebufferTimeMs();
        rebufferCount = playbackStats.getMeanRebufferCount();
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

    /**
     * Returns the average (mean) network bandwidth based on from measurements of media transfers for all
     * the media that comprises the current playing streams.   This values is smoothed by the player's
     * bandwidth meter to use in ABR selection of the streams.
     *
     * This tracks {@link #getAvgVideoBitrate()} as when this values increases, the ABR should select a
     * higher variant bitrate
     *
     * @return network bandwidth observed over the session in Mbps
     */
    public float getAvgNetworkBandwidth() {
        return avgNetworkBitrateMbps;
    }

    /**
     * Number of times playback paused for re-buffering (buffered media duration less than threshold).  Note this
     * excludes buffering for:
     *
     * <ol>
     *     <li>Initial playback startup</li>
     *     <li>Restarting playback after a seek</li>
     *     <li>Buffering during trickplay</li>
     * </ol>
     *
     * @return count of rebufferings
     */
    public float getRebufferCount() {
        return rebufferCount;
    }

    /**
     * Ratio of playback time to time rebuffering.
     *
     * @return avg rebuffering time
     */
    public long getAvgRebufferTime() {
        return avgRebufferTime;
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

    /**
     * Get the total playback time since the start of the session.  Playback time is time actually playing,
     * that is not counting paused, buffering, trick-play, etc.
     *
     * @return time playing in milliseconds
     */
    public long getTotalPlaybackTimeMs() {
        return totalPlaybackTime;
    }
}
