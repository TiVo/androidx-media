package com.tivo.exoplayer.library.metrics;

import android.app.Activity;
import android.util.Log;
import androidx.annotation.VisibleForTesting;

import java.util.Map;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.DefaultPlaybackSessionManager;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.analytics.PlaybackStatsListener;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.EventLogger;

/**
 * Manages collection and producing {@link PlaybackMetrics} for use in an operational intelligence dashboard
 * This class listens to the various ExoPlayer interfaces and gathers information from the ExoPlayer components
 * to produce the set of playback metrics.
 *
 * The concept is to abstract all reference to ExoPlayer classes and interfaces behind this class and the
 * {@link PlaybackMetrics} it produces.
 */
public class ManagePlaybackMetrics {
    private static final String TAG = "ManagePlaybackMetrics";

    private final PlaybackStatsListener playbackStatsListener;
    private PlaybackMetrics currentPlaybackMetrics;
    private final Clock clock;
    private final MetricsPlaybackSessionManager sessionManager;


    /**
     * Create and associate with a player.
     *
     * @param player - the SimpleExoPlayer to manage playback metrics for
     * @param clock - instance of {@link Clock} used for the player, or test instance
     */
    public ManagePlaybackMetrics(SimpleExoPlayer player, Clock clock) {
        this(clock);
        player.addAnalyticsListener(playbackStatsListener);
    }

    @VisibleForTesting
    ManagePlaybackMetrics(Clock clock) {
        this.clock = clock;
        sessionManager = new MetricsPlaybackSessionManager(new DefaultPlaybackSessionManager());
        playbackStatsListener =
                new PlaybackStatsListener(true, sessionManager, (eventTime, playbackStats) -> playbackStatsUpdate(eventTime, playbackStats));
    }

    @VisibleForTesting
    PlaybackStatsListener getPlaybackStatsListener() {
        return playbackStatsListener;
    }

    /**
     * This method will trigger the final {@link #playbackStatsUpdate(AnalyticsListener.EventTime, PlaybackStats)} event
     * and end the current stats session.
     *
     * It should be called just prior to releasing the player (e.g. in
     * the {@link Activity} lifecycle method, onStop or destory).   After this method is called simply discard the
     * reference to this class and create a new one when needed
     */
    public void endAllSessions() {
        playbackStatsListener.finishAllSessions();
    }

    /**
     * Set the a new playback metrics object to collect playback metrics into, return any
     * current one (if previously set) with the values filled in since the list call to
     * this method.
     *
     * @param playbackMetrics new {@link PlaybackMetrics} object to begin collection for
     * @return PlaybackMetrics with valid values since the last call to this method, or null if no prior metrics
     */
    public PlaybackMetrics setCurrentPlaybackMetrics(PlaybackMetrics playbackMetrics) {
        PlaybackMetrics priorMetrics = currentPlaybackMetrics;
        currentPlaybackMetrics = playbackMetrics;
        playbackMetrics.initialize(clock.elapsedRealtime(), priorMetrics);

        fillInPlaybackMetrics(priorMetrics);
        return priorMetrics;
    }

    /**
     * Get the current state of the PlaybackMetrics, with values since the last call
     * to {@link #setCurrentPlaybackMetrics(PlaybackMetrics)}.
     *
     * @return current PlaybackMetrics from last reset to current time, or null if setPlaybackMetrics was not called.
     */
    public PlaybackMetrics getUpdatedPlaybackMetrics() {
        fillInPlaybackMetrics(currentPlaybackMetrics);
        return currentPlaybackMetrics;
    }

    private void fillInPlaybackMetrics(PlaybackMetrics priorMetrics) {
        if (priorMetrics != null) {
            PlaybackStats stats = playbackStatsListener.getPlaybackStats();
            long currentRealtime = clock.elapsedRealtime();
            priorMetrics.updateValuesFromStats(stats, currentRealtime);
        }
    }

    protected void playbackStatsUpdate(AnalyticsListener.EventTime sessionStartTime, PlaybackStats stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n  ");
        sb.append("total time:  ");
        sb.append(stats.getTotalElapsedTimeMs());sb.append("ms");
        sb.append("\n  ");
        sb.append("startup time:  ");
        sb.append(stats.getMeanJoinTimeMs());sb.append("ms");
        sb.append("\n  ");
        sb.append("playing time:  ");
        sb.append(stats.getTotalPlayTimeMs());sb.append("ms");
        sb.append("\n  ");
        sb.append("paused time:  ");
        sb.append(stats.getTotalPausedTimeMs());sb.append("ms");
        sb.append("\n  ");
        sb.append("re-buffering time:  ");
        sb.append(stats.getTotalRebufferTimeMs());sb.append("ms");
        sb.append("\n  ");
        sb.append("format changes:  ");
        sb.append(stats.videoFormatHistory.size());
        sb.append("\n  ");
        sb.append("avg video bitrate:  ");
        sb.append(stats.getMeanVideoFormatBitrate());
        sb.append("\n  ");
        sb.append("mean bandwidth: ");
        sb.append(stats.getMeanBandwidth() / 1_000_000.0f);
        sb.append(" Mbps");

        MetricsPlaybackSessionManager.SessionInformation sessionInformation =
                sessionManager.getSessionInformationFor(sessionStartTime.realtimeMs);
        Map<Format, Long> timeInFormat = PlaybackStatsExtension.getPlayingTimeInFormat(stats, C.TIME_UNSET);

        String currentUri = sessionInformation.getSessionUrl();

        Log.d(TAG, "Playback stats for '" + currentUri + "'" + sb.toString());

        if (stats.videoFormatHistory.size() > 0) {
            Log.d(TAG, "Time in variant:");
            for (Map.Entry<Format, Long> entry : timeInFormat.entrySet()) {
                Log.d(TAG, "  " + EventLogger.getVideoLevelStr(entry.getKey()) + " played for " + entry.getValue() + " ms total");
            }
        }
    }

}
