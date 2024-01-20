package com.google.android.exoplayer2.trickplay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistParserFactory;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParserFactory;
import com.google.android.exoplayer2.trickplay.hls.DualModeHlsPlaylistParserFactory;
import com.google.android.exoplayer2.trickplay.hls.FrameRateAnalyzer;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Log;

/**
 * Implements simple trick-play for video fast forward / reverse without sound.
 *
 * Fast modes are three different rates in each direction including normal playback (which re-enables sound)
 */
class TrickPlayController implements TrickPlayControlInternal {

    private static final String TAG = "TrickPlayController";

    private static final int REACTION_TIME_FOR_OVERSHOOT = 250;       // ms jump for FR/FF reaction time correction
    private static final int TUNNELING_MODE_SAFE_OFFSET_MS = 6000;

    private @MonotonicNonNull SimpleExoPlayer player;
    private final DefaultTrackSelector trackSelector;
    private DefaultTrackSelector.Parameters savedParameters;

    private final CopyOnWriteArraySet<ListenerRef> listeners;

    private TrickMode currentTrickMode = TrickMode.NORMAL;
    private @Nullable PlayerEventListener playerEventListener;
    private int lastSelectedAudioTrack = -1;

    private SeekBasedTrickPlay currentHandler = null;

    private ScrubTrickPlay scrubTrickPlay = null;

    private Map<TrickMode, Float> speedsForMode;

    private boolean isSmoothPlayAvailable = false;
    private boolean isMetadataValid = false;

    /** To dispatch events on the Players main handler thread
     */
    private Handler playbackHandler;

    /** To dispatch events on the appliation thread that created the ExoPlayer and TrickPlayControl
     */
    private Handler applicatonHandler;

    /** Keep track of the last N rendered frame PTS values for jumpback seek
     */
    private final LastNPositions lastRenderPositions = new LastNPositions();
    private boolean playbackSpeedForwardTrickPlayEnabled = true;

    /** Created by the DualModeHlsPlaylistParserFactory, used to resolve the frame rate for an iFrame variant
     */
    @Nullable private FrameRateAnalyzer frameRateAnalyzer;

    /**
     * On exit from any {@link TrickMode} to {@link TrickMode#NORMAL} this contains the mode we are
     * exiting. After exit completes it is set to NORMAL, implies no exit in progress
     */
    private @NonNull TrickMode exitingTrickMode = TrickMode.NORMAL;

    TrickPlayController(DefaultTrackSelector trackSelector) {
        this.trackSelector = trackSelector;
        this.listeners = new CopyOnWriteArraySet<>();

        // Default (for now) assume iFrames are available.
        setSpeedsForMode(true);

        addEventListener(new TrickPlayEventListener() {
            @Override
            public void playlistMetadataValid(boolean isMetadataValid) {
                if (isMetadataValid) {
                    setSpeedsForMode(isSmoothPlayAvailable());
                }
            }
        });
    }

    /**
     * Keeps trick play event listener and context to call it with
     */
    private static final class ListenerRef {
        enum CallType {PLAYER, APPLICATION};

        final CallType callType;
        final TrickPlayEventListener listener;

        ListenerRef(CallType callType, TrickPlayEventListener listener) {
            this.callType = callType;
            this.listener = listener;
        }
    }

    /**
     * Keeps record of the program time of the last N rendered frames in trickplay mode.
     * Returns this list, converted to playback positions
     *
     * NOTE this class is access by both the Application and ExoPlayer playback threads
     */
    private class LastNPositions {
        public static final int LAST_RENDER_POSITIONS_MAX = 15;

        private long[] store;
        private int lastWriteIndex = -1;

        LastNPositions(int size) {
            this.store = new long[size];
            Arrays.fill(this.store, C.TIME_UNSET);
        }

        public LastNPositions() {
            this(LAST_RENDER_POSITIONS_MAX);
        }

        /**
         * Add a rendered time stamp, this is called from the player's thread
         *
         * @param value - render program time of the frame
         */
        synchronized void add(long value) {
            lastWriteIndex = (lastWriteIndex + 1) % store.length;
            store[lastWriteIndex] = value;
        }

        synchronized private List<Long> getLastNRenderPositionsUs() {
            ArrayList<Long> values = new ArrayList<>();
            if (lastWriteIndex >= 0) {
                int readIndex = lastWriteIndex;
                while (values.size() < store.length && store[readIndex] != C.TIME_UNSET) {
                    values.add(store[readIndex]);
                    readIndex--;
                    readIndex = readIndex < 0 ? store.length - 1 : readIndex;
                }
            }
            return values;
        }

        synchronized int getSavedRenderPositionsCount() {
            int count = 0;
            for (long value : store) {
                count += (value != C.TIME_UNSET) ? 1 : 0;
            }
            return count;
        }

        synchronized void empty() {
            store = new long[store.length];
            Arrays.fill(this.store, C.TIME_UNSET);
            lastWriteIndex = -1;
        }

        /**
         * Creates a list, in reverse rendered order of the last frame renders converted from
         * program time to time offsets within the current window, in ms.  The values are suitable
         * for calling {@see SimpleExoPlayer#seekTo}.
         *
         * @return list of time player time values, in ms, normalized to the period time
         */
         List<Long> lastNPositions() {
            Timeline timeline = player.getCurrentTimeline();

            List<Long> values = Collections.emptyList();

            if (timeline.isEmpty()) {
                Log.w(TAG, "trickplay stopped with empty timeline, should not be possible");
            } else {
                Timeline.Window window = timeline.getWindow(player.getCurrentWindowIndex(), new Timeline.Window());
                List<Long> lastNRenders = getLastNRenderPositionsUs();

                ListIterator<Long> iter = lastNRenders.listIterator();
                while (iter.hasNext()) {
                    int index = iter.nextIndex();
                    Long renderTimeUs = iter.next();
                    lastNRenders.set(index, C.usToMs(renderTimeUs - window.positionInFirstPeriodUs));
                }

                values = lastNRenders;
            }

            return values;
        }

    }

