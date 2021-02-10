package com.tivo.exoplayer.library.metrics;

import android.util.Pair;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import static com.google.android.exoplayer2.analytics.PlaybackStats.PLAYBACK_STATE_PLAYING;

/**
 * Add-ons to the ExoPlayer {@link PlaybackStats} class.
 *
 * TODO - make a pull request and incorporate these into dev-v2 Google ExoPlayer
 */
public class PlaybackStatsExtension {

    /**
     * The latest (dev-v2) branch of ExoPlayer replaces the <pre>Pair<EventTime, Format></pre> with
     * an actual class.  Once we upgrade to version of ExoPlayer with EventTimeAndFormat, delete and import the ExoPlayer one
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

    /**
     * The latest (dev-v2) branch of ExoPlayer replaces the <pre>Pair<EventTime, Integer></pre> with
     * an actual class.  Once we upgrade to version of ExoPlayer with EventTimeAndPlaybackState, delete and import the ExoPlayer one
     */
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
     * Using the video format history in the {@link PlaybackStats}, determine the total amount of time
     * in each variant (@link {@link Format} objects describe each variant).  Note this is time includes
     * only the time actually playing in the format, not paused, seeking, buffering etc.
     *
     * It is assumed that at least a playback end state (PLAYBACK_STATE_ENDED, PLAYBACK_STATE_FAILED, ...)
     * exists and follows the final Format event or toEndTime must be set
     *
     * @param stats PlaybackStats to analyze and get formats
     * @param toEndTime if not C.TIME_UNSET, stop at this time not waiting for end event
     * @return a Map, keyed by each Format (Variant) with the time in ms spent "playing" (includes paused, buffering)
     */
    static Map<Format, Long> getPlayingTimeInFormat(PlaybackStats stats, long toEndTime) {
        HashMap<Format, Long> timeInFormat = new HashMap<>();
        ListIterator<EventTimeAndFormat> formats = EventTimeAndFormat.fromPairList(stats.videoFormatHistory).listIterator();
        ListIterator<EventTimeAndPlaybackState> states =
                EventTimeAndPlaybackState.fromPairList(stats.playbackStateHistory).listIterator();

        EventTimeAndFormat currentFormat = formats.hasNext() ? formats.next() : null;
        AnalyticsListener.EventTime playbackStartTime = null;

        // Loop Format change events, currentFormat is non-null and last format change event

        while (currentFormat != null) {
            EventTimeAndFormat nextFormat = formats.hasNext() ? formats.next() : null;

            // Loop state changes, each state in time range of currentFormat.  Assume at least end or error state after
            // every format.

            boolean stateTimeInCurrentFormat = true;
            while (states.hasNext() && stateTimeInCurrentFormat) {
                EventTimeAndPlaybackState nextState = states.next();
                stateTimeInCurrentFormat = nextFormat == null ||
                        nextState.eventTime.realtimeMs < nextFormat.eventTime.realtimeMs
                        && (toEndTime == C.TIME_UNSET || nextState.eventTime.realtimeMs < toEndTime);
                if (stateTimeInCurrentFormat) {
                    if (nextState.playbackState == PLAYBACK_STATE_PLAYING) {
                        playbackStartTime = nextState.eventTime;
                    } else if (playbackStartTime != null) {
                        long time = nextState.eventTime.realtimeMs - playbackStartTime.realtimeMs;
                        addPlayingTime(currentFormat.format, time, timeInFormat);
                        playbackStartTime = null;
                    }
                } else {
                    if (playbackStartTime != null) {     // playback time is from start to format change
                        long time = nextFormat.eventTime.realtimeMs - playbackStartTime.realtimeMs;
                        addPlayingTime(currentFormat.format, time, timeInFormat);
                        playbackStartTime = nextFormat.eventTime;
                    }
                    states.previous();
                }
            }

            // Add any remaining playing state in current format up to toEndTime if no more states left
            if (toEndTime != C.TIME_UNSET && playbackStartTime != null && playbackStartTime.realtimeMs < toEndTime && ! states.hasNext()) {
                addPlayingTime(currentFormat.format, toEndTime - playbackStartTime.realtimeMs, timeInFormat);
            }

            // next Format becomes current or terminates loop when no more formats
            currentFormat = nextFormat;
        }
        return timeInFormat;
    }

    private static void addPlayingTime(Format format, long playingTime, HashMap<Format, Long> timeInFormat) {
        Long time = timeInFormat.get(format);
        time = time == null ? 0 : time;
        timeInFormat.put(format, time + playingTime);
    }
}
