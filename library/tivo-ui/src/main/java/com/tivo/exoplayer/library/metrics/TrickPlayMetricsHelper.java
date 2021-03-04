package com.tivo.exoplayer.library.metrics;

import org.json.JSONObject;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.PlaybackSessionManager;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.analytics.PlaybackStatsListener;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.trickplay.TrickPlayEventListener;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;

class TrickPlayMetricsHelper implements TrickPlayEventListener, PlaybackStatsListener.Callback {
    private static final String TAG = "TrickPlayMetricsHelper";
    private final MetricsEventListener metricsEventCallback;
    private final TrickPlayMetricsAnalyticsListener trickPlayMetricsAnalyticsListener;
    private TrickPlayMetrics currentTrickPlayMetrics;
    private PlaybackStatsListener trickPlayStatsListener;
    private final Clock clock;
    private final SimpleExoPlayer currentPlayer;
    private final TrickPlayControl trickPlayControl;

    /**
     * A scant few interesting metrics that are not supported by {@link PlaybackStats} can be
     * gleaned from {@link AnalyticsListener} events from the player.
     *
     * This listener is created and attached to the player while we are in trickplay.
     */
    private class TrickPlayMetricsAnalyticsListener implements AnalyticsListener {
        @Override
        public void onLoadCompleted(EventTime eventTime,
                                    MediaSourceEventListener.LoadEventInfo loadEventInfo,
                                    MediaSourceEventListener.MediaLoadData mediaLoadData) {
            if (currentTrickPlayMetrics != null && isTrickPlay(mediaLoadData)) {
                currentTrickPlayMetrics.recordLoadedIframeInfo(new TrickPlayMetrics.IframeLoadEvent(eventTime.realtimeMs, loadEventInfo, mediaLoadData));
            }
        }

        @Override
        public void onLoadCanceled(EventTime eventTime,
                                   MediaSourceEventListener.LoadEventInfo loadEventInfo,
                                   MediaSourceEventListener.MediaLoadData mediaLoadData) {
            if (currentTrickPlayMetrics != null && isTrickPlay(mediaLoadData)) {
                currentTrickPlayMetrics.incrLoadCancels();
            }
        }


        private boolean isTrickPlay(MediaSourceEventListener.MediaLoadData mediaLoadData) {
            Format loadedFormat = mediaLoadData.trackFormat;
            boolean isTrickPlay = loadedFormat != null && (loadedFormat.roleFlags & C.ROLE_FLAG_TRICK_PLAY) != 0;
            return isTrickPlay;
        }

    }

    TrickPlayMetricsHelper(Clock clock, SimpleExoPlayer player, TrickPlayControl trickPlayControl, MetricsEventListener listener) {
        this.clock = clock;
        currentPlayer = player;
        this.trickPlayControl = trickPlayControl;
        metricsEventCallback = listener;
        trickPlayMetricsAnalyticsListener = new TrickPlayMetricsAnalyticsListener();
    }

    // Implement TrickPlayEventListener

    /**
     * Listen for enter/exit from trick-play as well as intra trick-play mode changes and manage the
     * listener for PlaybackStats.
     *
     * When entering/exiting trick-play to/from {@link TrickPlayControl.TrickMode#NORMAL} call the MetricsEventListener
     * ({@link MetricsEventListener#enteringTrickPlayMeasurement()} and {@link MetricsEventListener#exitingTrickPlayMeasurement()}
     * to allow it to record the trick-play sequence and suspend it's own measurements.
     *
     * Each trick-play mode change a new {@link PlaybackSessionManager} session is created to seperate out the metrics
     * for each session.
     *
     * TODO - currently, a new listener is created each change, this does not allow rolling up all trickplay sessions
     * TODO - stats into one, if this is needed, modify the {@link MetricsPlaybackSessionManager} to manage it
     *
     * @param newMode - the trickplay mode currently being played
     * @param prevMode - the previous mode before the change
     */
    @Override
    public void trickPlayModeChanged(TrickPlayControl.TrickMode newMode, TrickPlayControl.TrickMode prevMode) {
        Log.d(TAG, "trickPlayModeChanged() from : " + prevMode + " to: " + newMode + " - currentTrickPLayMetrics: " + currentTrickPlayMetrics);

        switch (TrickPlayControl.directionForMode(newMode)) {
            case REVERSE:
            case FORWARD:
            case SCRUB:
                // on initial switch into trick-play, remove the listeners for normal playback stats
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
        AnalyticsListener.EventTime endedEventTime = createEventTimeNow();

        currentTrickPlayMetrics.updateOnSessionEnd(playbackStats, eventTime, endedEventTime);

        JSONObject jsonObject = new JSONObject(currentTrickPlayMetrics.getMetricsAsMap());
        Log.i(TAG, "trick-play end stats: " + jsonObject.toString());

        metricsEventCallback.trickPlayMetricsAvailable(currentTrickPlayMetrics, playbackStats);

        currentTrickPlayMetrics = null;
    }

    /**
     * Called to end the current trickplay session.  If it's not already ended (trickPlayStatsListener is active) then
     * remove the listener, and ff the session has not already ended (as could have happened for abandoning a trick-play
     * in progress with a channel change or new URL), then call finish the session
     */
    void endCurrentTrickPlaySession() {

        // Remove the Trick-play PlaybackStatsListener if any
        if (trickPlayStatsListener != null) {
            currentPlayer.removeAnalyticsListener(trickPlayStatsListener);
            currentPlayer.removeAnalyticsListener(trickPlayMetricsAnalyticsListener);

            trickPlayStatsListener.onPlayerStateChanged(createEventTimeNow(), false, Player.STATE_IDLE);

            // If the session has not already ended, end it
            if (currentTrickPlayMetrics != null) {
                trickPlayStatsListener.finishAllSessions();
            }
            trickPlayStatsListener = null;
        }
    }


    // Private methods

    private void createNewTrickPlaySession(TrickPlayControl.TrickMode newMode, TrickPlayControl.TrickMode prevMode) {
        Log.d(TAG, "Create a new trickplay session: " + newMode + " prevMode: " + prevMode);

        // End any current trickplay session
        endCurrentTrickPlaySession();

        currentTrickPlayMetrics = metricsEventCallback.createEmptyTrickPlayMetrics(newMode, prevMode);
        currentTrickPlayMetrics.setExpectedPlaybackSpeed(trickPlayControl.getSpeedFor(newMode));
        trickPlayStatsListener = new PlaybackStatsListener(true, this);
        currentPlayer.addAnalyticsListener(trickPlayStatsListener);
        currentPlayer.addAnalyticsListener(trickPlayMetricsAnalyticsListener);

        Format current = currentPlayer.getVideoFormat();
        if (currentTrickPlayMetrics != null && current != null && currentTrickPlayMetrics.isIntraTrickPlayChange()) {
            // initially we are already playing and playing the current video format.
            trickPlayStatsListener.onPlayerStateChanged(createEventTimeNow(), true, Player.STATE_READY);
            trickPlayStatsListener.onDownstreamFormatChanged(createEventTimeNow(), createMediaLoad(current));
        }

    }

    private AnalyticsListener.EventTime createEventTimeNow() {
        return new AnalyticsListener.EventTime(clock.elapsedRealtime(), Timeline.EMPTY, 0, null,
                0, currentPlayer.getCurrentPosition(), 0);
    }

    private static MediaSourceEventListener.MediaLoadData createMediaLoad(Format format) {
        int type = MimeTypes.getTrackType(format.sampleMimeType);
        return new MediaSourceEventListener.MediaLoadData(0, type, format, 0, null, 0, 0);
    }

}