    /**
     * Listens to Analytics events and run a periodic timer (Handler) to monitor playback
     * for managing TrickPlay.  This includes, but is not limited to:
     * <ul>
     *   <li>Listening to track-selection events in order to determine if trick play (IFrame) tracks are present</li>
     *   <li>Managing trick-play early exit when timeline boundaries are reached.</li>
     * </ul>
     *
     */
    @SuppressLint("HandlerLeak")
    private class PlayerEventListener extends Handler implements AnalyticsListener {

        /**
         * Handle player state changes for trickplay.  Best documentation of the states is
         * in <a href="https://exoplayer.dev/listening-to-player-events.html">Player Events</a>
         *
         * <ul>
         *     <li>STATE_IDLE - this is the initial state or for any error in playback, here we
         *                      invalidate the trickplay metadata (as the playlist could have changed)
         *                      and end any trickplay in progress</li>
         *     <li>STATE_ENDED - The player has reached the end of the playlist, if we are in any
         *                      trickplay (that is not mode is not NORMAL or SCRUB), we end trickplay.
         *     <li>STATE_BUFFERING/READY - check if we have exceeded the normal timeline window (possible
         *                      if the i-Frame only playlist is shorter than the regular playlist) and
         *                      if so exit trickplay.
         * </ul>
         *
         *
         * @param eventTime The event time.
         * @param playbackState The new {@link Player.State playback state}.
         */
        @Override
        public void onPlaybackStateChanged(EventTime eventTime, int playbackState) {

            switch(playbackState) {
                case Player.STATE_IDLE:
                    isMetadataValid = false;
                    resetTrickPlayState(true);
                    dispatchPlaylistMetadataChanged();

                case Player.STATE_ENDED:
                    TrickMode currentTrickMode = getCurrentTrickMode();
                    switch(currentTrickMode) {
                        case NORMAL:
                        case SCRUB:
                            Log.d(TAG, "Reached playlist end in mode: " + currentTrickMode + ", staying in mode.");
                            break;

                        default:
                            Log.d(TAG, "Reached playlist end in mode: " + currentTrickMode + ", stopping trickplay.");
                            resetTrickPlayState(true);
                            break;
                    }
                    break;

                case Player.STATE_READY:
                case Player.STATE_BUFFERING:
                    exitTrickPlayIfTimelineExceeded(eventTime.timeline);
                    break;
            }
        }

        @Override
        public void onTimelineChanged(EventTime eventTime, @Player.TimelineChangeReason int reason) {
            switch (reason) {
                case Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED:        // TODO - VTP should have exited ?
                    break;
                case Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE:
                    Timeline timeline = eventTime.timeline;
                    exitTrickPlayIfTimelineExceeded(timeline);
                    break;
            }
        }

        @Override
        public void onTracksChanged(EventTime eventTime, TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
            isMetadataValid = true;

            if (getCurrentTrickMode() != TrickMode.NORMAL) {
                sendEmptyMessageDelayed(0, 1000);
            }

            // Each related set of playlists in the master (for HLS at least) creates a TrackGroup in
            // the TrackGroupArray.  The Format's within that group are the adaptations of the playlist.
            //
            // Look for the video trackgroup, if there is one see if it has any trickplay format.
            //
            for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
                TrackGroup group = trackGroups.get(groupIndex);
                boolean groupIsVideo = group.length > 0 && isVideoFormat(group.getFormat(0));

                if (groupIsVideo) {
                    boolean foundIframeOnlyTracks = false;
                    for (int trackIndex = 0; trackIndex < group.length && ! foundIframeOnlyTracks; trackIndex++) {
                        Format format = group.getFormat(trackIndex);
                        foundIframeOnlyTracks = (format.roleFlags & C.ROLE_FLAG_TRICK_PLAY) != 0;
                    }
                    synchronized (this) {
                        isSmoothPlayAvailable = foundIframeOnlyTracks;
                    }
                }
            }

            dispatchPlaylistMetadataChanged();

            TrackSelectionArray selections = player.getCurrentTrackSelections();
            for (int i = 0; i < selections.length; i++) {
              if (player.getRendererType(i) == C.TRACK_TYPE_AUDIO && selections.get(i) != null && lastSelectedAudioTrack == -1) {
                lastSelectedAudioTrack = i;
              }
            }

            seekOnTrickPlayExitToLastRenderedFrameN();
        }

        /**
         * Listen for seeks.  When a seek occurs while trick-play is active the seek position
         * may not yet be rendered, this makes the list of last rendered
         * positions {@link #lastRenderPositions} no longer relevant.
         *
         * Clear the list, subsequent renders and continued trick-play from the seek position
         * will fill the list again.  (See Jira issue BZSTREAM-5732)
         *
         * @param eventTime The event time.
         * @param oldPosition The position before the discontinuity.
         * @param newPosition The position after the discontinuity.
         * @param reason The reason for the position discontinuity.
         */
        @Override
        public void onPositionDiscontinuity(
            @NonNull EventTime eventTime,
            @NonNull Player.PositionInfo oldPosition,
            @NonNull Player.PositionInfo newPosition,
            @Player.DiscontinuityReason int reason) {
            if (currentTrickMode != TrickMode.NORMAL && reason == Player.DISCONTINUITY_REASON_SEEK) {
                lastRenderPositions.empty();
            }
        }

