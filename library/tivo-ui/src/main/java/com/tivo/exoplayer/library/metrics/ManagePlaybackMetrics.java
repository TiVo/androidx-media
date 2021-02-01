package com.tivo.exoplayer.library.metrics;

import android.app.Activity;
import android.util.Log;
import android.util.Pair;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.analytics.PlaybackStatsListener;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.hls.HlsManifest;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
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
    private String currentUri;      // Last prepared URI
    private PlaybackMetrics currentPlaybackMetrics;
    private Clock clock;

    /**
     * The latest (dev-v2) branch of ExoPlayer replaces the <pre>Pair<EventTime, Format></pre> with
     * an actual class.  Once we upgrade to version of ExoPlayer with this object simply delete this
     * class and import the ExoPlayer one
     */
    public static final class EventTimeAndFormat {
        public final AnalyticsListener.EventTime eventTime;
        @Nullable
        public final Format format;

        private EventTimeAndFormat(AnalyticsListener.EventTime eventTime, @Nullable Format format) {
            this.eventTime = eventTime;
            this.format = format;
        }
        public static List<EventTimeAndFormat> fromPairList(List<Pair<AnalyticsListener.EventTime, Format>> pairList) {
            List<EventTimeAndFormat> list = new ArrayList<>(pairList.size());
            for (Pair<AnalyticsListener.EventTime, Format> pair : pairList) {
                list.add(new EventTimeAndFormat(pair.first, pair.second));
            }
            return list;
        }
    }
    public static final class EventTimeAndPlaybackState {
        public final AnalyticsListener.EventTime eventTime;
        public final int playbackState;

        public EventTimeAndPlaybackState(AnalyticsListener.EventTime eventTime, int playbackState) {
            this.eventTime = eventTime;
            this.playbackState = playbackState;
        }
        public static List<EventTimeAndPlaybackState> fromPairList(List<Pair<AnalyticsListener.EventTime, Integer>> pairList) {
            List<EventTimeAndPlaybackState> list = new ArrayList<>(pairList.size());
            for (Pair<AnalyticsListener.EventTime, Integer> pair : pairList) {
                list.add(new EventTimeAndPlaybackState(pair.first, pair.second));
            }
            return list;
        }
    }


    /**
     * Listen for Player Events in order to manage which session is active
     */

    private class PlayerEventListener implements Player.EventListener {
        @Override
        public void onTimelineChanged(Timeline timeline, int reason) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PREPARED && !timeline.isEmpty()) {
                Timeline.Window window = timeline.getWindow(0, new Timeline.Window());

                // TODO - this works for HLS, need something different for DASH.
                // This assumes a SinglePeriodTimeline (so that there is one PlaybackSessionManager session per prepare)
                if (window.manifest instanceof HlsManifest) {
                    HlsManifest manifest = (HlsManifest) window.manifest;
                    currentUri = manifest.masterPlaylist.baseUri;
                }
            }
        }
    }

    /**
     * Create and associate with a player.
     *
     * @param player - the SimpleExoPlayer to manage playback metrics for
     * @param clock - instance of {@link Clock} used for the player, or test instance
     */
    public ManagePlaybackMetrics(SimpleExoPlayer player, Clock clock) {
        this.clock = clock;
        playbackStatsListener = new PlaybackStatsListener(true, (eventTime, playbackStats) -> playbackStatsUpdate(eventTime, playbackStats));
        player.addAnalyticsListener(playbackStatsListener);
        player.addListener(new PlayerEventListener());
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
     * Set the a new playback metrics object to collect playback metrics into, return current one if any
     *
     * @param playbackMetrics new {@link PlaybackMetrics} object to begin collection for
     */
    public PlaybackMetrics setCurrentPlaybackMetrics(PlaybackMetrics playbackMetrics) {
        PlaybackMetrics priorMetrics = currentPlaybackMetrics;
        currentPlaybackMetrics = playbackMetrics;
        playbackMetrics.initialize(clock.elapsedRealtime());

        if (priorMetrics != null) {
            // TODO get the current stats from the session and update the metrics from them
//            playbackMetrics.updateValuesFromStats(...);
        }
        return priorMetrics;
    }



    protected void playbackStatsUpdate(AnalyticsListener.EventTime readTime, PlaybackStats stats) {
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
        sb.append("mean bandwidth: ");
        sb.append(stats.getMeanBandwidth() / 1_000_000.0f);
        sb.append(" Mbps");

        Map<Format, Long> timeInFormat = getTimeInFormat(stats);

        Log.d(TAG, "Playback stats for '" + currentUri + "'" + sb.toString());

        if (stats.videoFormatHistory.size() > 0) {
            Log.d(TAG, "Time in variant:");
            for (Map.Entry<Format, Long> entry : timeInFormat.entrySet()) {
                Log.d(TAG, "  " + EventLogger.getVideoLevelStr(entry.getKey()) + " played for " + entry.getValue() + " ms total");
            }
        }
    }

    /**
     * Using the video format history in the {@link PlaybackStats}, determine the total amount of time
     * in each variant (@link {@link Format} objects decribe each variant).
     *
     * TODO - suggest adding this to ExoPlayer's PlaybackStats and share it
     *
     * @param stats PlaybackStats to analyize and get formats
     * @return a Map, keyed by each Format (Variant) with the time in ms spent "playing" (includes paused, buffering)
     */
    protected Map<Format, Long> getTimeInFormat(PlaybackStats stats) {
        HashMap<Format, Long> timeInFormat = new HashMap<>();
        long lastFormatChangeTime = C.TIME_UNSET;
        EventTimeAndFormat lastFormatEvent = null;
        for (EventTimeAndFormat formatEvent : EventTimeAndFormat.fromPairList(stats.videoFormatHistory)) {
            Long time = timeInFormat.get(formatEvent.format);
            if (time == null) {
                time = 0L;
            }
            if (lastFormatChangeTime != C.TIME_UNSET) {
                time += formatEvent.eventTime.realtimeMs - lastFormatChangeTime;
            }
            timeInFormat.put(formatEvent.format, time);
            lastFormatChangeTime = formatEvent.eventTime.realtimeMs;
            lastFormatEvent = formatEvent;
        }

        long playbackStopTime = findLastStoppedOrEnded(stats);

        // Balance of time is in last format (note could be playing, buffering or paused)
        if (playbackStopTime != C.TIME_UNSET && lastFormatEvent != null) {
            Long time = timeInFormat.get(lastFormatEvent.format);
            time += playbackStopTime - lastFormatChangeTime;
            timeInFormat.put(lastFormatEvent.format, time);
        }
        return timeInFormat;
    }

    private static long findLastStoppedOrEnded(PlaybackStats stats) {
        List<EventTimeAndPlaybackState> stateHistory = EventTimeAndPlaybackState.fromPairList(stats.playbackStateHistory);
        for (int i = stateHistory.size() - 1; i > 0; i--) {
            EventTimeAndPlaybackState endEvent = stateHistory.get(i);
            if (endEvent.playbackState == PlaybackStats.PLAYBACK_STATE_ENDED ||
                    endEvent.playbackState == PlaybackStats.PLAYBACK_STATE_STOPPED) {
                return endEvent.eventTime.realtimeMs;
            }
        }
        return C.TIME_UNSET;
    }

}
