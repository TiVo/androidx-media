package com.tivo.exoplayer.library.metrics;

import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK;
import static com.google.android.exoplayer2.Player.EVENT_PLAYBACK_STATE_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_POSITION_DISCONTINUITY;
import static com.tivo.exoplayer.library.metrics.TrickPlayMetricsHelper.createEventsAtSameEventTime;

import android.util.Log;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.util.EventLogger;
import java.util.Map;

import com.google.android.exoplayer2.Player;
import org.json.JSONObject;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.DefaultPlaybackSessionManager;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.analytics.PlaybackStatsListener;
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
    public final PlayerStatisticsHelper playerStatisticsHelper;

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

    /**
     * During Trick-play the regular playback metrics listener (the outer class
     * {@link #playbackStatsListener}) is "disconnected" from the player so events are only
     * directed to the trick-play stats listener.  At the end of Trick-play we report
     * the entire trick-play session as a position discontinuity to the regular playback metrics
     * listener.
     *
     */
    private class TrickPlayMetricsHelperListener implements MetricsEventListener {
        long lastTrickPlayStartTimeMs;
        private Player.PositionInfo lastTrickPlayStartPosition;
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

        /**
         * Entering trick-play suspends the main PlaybackMetrics collection (by removing
         * it's {@link AnalyticsListener}) then posting events to the removed listener to
         * make the trick-play period appear as a seek operation (Player reports STATE_BUFFERING and
         * a discontinuity matching the trick-play position change is recorded).
         */
        @Override
        public void enteringTrickPlayMeasurement() {
            currentPlayer.removeAnalyticsListener(playbackStatsListener);
            playerStatisticsHelper.enterTrickPlay(currentPlayer.getVideoDecoderCounters());

            lastTrickPlayStartTimeMs = clock.elapsedRealtime();
            lastTrickPlayStartPosition = getCurrentPositionInfo();

            // TODO - Creating a separate tracker/session with MetricsPlaybackSessionManager for VTP is a better
            //        solution to hacking at the PlaybackStatsListener.  Now we have to fake state in the player to
            //        cause the tracker to show the time as seeking

            // Fake like trick play is start of a seek operation
            AnalyticsListener.EventTime eventTime = createEventTime(lastTrickPlayStartTimeMs);
            Player stateMask = new StateMaskingPlayerFacade(currentPlayer, Player.STATE_BUFFERING);
            playbackStatsListener.onPositionDiscontinuity(eventTime, lastTrickPlayStartPosition, getCurrentPositionInfo(), DISCONTINUITY_REASON_SEEK);
            playbackStatsListener.onEvents(stateMask, createEventsAtSameEventTime(eventTime, EVENT_POSITION_DISCONTINUITY, EVENT_PLAYBACK_STATE_CHANGED));

            parentListener.enteringTrickPlayMeasurement();
        }

        /**
         * Mark the time while in trick-play the create a discontinuity event (seek) to represent the position change
         */
        @Override
        public void exitingTrickPlayMeasurement() {
            long stoppedTrickPlayAt = clock.elapsedRealtime();
            long trickPlayTime = stoppedTrickPlayAt - lastTrickPlayStartTimeMs;
            createOrReturnCurrent().addTrickPlayTime(trickPlayTime);

            // TODO  - see the TODO in enteringTrickPlayMeasurement()

            // Exit the VTP operation in the main playback event listener, since it is the end of a long seek, any time the player is
            // buffering after this call is treated as buffering after a seek (so not rebuffering or stalling)
            //
            AnalyticsListener.EventTime eventTime = createEventTime(stoppedTrickPlayAt);
            playbackStatsListener.onPositionDiscontinuity(eventTime, lastTrickPlayStartPosition, getCurrentPositionInfo(), DISCONTINUITY_REASON_SEEK);
            playbackStatsListener.onEvents(currentPlayer, createEventsAtSameEventTime(eventTime, EVENT_POSITION_DISCONTINUITY));
            currentPlayer.addAnalyticsListener(playbackStatsListener);
            if (currentPlayer != null && currentPlayer.getVideoDecoderCounters() != null) {
                playerStatisticsHelper.exitTrickPlay(currentPlayer.getVideoDecoderCounters());
            }
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
        playerStatisticsHelper = new PlayerStatisticsHelper();
        player.addAnalyticsListener(playerStatisticsHelper);
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

    /**
     * Return a PositionInfo useable for {@link PlaybackStatsListener} recording position
     * changes.
     *
     * @return PositionInfo for the current position, only supports single period timeline
     */
    private Player.PositionInfo getCurrentPositionInfo() {
        return new Player.PositionInfo(
                null,
                0,
                null,
                0,
                currentPlayer.getCurrentPosition(),
                currentPlayer.getContentPosition(),
                C.INDEX_UNSET,
                C.INDEX_UNSET);
    }

    @Override
    public void endAllSessions() {
        trickPlayMetricsHelper.endCurrentTrickPlaySession();
        playbackStatsListener.finishAllSessions();
        currentPlayer.removeAnalyticsListener(playerStatisticsHelper);
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

            DecoderCounters counters = null;
            if (currentPlayer != null && currentPlayer.getVideoDecoderCounters() != null){
                counters = currentPlayer.getVideoDecoderCounters();
            }
            priorMetrics.updateValuesFromStats(stats, currentRealtime, playerStatisticsHelper, counters == null ? new DecoderCounters() : counters);
        }
    }

    private void playbackStatsUpdate(AnalyticsListener.EventTime sessionStartTime, PlaybackStats stats) {
        PlaybackMetrics playbackMetrics = createOrReturnCurrent();
        fillInPlaybackMetrics(playbackMetrics, stats);
        MetricsPlaybackSessionManager.SessionInformation sessionInformation =
                sessionManager.getSessionInformationFor(sessionStartTime.realtimeMs);

        Map<String, Object> loggedStats = playbackMetrics.getMetricsAsMap();


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
