package com.tivo.exoplayer.library.metrics;

import android.annotation.SuppressLint;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import java.util.HashMap;
import java.util.Map;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Log;
import com.tivo.exoplayer.library.util.LoggingUtils;
import static com.google.android.exoplayer2.analytics.PlaybackStats.PLAYBACK_STATE_ABANDONED;
import static com.google.android.exoplayer2.analytics.PlaybackStats.PLAYBACK_STATE_BUFFERING;
import static com.google.android.exoplayer2.analytics.PlaybackStats.PLAYBACK_STATE_ENDED;
import static com.google.android.exoplayer2.analytics.PlaybackStats.PLAYBACK_STATE_FAILED;
import static com.google.android.exoplayer2.analytics.PlaybackStats.PLAYBACK_STATE_INTERRUPTED_BY_AD;
import static com.google.android.exoplayer2.analytics.PlaybackStats.PLAYBACK_STATE_JOINING_BACKGROUND;
import static com.google.android.exoplayer2.analytics.PlaybackStats.PLAYBACK_STATE_JOINING_FOREGROUND;
import static com.google.android.exoplayer2.analytics.PlaybackStats.PLAYBACK_STATE_NOT_STARTED;
import static com.google.android.exoplayer2.analytics.PlaybackStats.PLAYBACK_STATE_PAUSED;
import static com.google.android.exoplayer2.analytics.PlaybackStats.PLAYBACK_STATE_PAUSED_BUFFERING;
import static com.google.android.exoplayer2.analytics.PlaybackStats.PLAYBACK_STATE_PLAYING;
import static com.google.android.exoplayer2.analytics.PlaybackStats.PLAYBACK_STATE_SEEKING;
import static com.google.android.exoplayer2.analytics.PlaybackStats.PLAYBACK_STATE_STOPPED;
import static com.google.android.exoplayer2.analytics.PlaybackStats.PLAYBACK_STATE_SUPPRESSED;
import static com.google.android.exoplayer2.analytics.PlaybackStats.PLAYBACK_STATE_SUPPRESSED_BUFFERING;

public abstract class AbstractBasePlaybackMetrics {
    private static final String TAG = "PlaybackMetrics";
    protected long startingTimestamp;
    private @Nullable
    AbstractBasePlaybackMetrics previousMetrics;
    private int profileShiftCount;
    private Map<Format, Long> timeInVideoFormat;
    private long droppedFramesCount;
    private float avgVideoBitrate;
    private float avgAudioBitrate;
    private float avgNetworkBitrateMbps;
    private long totalElapsedTimeMs;
    private long totalRebufferingTime;
    private float rebufferCount;
    private long initialPlaybackStartDelay;
    private Exception endedWithError;
    protected CurrentState currentState = CurrentState.UNKNOWN;
    private int totalSeekCount;
    private long totalSeekTime;
    protected final DecoderCounters cumulativeVideoDecoderCounters = new DecoderCounters();


    /**
     * Called by the {@link ManagePlaybackMetrics} to initialize a new set of PlaybackMetrics.
     * <p>
     * This call is used to manage metric values that must be "reset" on each event, so with this call, these per-event
     * values can be snapshot and just the deltas reported.
     *
     * @param currentElapsedTime - long time of creation (from {@link Clock#elapsedRealtime()}
     * @param previousMetrics    - the previous active metrics, or null if none
     */
    void initialize(long currentElapsedTime, @Nullable AbstractBasePlaybackMetrics previousMetrics) {
        startingTimestamp = currentElapsedTime;
        this.previousMetrics = previousMetrics;
    }

