package com.tivo.exoplayer.library.metrics;

import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.util.Clock;

/**
 * Produces the required metrics from the TiVo OI Dashboard.  The PlaybackSessionManager expect an instance (or
 * subclass) of this class for each call to
 */
public class PlaybackMetrics {
    private long avgVideoBitrate;
    private long startingTimestamp;


    /**
     * Called by the {@link ManagePlaybackMetrics} to initialize the time of creation to allow scanning the
     * relevant events from the {@link PlaybackStats}
     *
     * @param currentElapsedTime - long time of creation (from {@link Clock#elapsedRealtime()}
     */
    void initialize(long currentElapsedTime) {
        startingTimestamp = currentElapsedTime;
    }

    /**
     * Updates the metrics values from the PlaybackStats since the last initialize call. After this call
     * this set of PlaybackMetrics will cover the range from the last call to this method or {@link #initialize(long)}
     *
     * @param playbackStats used to compute the metric values
     * @param currentElapsedTime current {@link Clock#elapsedRealtime()}
     */
    void updateValuesFromStats(PlaybackStats playbackStats, long currentElapsedTime) {
        // TODO compute values
        startingTimestamp = currentElapsedTime;
    }

    /**
     *
     * @return video bitrate, in mbps, since this PlaybackMetrics was created
     */
    public long getAvgVideoBitrate() {
        return avgVideoBitrate;
    }
}
