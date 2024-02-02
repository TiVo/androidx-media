package com.tivo.exoplayer.library.metrics;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.decoder.DecoderCounters;
import java.util.HashMap;
import java.util.Map;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import static com.google.android.exoplayer2.analytics.PlaybackStats.PLAYBACK_STATE_ABANDONED;
import static com.google.android.exoplayer2.analytics.PlaybackStats.PLAYBACK_STATE_ENDED;
import static com.google.android.exoplayer2.analytics.PlaybackStats.PLAYBACK_STATE_STOPPED;

/**
 * Produces the required metrics from the TiVo OI Dashboard.  The PlaybackSessionManager expect an instance (or
 * subclass) of this class for each call to
 */
public class PlaybackMetrics extends AbstractBasePlaybackMetrics {

    private long totalPlaybackTime;
    private long totalTrickPlayTime;
    private int trickPlayCount;
    private EndReason endReason = EndReason.NONE;
    private Map<Format, Long> timeInAudioOnlyFormat;
    private int videoFramesRendered;

    // Internal methods

    void addTrickPlayTime(long trickPlayTime) {
        trickPlayCount++;
        totalTrickPlayTime += trickPlayTime;
    }


    /**
     * Get the set of metrics tracked in this object in a Map, suitable for serializing to JSON
     * and logging or passing to an API
     *
     * @return Metrics with string keys and numeric or collection values.
     */
    @Override
    public Map<String, Object> getMetricsAsMap() {
        Map<String, Object> loggedStats = new HashMap<>(super.getMetricsAsMap());
        loggedStats.put("totalPlayingTimeMs", getTotalPlaybackTimeMs());
        loggedStats.put("videoFramesPresented", getVideoFramesPresented());
        loggedStats.put("totalRebufferingTimeMs", getTotalRebufferingTime());
        loggedStats.put("totalTrickPlayTimeMs", getTotalTrickPlayTime());
        loggedStats.put("totalElapsedTimeMs", getTotalElapsedTimeMs());
        loggedStats.put("endedFor", getEndReason().toString());

        return loggedStats;
    }

    @Override
    int updateValuesFromStats(PlaybackStats playbackStats, long currentElapsedTime, DecoderCounters currentCounters) {
        int playbackStateAtTime = super.updateValuesFromStats(playbackStats, currentElapsedTime, new DecoderCounters());
        totalPlaybackTime = playbackStats.getTotalPlayTimeMs();
        timeInAudioOnlyFormat= PlaybackStatsExtension.getPlayingTimeInAudioOnlyFormat(playbackStats,currentElapsedTime);

        DecoderCounters countersSnapShot = new DecoderCounters();
        countersSnapShot.merge(cumulativeVideoDecoderCounters);
        countersSnapShot.merge(currentCounters);
        videoFramesRendered = countersSnapShot.renderedOutputBufferCount;


        if (getEndedWithError() != null) {
            endReason = PlaybackMetrics.EndReason.ERROR;
        } else {
            switch (playbackStateAtTime) {
                case PLAYBACK_STATE_ABANDONED:
                case PLAYBACK_STATE_STOPPED:
                    endReason = PlaybackMetrics.EndReason.USER_ENDED;
                    break;

                case PLAYBACK_STATE_ENDED:
                    endReason = PlaybackMetrics.EndReason.END_OF_CONTENT;
                    break;

                default:
                    endReason = PlaybackMetrics.EndReason.NONE;     // Default to not ended
                    break;
            }
        }
        return playbackStateAtTime;
    }

    /**
     * Return a Map with the total time spent in each of the {@link Format}'s.  Note, the base for this in
     * {@link PlaybackStats} allows for null formats (periods when no audio only  is playing, contain a null format)
     * Note this is used for Audio Only playback, which at this time should only
     * have a single format for the entire playback session.
     *
     * @return Map of audio only {@link Format} objects with the total time (including not playing) in each, null if no stats
     * captured
     */
    public @Nullable
    Map<Format, Long> getTimeInAudioOnlyFormat() {
      return timeInAudioOnlyFormat;
    }

    /**
     * total time spent in trick-play mode since the session began, in milliseconds
     *
     * @return total time in milliseconds
     */
    public long getTotalTrickPlayTime() {
        return totalTrickPlayTime;
    }

    /**
     * Get count of trick-play sessions.  A session includes point where starting any trickplay mode
     * until returning to normal playback, that is multiple speed changes within a trickplay session are not
     * counted.
     *
     * @return number of transitions from trickplay to non-trickplay mode since session start.
     */
    public int getTrickPlayCount() {
        return trickPlayCount;
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

    /**
     * Get the total number of video frames presented since the last call to
     * {@link #updateValuesFromStats(PlaybackStats, long, DecoderCounters)}
     *
     * Note this does not include frames presented during trickplay
     *
     * @return frames presented.
     */
    public int getVideoFramesPresented() {
        return videoFramesRendered;
    }

    /**
     * Reason for playback ending, based on final {@link PlaybackStats} playback state and any player error.
     *
     * @return reason playback ended
     */
    public EndReason getEndReason() {
        return endReason;
    }

    public enum EndReason {
        /**
         * Still playing
         */
        NONE,
        /**
         * Ended with error, see {@link #getEndedWithError()}
         */
        ERROR,
        /**
         * Reached the end of the playlists for VOD
         */
        END_OF_CONTENT,
        /**
         * User loaded annother URL or changed channel
         */
        USER_ENDED
    }
}