    /**
     * Updates the metrics values from the PlaybackStats since the last initialize call. After this call this set of
     * PlaybackMetrics will cover the range from the last call to this method or {@link #initialize(long,
     * AbstractBasePlaybackMetrics)}
     *
     * @param playbackStats      used to compute the metric values
     * @param currentElapsedTime current {@link Clock#elapsedRealtime()}
     * @param currentCounters   DecoderCounters from the player at the point of the update.
     * @return the final PlaybackStats @PlaybackState
     */
    @SuppressLint("SwitchIntDef")
    int updateValuesFromStats(PlaybackStats playbackStats, long currentElapsedTime, DecoderCounters currentCounters) {
        // TODO compute values
        startingTimestamp = currentElapsedTime;

        totalElapsedTimeMs = playbackStats.getTotalElapsedTimeMs();
        timeInVideoFormat = PlaybackStatsExtension.getPlayingTimeInVideoFormat(playbackStats, currentElapsedTime);
        avgVideoBitrate = bpsToMbps(playbackStats.getMeanVideoFormatBitrate());
        avgAudioBitrate = bpsToMbps(playbackStats.getMeanAudioFormatBitrate());
        avgNetworkBitrateMbps = bpsToMbps(playbackStats.getMeanBandwidth());
        profileShiftCount = playbackStats.videoFormatHistory.size();
        droppedFramesCount = playbackStats.totalDroppedFrames;
        totalRebufferingTime = playbackStats.getTotalRebufferTimeMs();
        rebufferCount = playbackStats.getMeanRebufferCount();
        initialPlaybackStartDelay = playbackStats.totalValidJoinTimeMs;
        totalSeekTime = playbackStats.getTotalSeekTimeMs();
        totalSeekCount = playbackStats.totalSeekCount;

        if (playbackStats.fatalErrorHistory.size() > 0) {
            endedWithError = playbackStats.fatalErrorHistory.get(0).exception;
        } else {
            endedWithError = null;
        }
        int playbackStateAtTime = playbackStats.getPlaybackStateAtTime(currentElapsedTime);


        switch (playbackStateAtTime) {
            case PLAYBACK_STATE_NOT_STARTED:
                currentState = CurrentState.UNKNOWN;
                break;

            case PLAYBACK_STATE_JOINING_BACKGROUND:
            case PLAYBACK_STATE_JOINING_FOREGROUND:
                currentState = CurrentState.PLAYBACK_STARTING;
                break;

            case PLAYBACK_STATE_PLAYING:
            case PLAYBACK_STATE_PAUSED:
            case PLAYBACK_STATE_SEEKING:
                currentState = CurrentState.PLAYBACK_STARTED;
                break;

            case PLAYBACK_STATE_BUFFERING:
            case PLAYBACK_STATE_PAUSED_BUFFERING:
            case PLAYBACK_STATE_SUPPRESSED:
            case PLAYBACK_STATE_SUPPRESSED_BUFFERING:
                currentState = CurrentState.BUFFERING;
                break;

            case PLAYBACK_STATE_FAILED:
                currentState = CurrentState.FAILED;
                break;

            case PLAYBACK_STATE_ENDED:
            case PLAYBACK_STATE_STOPPED:
            case PLAYBACK_STATE_ABANDONED:
                currentState = CurrentState.DONE;
                break;

            case PLAYBACK_STATE_INTERRUPTED_BY_AD:      // Not posssible
                break;
        }

        if (endedWithError != null) {
            Log.d(TAG, "updateValuesFromStats() - playing, time: " + currentElapsedTime + " currentState: " + currentState + " from playbackState: " + playbackStateAtTime);
        } else {
            Log.d(TAG, "updateValuesFromStats() - ended, time: " + currentElapsedTime + " endedWithError: " + endedWithError + " endState: " + playbackStateAtTime);
        }
        return playbackStateAtTime;
    }

    private float bpsToMbps(int meanBitrate) {
        return (float) ((float) meanBitrate / 1_000_000.0);
    }

