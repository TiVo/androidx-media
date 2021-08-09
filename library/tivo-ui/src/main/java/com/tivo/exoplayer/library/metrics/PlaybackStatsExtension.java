package com.tivo.exoplayer.library.metrics;

import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import static com.google.android.exoplayer2.analytics.PlaybackStats.PLAYBACK_STATE_PLAYING;
import static com.google.android.exoplayer2.analytics.PlaybackStats.EventTimeAndFormat;
import static com.google.android.exoplayer2.analytics.PlaybackStats.EventTimeAndPlaybackState;

/**
 * Add-ons to the ExoPlayer {@link PlaybackStats} class.
 *
 * TODO - make a pull request and incorporate these into dev-v2 Google ExoPlayer
 */
public class PlaybackStatsExtension {


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
    static Map<Format, Long> getPlayingTimeInVideoFormat(PlaybackStats stats, long toEndTime) {
        return getPlayingTimeInAnyFormat(stats, toEndTime, stats.videoFormatHistory.listIterator());
    }

    /**
     * Using the audio format history in the {@link PlaybackStats}, determine the total amount of time
     * played in the format.  Note this is used for Audio Only playback, which at this time should only
     * have a single format for the entire playback session.
     *
     * @param stats PlaybackStats to analyze and get formats
     * @param toEndTime if not C.TIME_UNSET, stop at this time not waiting for end event
     * @return a Map, keyed by each Format (Variant) with the time in ms spent "playing" (includes paused, buffering)
     */
    static Map<Format, Long> getPlayingTimeInAudioOnlyFormat(PlaybackStats stats, long toEndTime) {
        return getPlayingTimeInAnyFormat(stats, toEndTime, stats.audioFormatHistory.listIterator());
    }


    /**
     * If their are any played video {@link Format}s in the {@link PlaybackStats} returns true
     *
     * @param stats PlaybackStats to analyze and get formats
     * @return true if there are video Format's that played
     */
    static boolean hasVideoFormats(PlaybackStats stats) {
        return stats.videoFormatHistory.isEmpty();
    }

    /**
     * If their are any played audio only {@link Format}s in the {@link PlaybackStats} returns true
     * note, it is expected this is only true for a session that is audio only, in which case
     * {@link #hasVideoFormats(PlaybackStats)} will return false.
     *
     * @param stats PlaybackStats to analyze and get formats
     * @return true if there are audio only Format's that played
     */
    static boolean hasAudioFormats(PlaybackStats stats) {
        return stats.audioFormatHistory.isEmpty();
    }

    private static HashMap<Format, Long> getPlayingTimeInAnyFormat(PlaybackStats stats, long toEndTime, ListIterator<EventTimeAndFormat> formats) {
        HashMap<Format, Long> timeInFormat = new HashMap<>();
        ListIterator<EventTimeAndPlaybackState> states = stats.playbackStateHistory.listIterator();

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
