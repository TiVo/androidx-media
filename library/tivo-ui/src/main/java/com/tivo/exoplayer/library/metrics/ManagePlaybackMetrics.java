package com.tivo.exoplayer.library.metrics;

import android.app.Activity;
import android.util.Log;
import androidx.annotation.VisibleForTesting;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.DefaultPlaybackSessionManager;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.analytics.PlaybackStatsListener;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
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

    private final MetricsEventListener eventListener;
    private final SimpleExoPlayer currentPlayer;
    private final Clock clock;
    private final TrickPlayControl trickPlayControl;
    private final MetricsPlaybackSessionManager sessionManager;

    private PlaybackStatsListener playbackStatsListener;
    private PlaybackMetrics currentPlaybackMetrics;


    public static class Builder {
        private final SimpleExoPlayer player;
        private final TrickPlayControl trickPlayControl;
        private Clock clock;
        private MetricsEventListener callback;

        /**
         * Base builder for creating a {@link ManagePlaybackMetrics} with required arguments.
         *
         * @param player - player metrics are managed for
         * @param trickPlayControl - trickplay control associated with the player
         */
        public Builder(SimpleExoPlayer player, TrickPlayControl trickPlayControl) {
            this.player = player;
            this.trickPlayControl = trickPlayControl;
        }

        @VisibleForTesting
        public Builder setClock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /**
         * This callback is used to allow the client to create subclasses of the base
         * metrics objects ({@link PlaybackMetrics} and {@link TrickPlayMetrics}) as well
         * as report events when the metrics are available with updated values.
         *
         * @param callback call back for reporting metrics events.
         */
        public Builder setMetricsEventListener(MetricsEventListener callback) {
            this.callback = callback;
            return this;
        }

        public ManagePlaybackMetrics build() {
            Clock finalClock = clock == null ? Clock.DEFAULT : this.clock;
            MetricsEventListener listener = this.callback == null ? new MetricsEventListener() {} : callback;
            return new ManagePlaybackMetrics(finalClock, trickPlayControl, player, listener);
        }
    }

    private ManagePlaybackMetrics(Clock clock, TrickPlayControl trickPlayControl, SimpleExoPlayer player, MetricsEventListener callback) {
        this.currentPlayer = player;
        this.clock = clock;
        this.trickPlayControl = trickPlayControl;
        eventListener = callback;
        sessionManager = new MetricsPlaybackSessionManager(new DefaultPlaybackSessionManager());
        playbackStatsListener =
                new PlaybackStatsListener(true, sessionManager, (eventTime, playbackStats) -> playbackStatsUpdate(eventTime, playbackStats));
        player.addAnalyticsListener(playbackStatsListener);

        trickPlayControl.addEventListener(new TrickPlayMetricsHelper(clock, player, new MetricsEventListener() {
            long lastTrickPlayStartTimeMs;

            @Override
            public void enteringTrickPlayMeasurement() {
                player.removeAnalyticsListener(playbackStatsListener);
                lastTrickPlayStartTimeMs = clock.elapsedRealtime();

                // Mark time period in regular playback as "SEEKING" during the VTP operation, most logical bucket for now
                playbackStatsListener.onSeekStarted(createEventTime(lastTrickPlayStartTimeMs));
                callback.exitingTrickPlayMeasurement();
            }

            @Override
            public void exitingTrickPlayMeasurement() {
                long stoppedTrickPlayAt = clock.elapsedRealtime();
                long trickPlayTime = stoppedTrickPlayAt - lastTrickPlayStartTimeMs;
                createOrReturnCurrent().addTrickPlayTime(trickPlayTime);

                // Once VTP ends, end the virtual "SEEK" and restore the regular playback stats listener
                playbackStatsListener.onSeekProcessed(createEventTime(stoppedTrickPlayAt));
                player.addAnalyticsListener(playbackStatsListener);
            }
        }));
    }

    private AnalyticsListener.EventTime createEventTime(long eventTime) {
        long position = currentPlayer.getCurrentPosition();
        return new AnalyticsListener.EventTime(eventTime, currentPlayer.getCurrentTimeline(),
                0, null, position, position, 0);
    }

    /**
     * This method will trigger the final {@link #playbackStatsUpdate(AnalyticsListener.EventTime, PlaybackStats)} event
     * and end the current stats session.
     *
     * It should be called when:
     * <ol>
     *     <li>There is a fatal player error reported.</li>
     *     <li>Prior to releasing the player - (e.g. in the {@link Activity} lifecycle method, onStop or destory).</li>
     * </ol>
     *
     */
    public void endAllSessions() {
        playbackStatsListener.finishAllSessions();
    }

    /**
     * Sets a "lap counter" reset in the PlaybackMetrics, so values that are accumulated only from
     * the last call to this method to the call to {@link #getUpdatedPlaybackMetrics()} are accounted
     * properly.
     *
     * TODO - is this needed still?
     */
    public void resetPlaybackMetrics() {
        if (currentPlaybackMetrics != null) {
            PlaybackMetrics priorMetrics = currentPlaybackMetrics;
            currentPlaybackMetrics = eventListener.createEmptyPlaybackMetrics();
            currentPlaybackMetrics.initialize(clock.elapsedRealtime(), priorMetrics);
        }
    }

    /**
     * Get the current PlaybackMetrics, with values since the start of the
     * current playback session to now.
     *
     * Any values that are only accumulated since the last reset are just deltas since the reset.
     *
     * @return current PlaybackMetrics filled in with stats from the current session.
     */
    public PlaybackMetrics getUpdatedPlaybackMetrics() {
        createOrReturnCurrent();
        PlaybackStats stats = playbackStatsListener.getPlaybackStats();
        if (stats != null) {
            fillInPlaybackMetrics(createOrReturnCurrent(), stats);
        }
        return currentPlaybackMetrics;
    }

    private PlaybackMetrics createOrReturnCurrent() {
        if (currentPlaybackMetrics == null) {
            currentPlaybackMetrics = eventListener.createEmptyPlaybackMetrics();
        }
        return currentPlaybackMetrics;
    }

    private void fillInPlaybackMetrics(PlaybackMetrics priorMetrics, PlaybackStats stats) {
        if (priorMetrics != null) {
            long currentRealtime = clock.elapsedRealtime();
            priorMetrics.updateValuesFromStats(stats, currentRealtime);
        }
    }

    private void playbackStatsUpdate(AnalyticsListener.EventTime sessionStartTime, PlaybackStats stats) {
        PlaybackMetrics playbackMetrics = createOrReturnCurrent();
        fillInPlaybackMetrics(playbackMetrics, stats);
        MetricsPlaybackSessionManager.SessionInformation sessionInformation =
                sessionManager.getSessionInformationFor(sessionStartTime.realtimeMs);

        Map<String, Object> loggedStats = new HashMap<>();
        loggedStats.put("totalTimeMs", stats.getTotalElapsedTimeMs());
        loggedStats.put("startDelayMs", playbackMetrics.getInitialPlaybackStartDelay());
        loggedStats.put("totalPlayingTimeMs", playbackMetrics.getTotalPlaybackTimeMs());
        loggedStats.put("totalPausedTimeMs", stats.getTotalPausedTimeMs());
        loggedStats.put("totalSeekTimeMs", stats.getTotalSeekTimeMs());
        loggedStats.put("totalRebufferTimeMs", stats.getTotalRebufferTimeMs());
        loggedStats.put("totalTrickPlayTimeMs", playbackMetrics.getTotalTrickPlayTime());
        loggedStats.put("formatChanges", playbackMetrics.getProfileShiftCount());
        loggedStats.put("avgRebufferingTimeMs", playbackMetrics.getAvgRebufferTime());
        loggedStats.put("avgVideoBitrate", playbackMetrics.getAvgVideoBitrate());
        loggedStats.put("avgBandwidthMbps", playbackMetrics.getAvgNetworkBitrate());
        loggedStats.put("endedFor", playbackMetrics.getEndReason().toString());
        if (playbackMetrics.getEndReason() == PlaybackMetrics.EndReason.ERROR) {
            loggedStats.put("playbackError", currentPlaybackMetrics.getEndedWithError().toString());
        }

        Map<Format, Long> timeInFormat = playbackMetrics.getTimeInVideoFormat();
        Map<String, Long> timeInVariant = new HashMap<>();
        for (Map.Entry<Format, Long> entry : timeInFormat.entrySet()) {
            timeInVariant.put(EventLogger.getVideoLevelStr(entry.getKey()), entry.getValue());
        }
        loggedStats.put("timeInFormats", timeInVariant);
        JSONObject jsonObject = new JSONObject(loggedStats);
        Log.i(TAG, "session end stats, URL: " + sessionInformation.getSessionUrl() + " stats: " + jsonObject.toString());

        eventListener.playbackMetricsAvailable(stats, sessionInformation, playbackMetrics);

    }

}