    public Map<String, Object> getMetricsAsMap() {
        Map<String, Object> loggedStats = new HashMap<>();
        loggedStats.put("initialPlaybackStartDelay", getInitialPlaybackStartDelay());
        loggedStats.put("totalElapsedTimeMs", getTotalElapsedTimeMs());
        loggedStats.put("rebufferCount", getRebufferCount());
        loggedStats.put("totalSeekTime", getTotalSeekTimeMs());
        loggedStats.put("totalSeekCount", getTotalSeekCount());
        loggedStats.put("profileShiftCount", getProfileShiftCount());
        loggedStats.put("avgVideoBitrate", getAvgVideoBitrate());
        loggedStats.put("avgAudioBitrate", getAvgAudioBitrate());
        loggedStats.put("avgBandwidthMbps", getAvgNetworkBitrate());
        loggedStats.put("videoFramesDropped", getVideoFramesDropped());

        if (getEndedWithError() != null) {
            loggedStats.put("playbackError", getEndedWithError().toString());
        }

        Map<Format, Long> timeInFormat = getTimeInVideoFormat();
        if (timeInFormat != null) {
            Map<String, Long> timeInVariant = new HashMap<>();

            for (Map.Entry<Format, Long> entry : timeInFormat.entrySet()) {
                timeInVariant.put(LoggingUtils.getVideoLevelStr(entry.getKey()), entry.getValue());
            }
            loggedStats.put("timeInFormats", timeInVariant);
        }

        return loggedStats;
    }

    /**
     * Number of number of decoded video frames dropped.  Frames are dropped because they are decoded to late (more than
     * 30ms past the current render time) for their time to render.
     *
     * @return count of dropped frames since the session began
     */
    public long getVideoFramesDropped() {
        return droppedFramesCount;
    }

    /**
     * The Average Video bitrate is the computed from the {@link Format#bitrate}, which is the AVERAGE_BANDWIDTH for an
     * HLS variant where available, or BANDWIDTH (the peak).  This is more sustinctly the varaints bitrate (as it
     * includes all playable renditions (audio, video and captions).
     * <p>
     * The average computation is a weighted average, taking the time the format was used for playback (that is
     * eliminating paused and buffering times) into account.
     * <p>
     * TODO - legacy calls this a "video" bitrate, but it is all muxed media for the variant
     *
     * @return average video bitrate, in mbps, since the session began
     * or C.LENGTH_UNSET if there is no video variant or no video has played.
     */
    public float getAvgVideoBitrate() {
        return avgVideoBitrate;
    }

    /**
     * Average audio bitrate in mbps
     *
     * @return average audio bitrate, in mbps, since the session began
     * or C.LENGTH_UNSET if there is no audio data or the audio bitrate is unknown
     */
    public float getAvgAudioBitrate() {
        return avgAudioBitrate;
    }

    /**
     * Returns the average (mean) network bandwidth based on from measurements of media transfers for all the media that
     * comprises the current playing streams.   This values is smoothed by the player's bandwidth meter to use in ABR
     * selection of the streams.
     * <p>
     * This tracks {@link #getAvgVideoBitrate()} as when this values increases, the ABR should select a higher variant
     * bitrate
     *
     * @return network bandwidth observed over the session in Mbps
     */
    public float getAvgNetworkBitrate() {
        return avgNetworkBitrateMbps;
    }

    /**
     * Total time in all playback states
     *
     * @return total time in ms
     */
    public long getTotalElapsedTimeMs() {
        return totalElapsedTimeMs;
    }

    /**
     * Number of times playback paused for re-buffering (buffered media duration less than threshold).  Note this
     * excludes buffering for:
     *
     * <ol>
     *     <li>Initial playback startup</li>
     *     <li>Restarting playback after a seek</li>
     *     <li>Buffering during trickplay</li>
     * </ol>
     *
     * @return count of rebufferings
     */
    public float getRebufferCount() {
        return rebufferCount;
    }

    /**
     * Total time (in ms) spent rebuffering.  That is buffering when it is not for initial join times, times after a
     * seek and buffering while paused.  See {@link #getRebufferCount()}.
     * <p>
     * This is equivalent to QoE spec metric called "videoStallingTime".
     * </p>
     * <p>
     * This total / rebuffingCount would give you the average re-buffering time
     *
     * @return total re-buffering time in ms
     */
    public long getTotalRebufferingTime() {
        return totalRebufferingTime;
    }

