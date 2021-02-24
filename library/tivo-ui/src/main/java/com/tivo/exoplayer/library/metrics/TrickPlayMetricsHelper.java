package com.tivo.exoplayer.library.metrics;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.analytics.PlaybackStatsListener;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.trickplay.TrickPlayEventListener;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Log;

class TrickPlayMetricsHelper implements TrickPlayEventListener, PlaybackStatsListener.Callback {
    private static final String TAG = "TrickPlayMetricsHelper";
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
        Log.d(TAG, "trickPlayModeChanged() from : " + prevMode + " to: " + newMode + " - currentTrickPLayMetrics: " + currentTrickPlayMetrics);

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
        Log.d(TAG, "onPlaybackStatsReady() - currentTrickPLayMetrics: " + currentTrickPlayMetrics);

        AnalyticsListener.EventTime endedEventTime =
                new AnalyticsListener.EventTime(clock.elapsedRealtime(), Timeline.EMPTY, 0, null,
                0, currentPlayer.getCurrentPosition(), 0);

        currentTrickPlayMetrics.updateOnSessionEnd(playbackStats, eventTime, endedEventTime);
        metricsEventCallback.trickPlayMetricsAvailable(currentTrickPlayMetrics, playbackStats);

        currentTrickPlayMetrics = null;
    }

    // Private methods

    private void createNewTrickPlaySession(TrickPlayControl.TrickMode newMode, TrickPlayControl.TrickMode prevMode) {
        Log.d(TAG, "Create a new trickplay session.");
        endCurrentTrickPlaySession();
        currentTrickPlayMetrics = metricsEventCallback.createEmptyTrickPlayMetrics(newMode, prevMode);
        trickPlayStatsListener = new PlaybackStatsListener(true, this);
        currentPlayer.addAnalyticsListener(trickPlayStatsListener);
    }


    /**
     * Called to end the current trickplay session.  If it's not already ended (trickPlayStatsListener is active) then
     * remove the listener, and ff the session has not already ended (as could have happened for abandoning a trick-play
     * in progress with a channel change or new URL), then call finish the session
     */
    private void endCurrentTrickPlaySession() {

        // Remove the Trick-play PlaybackStatsListener if any
        if (trickPlayStatsListener != null) {
            currentPlayer.removeAnalyticsListener(trickPlayStatsListener);

            // If the session has not already ended, end it
            if (currentTrickPlayMetrics != null) {
                trickPlayStatsListener.finishAllSessions();
            }
            trickPlayStatsListener = null;
        }
    }

}