        @Override
        public void handleMessage(Message msg) {
            if (currentTrickMode != TrickMode.NORMAL && player != null) {
                Timeline timeline = player.getCurrentTimeline();
                exitTrickPlayIfTimelineExceeded(timeline);
                sendEmptyMessageDelayed(0, 1000);
            }
        }
    }

    /**
     * Manages sequence of seeks to simulate trick-play when it is not possible to use the
     * {@link com.google.android.exoplayer2.Renderer} clock ({@link com.google.android.exoplayer2.util.MediaClock}
     * That is when:
     * <ol>
     *   <li>Playback direction (reverse) is not supported by the clock</li>
     *   <li>Speed exceeds what is possible without a lower frame rate track (iFrames)</li>
     * </ol>
     *
     * The basic algorithm is to issue repeated seeks, timed to achieve a target frame rate, but
     * throttled back (by issuing the seeks less frequently) if a minimum frame rate is not realized.
     *
     * This way {@link Player#getCurrentPosition()} and the UI rendering of it (time bar) move at the
     * expected rate (that is matching the requested speed) while the player makes a best effort to
     * show visual progress with rendered frames.
     *
     * Two parameters set the alogorithm behavior:
     * <ol>
     *   <li>DEFAULT_SEEK_INTERVAL_MS - seeks are issued at this rate, as long as render intervals meet target</li>
     *   <li>THROTTLED_SEEK_INTERVAL_MS - if there is not a render during the targetFrameInterval, switch down to this until there is</li>
     * </ol>
     *
     */
    private static class SeekBasedTrickPlay extends Handler implements AnalyticsListener, TrickPlayEventListener {
        private static final String TAG = "SeekBasedTrickPlay";

        // Causes the handler to issue an initial seek to the position indicated by the clock
        static final int MSG_TRICKPLAY_STARTSEEK = 1;

        // Seek issued for seeks after the initial start, these are throtted to achive a frame rate
        static final int MSG_TRICKPLAY_TIMED_SEEK = 2;

        // FPS target, this is the render rate we hope for
        static final int TP1_TARGET_FPS = 3;
        static final int TP2_TARGET_FPS = 4;
        static final int TP3_TARGET_FPS = 5;
        static int targetFrameIntervalMs = 1000 / TP3_TARGET_FPS;

        private ScrubTrickPlay scrubTrickPlay;

        private long lastIssuedSeekTimeMs = C.TIME_UNSET;
        private final ReversibleMediaClock currentMediaClock;
        private int currentSeekIntervalMs = targetFrameIntervalMs;
        private boolean trickPlayStarted;
        private final @NonNull TrickPlayController trickPlayController;
        private final @NonNull SimpleExoPlayer player;

        SeekBasedTrickPlay(TrickPlayController trickPlayController, SimpleExoPlayer player) {
            this.trickPlayController = trickPlayController;
            this.player = player;
            boolean isForward = trickPlayController.getCurrentTrickDirection() == TrickPlayDirection.FORWARD;
            long startingPosition = player.getCurrentPosition();
            Log.d(TAG, "Create SeekBasedTrickPlay handler - initial mode: " + trickPlayController.getCurrentTrickMode() + " start at: " + startingPosition);
            currentMediaClock = new ReversibleMediaClock(isForward, C.msToUs(startingPosition), Clock.DEFAULT);
            scrubTrickPlay = new ScrubTrickPlay(player, trickPlayController);
            scrubTrickPlay.scrubStart();
            TrickMode currentTrickMode = trickPlayController.getCurrentTrickMode();
            updateTrickMode(currentTrickMode);
        }

        private void updateTrickMode(TrickMode currentTrickMode) {
            boolean isForward = trickPlayController.getCurrentTrickDirection() == TrickPlayDirection.FORWARD;
            float currentSpeed = trickPlayController.getSpeedFor(currentTrickMode);
            PlaybackParameters playbackParameters = new PlaybackParameters(Math.abs(currentSpeed));
            currentMediaClock.setPlaybackParameters(playbackParameters);
            currentMediaClock.setForward(isForward);

            switch (currentTrickMode) {
                case FF1:
                case FR1:
                    targetFrameIntervalMs = 1000 / TP1_TARGET_FPS;
                    break;
                case FF2:
                case FR2:
                    targetFrameIntervalMs = 1000 / TP2_TARGET_FPS;
                    break;
                case FF3:
                case FR3:
                    targetFrameIntervalMs = 1000 / TP3_TARGET_FPS;
                    break;
                default:
                    targetFrameIntervalMs = 1000 / TP3_TARGET_FPS;
                    break;
            }
            currentSeekIntervalMs = targetFrameIntervalMs;
            Log.d(TAG, "updateTrickMode() - to: " + currentTrickMode + " current pos: " + currentMediaClock.getPositionUs());
        }

        @Override
        public void handleMessage(Message msg) {
            if (trickPlayStarted) {
                TrickMode currentTrickMode = trickPlayController.getCurrentTrickMode();

                /* if time to exit trick-play reached seek boundry, then switch to normal and discard this last message */
                boolean didExitTrickPlay = trickPlayController.exitTrickPlayIfTimelineExceeded(player.getCurrentTimeline());
                if (didExitTrickPlay) {
                    Log.d(TAG, "End seek-based trickplay, reached seek boundry.  mode: " + currentTrickMode + " at media time " + player.getCurrentPosition());
                } else if (currentTrickMode != TrickMode.NORMAL) {
                    processTrickplayMessage(msg);
                }
            } else {
                Log.d(TAG, "stale handleMessage("+msg.what+") - discarded, seek trickplay stopped, currentMode: " + trickPlayController.getCurrentTrickMode());
            }
        }

        private void processTrickplayMessage(Message msg) {

            switch (msg.what) {
                case MSG_TRICKPLAY_STARTSEEK:
                    Log.d(TAG, "msg STARTSEEK - calling seekToMediaClock() - targetFrameIntervalMs: " + targetFrameIntervalMs);
                    currentSeekIntervalMs = targetFrameIntervalMs;
                    sendEmptyMessageDelayed(MSG_TRICKPLAY_TIMED_SEEK, currentSeekIntervalMs);
                    Log.d(TAG, "msg STARTSEEK - set next seek time, currentSeekIntervalMs: " + currentSeekIntervalMs);
                    break;

                case MSG_TRICKPLAY_TIMED_SEEK:
                    Log.d(TAG, "msg TIMED_SEEK - next seek in: " + currentSeekIntervalMs +"ms");
                    seekToMediaClock();
                    sendEmptyMessageDelayed(MSG_TRICKPLAY_TIMED_SEEK, currentSeekIntervalMs);
            }
        }

        /**
         * Issue the seek request to the {@link ScrubTrickPlay}. The seek value is based on the current media
         * time (playback position) reported by the {@link #currentMediaClock}.
         *
         * The actual final seek position is adjusted by ExoPlayer to match to nearest sync (IDR) based on
         * the seek direction. Because of this it is possible the player position may be slightly ahead of or behind the requested
         * position. In the cases this position error results in moving the wrong direction do not issue the seek.
         *
         * @return true if seek was issued
         */
        private boolean seekToMediaClock() {
            boolean issuedSeek;
            long largestSafeSeekPositionMs = trickPlayController.getLargestSafeSeekPositionMs();
            long seekTargetMs = C.usToMs(currentMediaClock.getPositionUs());
            seekTargetMs = Math.min(seekTargetMs, largestSafeSeekPositionMs);
            long currentPositionMs = player.getCurrentPosition();
            TrickPlayDirection direction = trickPlayController.getCurrentTrickDirection();
            long deltaMs = seekTargetMs - currentPositionMs;
            if (direction == TrickPlayDirection.FORWARD && deltaMs <= 0
                || direction == TrickPlayDirection.REVERSE && deltaMs >= 0) {
                issuedSeek = false;
                Log.d(TAG, "seekToMediaClock() - skipping, seekTarget: " + seekTargetMs
                    + " delta to position (" + deltaMs +") moves in wrong direction for mode: " + direction);
            } else {
                lastIssuedSeekTimeMs = seekTargetMs;
                long timeLastSeek = scrubTrickPlay.getTimeSinceLastSeekIssued();
                boolean shouldForceSeek = timeLastSeek != C.TIME_UNSET && timeLastSeek > 8 * targetFrameIntervalMs;
                Log.d(TAG, "seekToMediaClock() - issuing, seekTarget: " + seekTargetMs / 1000.0f + debugTimeStr() + " forced: " + shouldForceSeek);
                issuedSeek = scrubTrickPlay.scrubSeek(seekTargetMs, shouldForceSeek);
            }
            return issuedSeek;
        }

        private String debugTimeStr() {
            long currentPositionMs = player.getCurrentPosition();
            long clockPositionUs = currentMediaClock.getPositionUs();
            return String.format(Locale.getDefault(), " [position: %.3f clock %.3f delta %d(ms)] ",
                currentPositionMs / 1000.0f, clockPositionUs / 1_000_000.0f, C.usToMs(clockPositionUs) - currentPositionMs);
        }

        /**
         * Start seek base trick-play, set the initial conditions and issue the first seek.
         *
         * Expects current trickmode is not {@link TrickMode#NORMAL}
         */
        private void startTrickPlay() {
            Log.d(TAG, "starting seek trick-play - mode " + trickPlayController.currentTrickMode + " current pos: " + currentMediaClock.getPositionUs());
            trickPlayStarted = true;
            sendEmptyMessage(MSG_TRICKPLAY_STARTSEEK);
            player.addAnalyticsListener(this);
            player.setPlaybackParameters(new PlaybackParameters(0.1f));
            if (trickPlayController.isSmoothPlayAvailable()) {
                trickPlayController.addEventListener(this);
            }
        }

        private void stopTrickPlay() {
            Log.d(TAG, "stop seek trick-play - mode " + trickPlayController.currentTrickMode + " current pos: " + currentMediaClock.getPositionUs());
            player.removeAnalyticsListener(this);
            trickPlayController.removeEventListener(this);
            trickPlayStarted = false;
            removeCallbacksAndMessages(null);
            scrubTrickPlay.scrubStop();
        }

        // Implement trickplay event listener

        @Override
        public void trickPlayModeChanged(TrickMode newMode, TrickMode prevMode) {
            if (newMode != TrickMode.NORMAL) {
                Log.d(TAG, "trickPlayModeChanged() from: " + prevMode + " to: " + newMode + " current pos: " + currentMediaClock.getPositionUs());

                updateTrickMode(newMode);
            }
        }

        /**
         * Check if it's our seek (we set the lastIssuedSeekTimeMs for each one we source).  Otherwise this
         * seek was user pressed 'advance' or some other jump button.
         * @param eventTime The event time.
         * @param oldPosition The position before the discontinuity.
         * @param newPosition The position after the discontinuity.
         * @param reason The reason for the position discontinuity.
         */
        @Override
        public void onPositionDiscontinuity(
            @NonNull EventTime eventTime,
            @NonNull Player.PositionInfo oldPosition,
            @NonNull Player.PositionInfo newPosition,
            int reason) {

            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                TrickMode currentTrickMode = trickPlayController.getCurrentTrickMode();
                if (eventTime.currentPlaybackPositionMs == lastIssuedSeekTimeMs) {
                    Log.d(TAG, "onSeekProcessed() - mode:  " + currentTrickMode + debugTimeStr());
                } else {
                    Log.d(TAG, "onSeekProcessed() - reset clock for advance - " + currentTrickMode + debugTimeStr());
                    currentMediaClock.resetPosition(C.msToUs(eventTime.currentPlaybackPositionMs));
                }
            }
        }
    }

    /////
    // API methods - TrickPlayControl interface
    /////

    @Override
    public Float getSpeedFor(TrickMode mode) {
        return speedsForMode.get(mode);
    }

    @Override
    public DefaultRenderersFactory createRenderersFactory(Context context) {
        return new TrickPlayRendererFactory(context, this);
//        return new DefaultRenderersFactory(context);
    }

    @Override
    public LoadControl createLoadControl(LoadControl delegate) {
        return new AdaptiveLoadControl(this, delegate);
    }

    @Override
    public HlsPlaylistParserFactory createHlsPlaylistParserFactory(boolean useDualMode) {
        HlsPlaylistParserFactory factory = new DefaultHlsPlaylistParserFactory();
        if (useDualMode) {
            DualModeHlsPlaylistParserFactory dualModeFactory = new DualModeHlsPlaylistParserFactory(factory);
            frameRateAnalyzer = dualModeFactory.getFrameRateAnalyzer();
            factory = dualModeFactory;
        }
        return factory;
    }

    @Override
    public void setPlayer(@NonNull  SimpleExoPlayer player) {
        setCurrentTrickMode(TrickMode.NORMAL);
        if (this.player != null) {
            removePlayerReference();
        }
        this.player = player;
        playbackHandler = new Handler(player.getPlaybackLooper());
        applicatonHandler = new Handler(player.getApplicationLooper());
        playerEventListener = new PlayerEventListener();
        this.player.addAnalyticsListener(playerEventListener);
    }

    @Override
    public void removePlayerReference() {
        stopSeekBasedTrickplay();
        playbackHandler.removeCallbacksAndMessages(null);
        playbackHandler = null;
        applicatonHandler.removeCallbacksAndMessages(null);
        applicatonHandler = null;
        listeners.clear();
        if (playerEventListener != null) {
            player.removeAnalyticsListener(playerEventListener);
            playerEventListener = null;
        }
        player = null;
    }

    @Override
    public synchronized TrickMode getCurrentTrickMode() {
        return currentTrickMode;
    }

    @Override
    public TrickPlayDirection getCurrentTrickDirection() {
        return TrickPlayControl.directionForMode(getCurrentTrickMode());
    }

    @Override
    public synchronized boolean isSmoothPlayAvailable() {
        return isSmoothPlayAvailable;
    }

    @Override
    public boolean isMetadataValid() {
        return isMetadataValid;
    }

    @Override
    public int setTrickMode(TrickMode newMode) {
        TrickMode previousMode = getCurrentTrickMode();
        int lastRendersCount = -1;

        // TODO disallow transition from forward to reverse without first normal

        if (newMode != previousMode) {
            boolean modeChangePossible = canTrickPlayInMode(newMode);
            if (modeChangePossible) {

                Log.d(TAG, "setTrickMode(" + newMode + ") previous mode: " + previousMode);

                switch (newMode) {
                    case NORMAL:
                        lastRendersCount = switchTrickModeToNormal(previousMode);
                        break;

                    case SCRUB:
                        if (scrubTrickPlay != null) {
                            scrubTrickPlay.scrubStop();
                        } else {
                            scrubTrickPlay = new ScrubTrickPlay(player, this);
                        }
                        switchTrickPlaySpeed(TrickMode.SCRUB, previousMode);
                        scrubTrickPlay.scrubStart();
                        lastRendersCount = 0;
                        break;

                    default:
                        switchTrickPlaySpeed(newMode, previousMode);
                        lastRendersCount = 0;
                        break;

                }
            } else {
                Log.d(TAG, "setTrickMode(" + newMode + ") not possible because of currentPosition");
            }
        }
        return lastRendersCount;
    }

    @Override
    public long getLargestSafeSeekPositionMs() {
        Timeline timeline = player == null ? Timeline.EMPTY : player.getCurrentTimeline();
        long largestSafeSeekPosition = C.TIME_UNSET;

        // Last seekable is either the duration (VOD) or the start of the valid live edge,
        // callee must make sure the timeline is valid and duration has been determined for the
        // current window (after mediasouce is prepared).
        //
        if (! timeline.isEmpty()) {
            Timeline.Window window = new Timeline.Window();
            DefaultTrackSelector.Parameters trackSelectorParameters = trackSelector.getParameters();
            timeline.getWindow(player.getCurrentWindowIndex(), window);
            largestSafeSeekPosition = C.usToMs(window.isDynamic ? window.defaultPositionUs : window.durationUs);

            // In tunneling mode dont allow trick play till the end. Starting play
            // back at end of video segment with misaligned or no audio segment in
            // tunneling mode cause video decoder get stuck forever. Not allowing
            // forward trick play in the last segment fixes this issue. The segment
            // length is assumed to be 6 seconds.
            if (trackSelectorParameters.tunnelingEnabled) {
                largestSafeSeekPosition = (largestSafeSeekPosition > TUNNELING_MODE_SAFE_OFFSET_MS) ? largestSafeSeekPosition - TUNNELING_MODE_SAFE_OFFSET_MS : largestSafeSeekPosition;
            }
        }
        return largestSafeSeekPosition;
    }

    @Override
    public boolean seekToNthPlayedTrickFrame(int frameNumber) {
        return false;
    }

    @Override
    public boolean scrubSeek(long positionMs) {
        if (getCurrentTrickDirection() != TrickPlayDirection.SCRUB) {
            throw new IllegalArgumentException("scrubSeek() is only valid in TrickPlay mode SCRUB, call setTrickMode(SCRUB) first");
        }

        return scrubTrickPlay.scrubSeek(positionMs, false);
    }


    /////
    // Internal API - used privately in the trickplay library
    ////

    @Override
    public synchronized float getTargetFrameRateForPlaybackSpeed(float speed) {
        float frameRate;

        // Calculate from a 3 data point linear reqression based on the
        // target speeds for the 3 forward and 3 reverse speeds.
        // forward rates higher then reverse.  The closest fit formula
        // is ŷ = bX + a, a plethora of calculators are available to generate the
        // values, I used https://www.socscistatistics.com/tests/regression/default.aspx
        //
        // As the JavaDoc for the method says, we can use different, even non linear
        // if the speeds are more continuous in the future.
        //
        final float beta = speed < 0 ? 0.04286f : 0.06667f;
        frameRate = (Math.abs(speed) *  beta) + 2.5f;

        return Math.round(frameRate);
    }

    @Override
    public synchronized float getFrameRateForFormat(Format format) {
        float frameRate = 1.0f;
        if (frameRateAnalyzer != null && frameRateAnalyzer.isAnalyzerInitialized()) {
            frameRate = frameRateAnalyzer.getFrameRateFor(format);
        } else if (format.frameRate != Format.NO_VALUE) {
            frameRate = format.frameRate;
        }
        return frameRate;
    }


    @Override
    public void dispatchTrickFrameRender(long renderedFramePositionUs) {
        lastRenderPositions.add(renderedFramePositionUs);
        for (ListenerRef listenerRef : listeners) {
            Handler handler = getHandler(listenerRef);
            handler.post(() -> listenerRef.listener.trickFrameRendered(renderedFramePositionUs));
        }
    }

    /////
    // Internal methods
    /////

    /**
     * Internal method to change trick play speed.  Assumes change is to a trick-play mode either
     * from another trick-play mode or {@link TrickMode#NORMAL}
     *
     * This method immediately begins trick playback.  If I-Frame tracks (isSmoothPlayback available)
     * are available and mode is forward playback, then use the DefaultMediaClock to handle timing trick-play
     * otherwise use seek based trick-play is used.
     *
     * @param newMode any TrickMode other then NORMAL
     * @param previousMode the previous TrickMode, can be any mode.
     */
    private void switchTrickPlaySpeed(TrickMode newMode, TrickMode previousMode) {
        // Reset to save new set of last N renders on first switch from normal mode.
        if (previousMode == TrickMode.NORMAL) {
            lastRenderPositions.empty();
        }
        setCurrentTrickMode(newMode);

        if (usePlaybackSpeedTrickPlay(newMode)) {
            Log.d(TAG, "Start i-frame trickplay " + newMode + " at media time " + player.getCurrentPosition());
            stopSeekBasedTrickplay();
            setTrackSelectionForTrickPlay(newMode, previousMode);
            player.setPlayWhenReady(newMode != TrickMode.SCRUB);        // SCRUB mode is always paused
            player.setPlaybackParameters(new PlaybackParameters(getSpeedFor(newMode)));
        } else {
            Log.d(TAG, "Start seek-based trickplay " + newMode + " at media time " + player.getCurrentPosition());
            if (TrickPlayControl.directionForMode(previousMode) == TrickPlayDirection.NONE ||
                    usePlaybackSpeedTrickPlay(previousMode)) {
                startSeekBasedTrickplay();
                setTrackSelectionForTrickPlay(newMode, previousMode);
            }
        }
        dispatchTrickModeChanged(newMode, previousMode);

    }

    /**
     * Switch out of trick-play mode 'previousMode'  back to normal {@link TrickMode#NORMAL} normal mode.
     *
     * This will stop the seek-based handler (@link {@link #currentHandler}, restores playback state
     * to 1x forward and, if lastRender positions are saved (i-Frame mode), will seek to the most
     * recent rendered frame.
     *
     * @param previousMode any mode other then {@link TrickMode#NORMAL}
     * @return count of the number of saved last rendered frame positions
     */
    private int switchTrickModeToNormal(TrickMode previousMode) {
        int lastRendersCount = 0;

        long currentPosition = player.getCurrentPosition();
        Log.d(TAG, "Stop trickplay at media time " + currentPosition + " parameters: " + player.getPlaybackParameters().speed + " prev mode: "+ previousMode);

        if (previousMode == TrickMode.SCRUB) {
            scrubTrickPlay.scrubStop();
            scrubTrickPlay = null;
        }
        resetTrickPlayState(false);

        setTrackSelectionForTrickPlay(TrickMode.NORMAL, previousMode);
        player.setPlayWhenReady(true);

        // Only trigger exit logic for non-SCRUB mode exit
        exitingTrickMode = previousMode == TrickMode.SCRUB ? TrickMode.NORMAL : previousMode;
        Log.d(TAG, "Trickplay stopped - media time: " + currentPosition + " parameters: " + player.getPlaybackParameters().speed + " prev mode: "+ previousMode);

        dispatchTrickModeChanged(TrickMode.NORMAL, previousMode);

        return lastRendersCount;
    }


    /**
     * On exit from trickplay we seek to the Nth frame from the last rendered frame.
     * This allows for overshoot correction at higher speeds.
     *
     * This is called after the track selection on exit from trickplay so the seek uses
     * the regular playlist to target the seek point.  The seek is EXACT so it will find
     * a frame intra-segment, by definition the seek target should be an iFrame.
     *
     * This is executed *after* trickplay exit is signaled in order to charge the seek operation
     * to regular playback metrics.
     *
     * Note also, if seek would be a nop (position is equal to the seek point) a small 5 ms
     * offset is added to the position to make sure a discontinuity event is signalled, this
     * allows ExoPlayer to reset the Live Offset target to the current position.
     */
    private void seekOnTrickPlayExitToLastRenderedFrameN() {
        if (exitingTrickMode != TrickMode.NORMAL) {
            long positionMs = player.getCurrentPosition();
            List<Long> lastRenders = lastRenderPositions.lastNPositions();
            Log.d(TAG, "Last rendered frame positions " + lastRenders);
            int lastRendersCount = lastRenders.size();

            if (lastRendersCount > 0) {
                float frameRate = getTargetFrameRateForPlaybackSpeed(getSpeedFor(exitingTrickMode));
                // Forward jumpback for forward is ms reaction time / ms for one frame.  Reverse is just last rendered frame
                int jumpBack = TrickPlayControl.directionForMode(exitingTrickMode) == TrickPlayDirection.FORWARD
                    ? Math.min(Math.round(REACTION_TIME_FOR_OVERSHOOT / (1000.0f / frameRate)), lastRendersCount - 1)
                    : 0;
                positionMs = lastRenders.get(jumpBack);
                long positionDeltaMs = positionMs - player.getCurrentPosition();
                if (positionDeltaMs != 0) {
                    Log.d(TAG, "Seek to " + positionMs + ", " + jumpBack + " frames back from last rendered, delta: " + positionDeltaMs + "ms");
                    SeekParameters save = player.getSeekParameters();
                    player.setSeekParameters(SeekParameters.EXACT);
                    player.seekTo(positionMs);
                    player.setSeekParameters(save);
                    positionMs = C.POSITION_UNSET;      // indicate a seek was issued.
                }
                lastRenderPositions.empty();
            }

            if (positionMs != C.POSITION_UNSET) {
                // Work-around Vecima issue with pre 5.1 origin versions that leaves the Milliseconds off the PDT for
                // iFrame only playlists.  This can result in a slightly negative position on exit which will not resolve
                // in the normal playlist.  Simple hack is to normalize it to >= 0.
                positionMs = Math.max(positionMs, 0);

                Log.d(TAG, "Forcing small noop seek to generate a discontinuity event");
                player.seekTo(positionMs + 5);
            }
            exitingTrickMode = TrickMode.NORMAL;        // indicate exit is completed
        }
    }

    /**
     * Reset any player state changes an the current trick mode to initial values.
     *
     * This is called when the player is stopped or whenever we want to return to normal playback
     * state (eg via {@link #switchTrickModeToNormal(TrickMode)}).
     *
     * When this method returns the player will remain stopped if it was, only the trickplay
     * state to normal and playback parameter changes are made.
     *
     * @param dispatchEvent - set to true to dispatch the trickmode changed event
     */
    private void resetTrickPlayState(boolean dispatchEvent) {
        stopSeekBasedTrickplay();
        player.setPlaybackParameters(PlaybackParameters.DEFAULT);
        TrickMode prevMode = getCurrentTrickMode();
        setCurrentTrickMode(TrickMode.NORMAL);
        restoreSavedTrackSelection();
        Log.d(TAG, "resetTrickPlayState("+ dispatchEvent + ") - speed: " + player.getPlaybackParameters().speed + " prev mode: " + prevMode);
        if (dispatchEvent && prevMode != TrickMode.NORMAL) {
            dispatchTrickModeChanged(TrickMode.NORMAL, prevMode);
        }
    }

    public boolean isPlaybackSpeedForwardTrickPlayEnabled() {
        return playbackSpeedForwardTrickPlayEnabled;
    }

    public void enablePlaybackSpeedForwardTrickPlay(boolean enabled) {
        playbackSpeedForwardTrickPlayEnabled = enabled;
    }

    /**
     * Test if can effect trick-play with the {@link Player#setPlaybackParameters(PlaybackParameters)} call
     * to set playback speed.  {@link PlaybackParameters} does not allow reverse (negative speed).
     *
     * @param mode TrickMode to test if possible.
     * @return true if it is possible to use {@link Player#setPlaybackParameters(PlaybackParameters)} to trickplay
     */
    private boolean usePlaybackSpeedTrickPlay(TrickMode mode) {
        boolean modeAllowsPlayback =
                TrickPlayControl.directionForMode(mode) == TrickPlayDirection.FORWARD || TrickPlayControl.directionForMode(mode) == TrickPlayDirection.SCRUB;
        return isPlaybackSpeedForwardTrickPlayEnabled() && modeAllowsPlayback && isSmoothPlayAvailable();
    }


    private void setSpeedsForMode(boolean isIFramesAvailable) {
        // Keep a seperate map defining the mapping from TrickMode enum to speed (to keep it a pure enum)
        //
        EnumMap<TrickMode, Float> initialSpeedsForMode = new EnumMap<TrickMode, Float>(TrickMode.class);
        for (TrickMode trickMode : TrickMode.values()) {
            initialSpeedsForMode.put(trickMode, getDefaultSpeedForMode(trickMode, isIFramesAvailable));
        }

        this.speedsForMode = Collections.synchronizedMap(initialSpeedsForMode);
    }

    /**
     * Default speeds used to initialize the map, depending on if smooth high speed
     * playback is available.
     *
     * @param mode - the {@link com.google.android.exoplayer2.trickplay.TrickPlayControl.TrickMode} to get
     * @param isIFramesAvailable - if speed should be iFrame based.
     * @return playback speed to set for the mode
     */
    private static Float getDefaultSpeedForMode(TrickMode mode, boolean isIFramesAvailable) {
        float speed = 0.0f;

        switch (mode) {
            case FF1:
                speed = 15.0f;
                break;
            case FR1:
                speed = -15.0f;
                break;

            case FF2:
                speed = 30.0f;
                break;
            case FR2:
                speed = -30.0f;
                break;

            case FF3:
                speed = 60.0f;
                break;
            case FR3:
                speed = -60.0f;
                break;

            case NORMAL:
                speed = 1.0f;
                break;

            case SCRUB:
                speed = 1.0f;   // set to 1, but effectively 0 as player is paused each seek
                break;
        }

        return speed;
    }

    private synchronized void setCurrentTrickMode(TrickMode mode) {
        currentTrickMode = mode;
    }


    /**
     * Check if we are not so close to the current timeline ({@link Player#getCurrentTimeline()}
     * boundaries as to prevent starting trick play in the mode.
     *
     * This check is equivalent to {@link #exitTrickPlayIfTimelineExceeded(Timeline)}, that is this
     * method returns false if {@link #exitTrickPlayIfTimelineExceeded(Timeline)} would exit to
     * normal mode.
     *
     * @param requestedMode trick play mode to check if the current timeline will allow starting it.
     * @return true if trick play in the requested mode is possible
     */
    private boolean canTrickPlayInMode(TrickMode requestedMode) {
        boolean modeChangePossible = player != null;
        if (modeChangePossible) {
            Timeline timeline = player.getCurrentTimeline();
            if (!timeline.isEmpty()) {
                Timeline.Window currentWindow = new Timeline.Window();
                timeline.getWindow(player.getCurrentWindowIndex(), currentWindow);
                if (currentWindow.durationUs != C.TIME_UNSET) {
                    modeChangePossible =
                        canContinuePlaybackInMode(currentWindow, player.getCurrentPosition(), requestedMode);
                }
            }
        }
        return modeChangePossible;
    }

    /**
     * Exit trick-play (back to normal mode) if the current position exceeds the timeline
     * This uses {@link #canContinuePlaybackInMode(Timeline.Window, long, TrickMode)}
     *
     * @param timeline {@link Timeline} to check if position in in seekable bounds for
     */
    private boolean exitTrickPlayIfTimelineExceeded(Timeline timeline) {
        boolean didExit = false;
        TrickMode currentTrickMode = getCurrentTrickMode();
        if (! timeline.isEmpty() && currentTrickMode != TrickMode.NORMAL) {
            Timeline.Window currentWindow = new Timeline.Window();
            timeline.getWindow(player.getCurrentWindowIndex(), currentWindow);
            if (! canContinuePlaybackInMode(currentWindow, player.getContentPosition(), currentTrickMode)) {
                Log.d(TAG, "exitTrickPlayIfTimelineExceeded() - exiting trickplay, position outside of seekable, currentPosition: " + player.getContentPosition() + "ms");
                switchTrickModeToNormal(currentTrickMode);
                didExit = true;
            }
        }
        return didExit;
    }

    /**
     * Checks if trick-play mode requested (requestedMode) is possible to start (or can
     * continue) based on the current playback position in the timeline and the trick
     * play speed.
     *
     * @param currentWindow - current playing Timeline.Window to check seek boundries on
     * @param playbackPositionMs - current playback position, checked against the window
     * @param requestedMode - the mode to check if trick-play can start (continue) in
     * @return true if trick-play in the 'requestedMode' is possible.
     */
    private boolean canContinuePlaybackInMode(Timeline.Window currentWindow,
        long playbackPositionMs,
        TrickMode requestedMode) {
        boolean isPossible = true;
        long lastSeekablePosition = getLargestSafeSeekPositionMs();

        // TODO - trickplay reverse, as well as being behind the live window can leave the
        // TODO - current position negative, the code below works as expected in this case, but note well.
        //
        TrickPlayDirection direction = TrickPlayControl.directionForMode(requestedMode);
        switch (direction) {
            case FORWARD:
                int tolerance = (int) (100 * getSpeedFor(requestedMode));
                long delta = lastSeekablePosition - playbackPositionMs;
                isPossible = delta >= tolerance;
                break;

            case REVERSE:
                isPossible = playbackPositionMs >= 1000;
                break;
        }
        return isPossible;
    }

    private void setTrackSelectionForTrickPlay(TrickMode newMode, TrickMode previousMode) {
        Log.d(TAG, "setTrackSelectionForTrickPlay(" + newMode +"," + previousMode +") - current newMode");

        // Update if we switch in or out of NORMAL mode only.
        if (newMode != previousMode && (newMode == TrickMode.NORMAL || previousMode == TrickMode.NORMAL)) {
            if (newMode == TrickMode.NORMAL) {
                restoreSavedTrackSelection();
            } else {
                Log.d(TAG, "setTrackSelectionForTrickPlay() - disabling tracks not used for trick-play (audio, CC)");

                DefaultTrackSelector.Parameters trackSelectorParameters = trackSelector.getParameters();
                savedParameters = trackSelectorParameters;

                DefaultTrackSelector.ParametersBuilder builder = trackSelectorParameters.buildUpon();
                for (int i = 0; i < player.getRendererCount(); i++) {
                  if (player.getRendererType(i) != C.TRACK_TYPE_VIDEO) {
                      builder.setRendererDisabled(i, true);
                  }
                }
                trackSelector.setParameters(builder);
            }

        }

    }

    private void restoreSavedTrackSelection() {
        // Restore track selection to state before trick-play started
        if (savedParameters != null) {
            Log.d(TAG, "setTrackSelectionForTrickPlay() - restoring previously saved track selection parameters");
            trackSelector.setParameters(savedParameters.buildUpon());
            savedParameters = null;
        }
    }

    private void stopSeekBasedTrickplay() {
        if (currentHandler != null) {
            Log.d(TAG, "destroySeekBasedTrickplayHandler() - removing listeners and discarding pending messages, currentHandler: " + currentHandler);
            currentHandler.stopTrickPlay();
            currentHandler = null;
        }
        player.setPlaybackParameters(PlaybackParameters.DEFAULT);
    }

    private void startSeekBasedTrickplay() {
        stopSeekBasedTrickplay();
        currentHandler = new SeekBasedTrickPlay(this, player);
        currentHandler.startTrickPlay();
    }

    private void dispatchPlaylistMetadataChanged() {
        for (ListenerRef listenerRef : listeners) {
            Handler handler = getHandler(listenerRef);
            handler.post(() -> listenerRef.listener.playlistMetadataValid(isMetadataValid));
        }
    }

    private void dispatchTrickModeChanged(TrickMode newMode, TrickMode prevMode) {
        for (ListenerRef listenerRef : listeners) {
            Handler handler = getHandler(listenerRef);
            handler.post(() -> listenerRef.listener.trickPlayModeChanged(newMode, prevMode));
        }
    }

    private Handler getHandler(ListenerRef listenerRef) {
        return listenerRef.callType == ListenerRef.CallType.APPLICATION ?
            applicatonHandler : playbackHandler;
    }


    @Override
    public void addEventListener(TrickPlayEventListener eventListener) {
        listeners.add(new ListenerRef(ListenerRef.CallType.APPLICATION, eventListener));
    }

    @Override
    public void removeEventListener(TrickPlayEventListener eventListener) {
        removeListenerWithType(eventListener, ListenerRef.CallType.APPLICATION);
    }

    @Override
    public void addEventListenerInternal(TrickPlayEventListener eventListener) {
        listeners.add(new ListenerRef(ListenerRef.CallType.PLAYER, eventListener));
    }

    @Override
    public void removeEventListenerInternal(TrickPlayEventListener eventListener) {
        removeListenerWithType(eventListener, ListenerRef.CallType.PLAYER);
    }

    @Override
    public void cleanUpForStop() {
        setCurrentTrickMode(TrickMode.NORMAL);
        listeners.clear();
    }

    private boolean isVideoFormat(Format format) {
        boolean isVideo = false;
        int trackType = MimeTypes.getTrackType(format.sampleMimeType);
        if (trackType != C.TRACK_TYPE_UNKNOWN) {
            isVideo = trackType == C.TRACK_TYPE_VIDEO;
        } else {
            isVideo = MimeTypes.getVideoMediaMimeType(format.codecs) != null;
        }
        return isVideo;
    }

    private void removeListenerWithType(TrickPlayEventListener eventListener, ListenerRef.CallType callType) {
        for (ListenerRef listener : listeners) {
            if (listener.listener == eventListener && listener.callType == callType) {
                listeners.remove(listener);
            }
        }
    }
}