    /**
     * Total number of "seek" operations issued.
     *
     * Note for VTP reverse this is the seeks used to move in the reverse direction, a
     * seek is productive if it results in a frame render.  So the ratio of {@link TrickPlayMetrics#getRenderedFramesCount()} /
     * {@link #getTotalSeekCount()} is the productivity of the "visual" aspect of reverse VTP.
     *
     * For forward VTP this number is only non-zero if the advance key is used to jump.
     *
     * @return number of seek operations
     */
    public int getTotalSeekCount() {
        return totalSeekCount;
    }

    /**
     * Contrast this with re-buffering (aka "stalling" in QoE spec), this value gives the total time the
     * player is in {@link Player#STATE_BUFFERING} that:
     * <ul>
     *   <li>Directly following a position change (discontinuity), either seek or trickplay</li>
     *   <li>Not directly following playback startup (channel change, play new asset)</li>
     * </ul>
     *
     * Use this value to derive the QoE <em>videoBufferingTime</em> value, e.g.
     *   videoBufferingTime = totalSeekTime + initialPlaybackStartDelay
     *
     * {@link #getInitialPlaybackStartDelay()}
     *
     * Note for the {@link TrickPlayMetrics} subclass this value includes the buffering for the
     * repeated seek operations followed by waiting for a short amount of time for a rendered frame.
     *
     * @return total time (in ms) spent buffering following a seek, trickplay or other user induced position change
     */
    public long getTotalSeekTimeMs() {
        return totalSeekTime;
    }

    /**
     * Returns the number of times the current playing video format changed.
     *
     * @return number of video profile changes since the session began
     */
    public int getProfileShiftCount() {
        return profileShiftCount;
    }

    /**
     * Return a Map with the total time spent in each of the {@link Format}'s.  Note, the base for this in
     * {@link PlaybackStats} allows for null formats (periods when no video is playing, contain a null format)
     *
     * @return Map of video {@link Format} objects with the total time (including not playing) in each, null if no stats
     * captured
     */
    public @Nullable
    Map<Format, Long> getTimeInVideoFormat() {
        return timeInVideoFormat;
    }

    /**
     * The total time spent initially buffering to start the playback, in milliseconds, or {@link C#TIME_UNSET} if no
     * valid time could be determined.
     * <p>
     * This value uses {@link PlaybackStats#totalValidJoinTimeMs} so it is valid for a single "join" as we do not
     * background the player while stats collection is active.
     *
     * @return start delay in milliseconds
     */
    public long getInitialPlaybackStartDelay() {
        return initialPlaybackStartDelay;
    }

    /**
     * If playback ended with and error this will be non-null and have the fatal exception that ended playback.
     *
     * @return the {@link ExoPlaybackException} that caused playback to stop, or null if there was no error
     */
    public @Nullable Exception getEndedWithError() {
        return endedWithError;
    }

    /**
     * Merge in the current{@link DecoderCounters} values into our running set.
     *
     * This method is called before the video decoder creates a fresh {@link DecoderCounters} object,
     * this happens during decoder disable()/enable().
     *
     * @param curr The current {@link DecoderCounters} for video
     */
    void captureVideoDecoderCountersSnapshot(DecoderCounters curr) {
        cumulativeVideoDecoderCounters.merge(curr);
        Log.d(TAG, "captureVideoDecoderCountersSnapshot, renderedFramesCount: " + cumulativeVideoDecoderCounters.renderedOutputBufferCount +
            " after capturing snapshot renderedOutputBufferCount: " + curr.renderedOutputBufferCount );
    }

    protected enum CurrentState {
        UNKNOWN,
        /**
         * No state yet recorded
         */
        PLAYBACK_STARTING,
        /**
         * Awaiting first buffering complete.
         */
        PLAYBACK_STARTED,
        /**
         * Currently playing (includes seek, pause, etc)
         */
        BUFFERING,
        FAILED,
        DONE
    }
}
