package com.tivo.exoplayer.library.metrics;

import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.VisibleForTesting;

import java.util.Map;

import com.google.android.exoplayer2.Player;
import org.json.JSONObject;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.DefaultPlaybackSessionManager;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.analytics.PlaybackStatsListener;
import com.google.android.exoplayer2.source.hls.HlsManifest;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.util.Clock;

/**
 * Manages collection and producing {@link PlaybackMetrics} for use in an operational intelligence dashboard
 * This class listens to the various ExoPlayer interfaces and gathers information from the ExoPlayer components
 * to produce the set of playback metrics.
 *
 * The concept is to abstract all reference to ExoPlayer classes and interfaces behind this class and the
 * {@link PlaybackMetrics} it produces.
 */
public class ManagePlaybackMetrics implements PlaybackMetricsManagerApi {
    private static final String TAG = "ManagePlaybackMetrics";

    private final MetricsEventListener eventListener;
    private final SimpleExoPlayer currentPlayer;
    private final Clock clock;
    private final MetricsPlaybackSessionManager sessionManager;
    private final TrickPlayMetricsHelper trickPlayMetricsHelper;

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
         * @param player           - player metrics are managed for
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
         * This callback is used to allow the client to create subclasses of the base metrics objects ({@link
         * PlaybackMetrics} and {@link TrickPlayMetrics}) as well as report events when the metrics are available with
         * updated values.
         *
         * @param callback call back for reporting metrics events.
         */
        public Builder setMetricsEventListener(MetricsEventListener callback) {
            this.callback = callback;
            return this;
        }

        public PlaybackMetricsManagerApi build() {
            Clock finalClock = clock == null ? Clock.DEFAULT : this.clock;
            MetricsEventListener listener = this.callback == null ? new MetricsEventListener() {} : callback;
            return new ManagePlaybackMetrics(finalClock, trickPlayControl, player, listener);
        }
    }

    private class TrickPlayMetricsHelperListener implements MetricsEventListener {
        long lastTrickPlayStartTimeMs;
        private final MetricsEventListener parentListener;

        private TrickPlayMetricsHelperListener(MetricsEventListener parentListener) {
            this.parentListener = parentListener;
        }

        @Override
        public TrickPlayMetrics createEmptyTrickPlayMetrics(TrickPlayControl.TrickMode currentMode, TrickPlayControl.TrickMode prevMode) {
            return parentListener.createEmptyTrickPlayMetrics(currentMode, prevMode);
        }

        @Override
        public void trickPlayMetricsAvailable(TrickPlayMetrics metrics, PlaybackStats stats) {
            parentListener.trickPlayMetricsAvailable(metrics, stats);
        }

        @Override
        public void enteringTrickPlayMeasurement() {
            currentPlayer.removeAnalyticsListener(playbackStatsListener);
            lastTrickPlayStartTimeMs = clock.elapsedRealtime();

            // Mark time period in regular playback as PAUSED during the VTP operation, TODO - perhaps background is better?
            playbackStatsListener.onPlayWhenReadyChanged(createEventTime(lastTrickPlayStartTimeMs), false, Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM);
            parentListener.enteringTrickPlayMeasurement();

        }

        @Override
        public void exitingTrickPlayMeasurement() {
            long stoppedTrickPlayAt = clock.elapsedRealtime();
            long trickPlayTime = stoppedTrickPlayAt - lastTrickPlayStartTimeMs;
            createOrReturnCurrent().addTrickPlayTime(trickPlayTime);

            // Once VTP ends, end the virtual "PAUSED" state and restore the regular playback stats listener
            playbackStatsListener.onPlayWhenReadyChanged(createEventTime(stoppedTrickPlayAt), true, Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM);
            currentPlayer.addAnalyticsListener(playbackStatsListener);
            parentListener.exitingTrickPlayMeasurement();
        }
    }

    private ManagePlaybackMetrics(Clock clock, TrickPlayControl trickPlayControl, SimpleExoPlayer player, MetricsEventListener callback) {
        this.currentPlayer = player;
        this.clock = clock;
        eventListener = callback;
        sessionManager = new MetricsPlaybackSessionManager(new DefaultPlaybackSessionManager());
        playbackStatsListener =
                new PlaybackStatsListener(true, sessionManager, (eventTime, playbackStats) -> playbackStatsUpdate(eventTime, playbackStats));
        player.addAnalyticsListener(playbackStatsListener);

        trickPlayMetricsHelper = new TrickPlayMetricsHelper(clock, player, trickPlayControl, new TrickPlayMetricsHelperListener(callback));
        trickPlayControl.addEventListener(trickPlayMetricsHelper);
    }

    private AnalyticsListener.EventTime createEventTime(long eventTime) {
        long position = currentPlayer.getCurrentPosition();
        return new AnalyticsListener.EventTime(
                eventTime,
                currentPlayer.getCurrentTimeline(),
                /* windowIndex= */ 0,
                /* mediaPeriodId= */ null,
                /* eventPlaybackPositionMs= */ position,
                currentPlayer.getCurrentTimeline(),
                /* currentWindowIndex= */ 0,
                /* currentMediaPeriodId= */ null,
                /* currentPlaybackPositionMs= */ position,
                /* totalBufferedDurationMs= */ 0);
    }

    @Override
    public void endAllSessions() {
        trickPlayMetricsHelper.endCurrentTrickPlaySession();
        playbackStatsListener.finishAllSessions();
    }

    @Override
    public void resetPlaybackMetrics() {
        if (currentPlaybackMetrics != null) {
            PlaybackMetrics priorMetrics = currentPlaybackMetrics;
            currentPlaybackMetrics = eventListener.createEmptyPlaybackMetrics();
            currentPlaybackMetrics.initialize(clock.elapsedRealtime(), priorMetrics);
            // TODO - if we need to reset, here's where we clear the currentPlaybackMetrics
        }
    }

    @Override
    public boolean updateFromCurrentStats(PlaybackMetrics metrics) {
        PlaybackStats stats = playbackStatsListener.getPlaybackStats();
        if (stats != null) {
            fillInPlaybackMetrics(metrics, stats);
        }
        return stats != null;
    }

    @Override
    public synchronized PlaybackMetrics createOrReturnCurrent() {
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

        Map<String, Object> loggedStats = playbackMetrics.getMetricsAsMap();

        loggedStats.put("totalTimeMs", stats.getTotalElapsedTimeMs());


        JSONObject jsonObject = new JSONObject(loggedStats);
        Log.i(TAG, "session end stats, URL: " + sessionInformation.getSessionUrl() + " stats: " + jsonObject.toString());
        eventListener.playbackMetricsAvailable(playbackMetrics, sessionInformation.getSessionUrl());
    }

    static String eventDebug(AnalyticsListener.EventTime first) {
        String timelineStr = "";
        if (first.timeline.isEmpty()) {
            timelineStr = "empty";
        } else {
            Timeline timeline = first.timeline;
            Timeline.Window window = timeline.getWindow(0, new Timeline.Window());
            timelineStr = "url: " + window.mediaItem.mediaId;
        }

        return timelineStr + " at " + first.realtimeMs;
    }
}
