package com.tivo.exoplayer.library.metrics;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.analytics.PlaybackStatsListener;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.trickplay.TrickPlayEventListener;
import com.google.android.exoplayer2.util.Clock;

class TrickPlayMetricsHelper implements TrickPlayEventListener, PlaybackStatsListener.Callback {
    private final MetricsEventListener metricsEventCallback;
    private TrickPlayMetrics currentTrickPlayMetrics;
    private PlaybackStatsListener trickPlayStatsListener;
    private final Clock clock;
    private final SimpleExoPlayer currentPlayer;

    TrickPlayMetricsHelper(Clock clock, SimpleExoPlayer player, MetricsEventListener listener) {
        this.clock = clock;
        currentPlayer = player;
        metricsEventCallback = listener;
    }

    // Implement TrickPlayEventListener

    @Override
    public void trickPlayModeChanged(TrickPlayControl.TrickMode newMode, TrickPlayControl.TrickMode prevMode) {
        switch (TrickPlayControl.directionForMode(newMode)) {
            case REVERSE:
            case FORWARD:
            case SCRUB:
                // on initial swtich into trick-play, remove the listeners for normal playback stats
                if (prevMode == TrickPlayControl.TrickMode.NORMAL) {
                    metricsEventCallback.enteringTrickPlayMeasurement();
                }
                createNewTrickPlaySession(newMode, prevMode);
                break;

            case NONE:      // exiting VTP, log end of session and restore listener
                endCurrentTrickPlaySession();
                metricsEventCallback.exitingTrickPlayMeasurement();
                break;


        }
    }

    @Override
    public void trickFrameRendered(long frameRenderTimeUs) {
        if (currentTrickPlayMetrics != null) {
            currentTrickPlayMetrics.incrRenderedFrames(frameRenderTimeUs);
        }
    }

    // Implement the stats callback

    /**
     * Each Trick-play mode changes we finish the current playback session and create a new stats listener
     *
     * @param eventTime     The {@link AnalyticsListener.EventTime} at which the playback session started. Can be used to identify the
     *                      playback session.
     * @param playbackStats The {@link PlaybackStats} for the ended playback session.
     */
    @Override
    public void onPlaybackStatsReady(AnalyticsListener.EventTime eventTime, PlaybackStats playbackStats) {
        AnalyticsListener.EventTime endedEventTime =
                new AnalyticsListener.EventTime(clock.elapsedRealtime(), Timeline.EMPTY, 0, null,
                0, currentPlayer.getCurrentPosition(), 0);

        currentTrickPlayMetrics.updateOnSessionEnd(playbackStats, eventTime, endedEventTime);
        metricsEventCallback.trickPlayMetricsAvailable(currentTrickPlayMetrics, playbackStats);

        currentTrickPlayMetrics = null;
    }

    // Private methods

    private void createNewTrickPlaySession(TrickPlayControl.TrickMode newMode, TrickPlayControl.TrickMode prevMode) {
        endCurrentTrickPlaySession();
        currentTrickPlayMetrics = metricsEventCallback.createEmptyTrickPlayMetrics(newMode, prevMode);
        trickPlayStatsListener = new PlaybackStatsListener(true, this);
        currentPlayer.addAnalyticsListener(trickPlayStatsListener);
    }


    /**
     * remove the current listener for trickplay stats, if any and end it's playback session to flush events.
     */
    private void endCurrentTrickPlaySession() {
        // Remove current listener and flush the session to create and log the trickplay stats
        //
        if (trickPlayStatsListener != null) {
            currentPlayer.removeAnalyticsListener(trickPlayStatsListener);
            trickPlayStatsListener.finishAllSessions();
            trickPlayStatsListener = null;
        }
    }

}
