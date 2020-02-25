package com.google.android.exoplayer2.trickplay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroup;
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
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SeekParameters;
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

    private @MonotonicNonNull SimpleExoPlayer player;
    private final DefaultTrackSelector trackSelector;
    private DefaultTrackSelector.Parameters savedParameters;

    private final CopyOnWriteArraySet<ListenerRef> listeners;

    private TrickMode currentTrickMode = TrickMode.NORMAL;
    private @Nullable PlayerEventListener playerEventListener;
    private int lastSelectedAudioTrack = -1;

    private SeekBasedTrickPlay currentHandler = null;

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

    TrickPlayController(DefaultTrackSelector trackSelector) {
        this.trackSelector = trackSelector;
        this.listeners = new CopyOnWriteArraySet<>();

        // Default (for now) assume iFrames are available.
        setSpeedsForMode(true);

        addEventListener(new TrickPlayEventListener() {
            @Override
            public void playlistMetadataValid(boolean isMetadataValid) {
                if (isMetadataValid) {
                    setSpeedsForMode(isSmoothPlayAvailable);
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
            Timeline.Window window = timeline.getWindow(player.getCurrentWindowIndex(), new Timeline.Window());

            List<Long> values = Collections.emptyList();

            if (timeline.isEmpty()) {
                Log.w(TAG, "trickplay stopped with empty timeline, should not be possible");
            } else {
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

        @Override
        public void onPlayerStateChanged(EventTime eventTime, boolean playWhenReady, int playbackState) {

            // reset isMetadata valid if transition to an "ended" like state. STATE_READY and
            // STATE_BUFFERING wait until the initial set of tracks are set before indicating valid
            // metadata.
            //
            switch(playbackState) {
                case Player.STATE_IDLE:
                case Player.STATE_ENDED:
                    isMetadataValid = false;
                    resetTrickPlayState(true);
                    dispatchPlaylistMetadataChanged();
                    break;

                case Player.STATE_READY:
                case Player.STATE_BUFFERING:
                    exitTrickPlayIfTimelineExceeded(eventTime.timeline);
                    break;
            }
        }

        @Override
        public void onTimelineChanged(EventTime eventTime, int reason) {
            if (reason == Player.TIMELINE_CHANGE_REASON_DYNAMIC) {
                exitTrickPlayIfTimelineExceeded(eventTime.timeline);
            }
        }

        @Override
        public void onMediaPeriodCreated(EventTime eventTime) {
            Log.d(TAG, "onMediaPeriodCreated() -  timeline empty: " + eventTime.timeline.isEmpty());
        }

        @Override
        public void onMediaPeriodReleased(EventTime eventTime) {
            Log.d(TAG, "onMediaPeriodReleased() -  timeline empty: " + eventTime.timeline.isEmpty());
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
                    isSmoothPlayAvailable = false;
                    for (int trackIndex = 0; trackIndex < group.length && ! isSmoothPlayAvailable; trackIndex++) {
                        Format format = group.getFormat(trackIndex);
                        isSmoothPlayAvailable = (format.roleFlags & C.ROLE_FLAG_TRICK_PLAY) != 0;
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
        }

        @Override
        public void handleMessage(Message msg) {
            if (currentTrickMode != TrickMode.NORMAL) {
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
     * NOTE: there is no chance of handler leak as {@link #removePlayerReference()} cleans up this handler
     */
    @SuppressLint("HandlerLeak")
    private class SeekBasedTrickPlay extends Handler implements AnalyticsListener, TrickPlayEventListener {
        private static final String TAG = "SeekBasedTrickPlay";

        // Causes the handler to issue an initial seek to the position indicated by the clock
        static final int MSG_TRICKPLAY_STARTSEEK = 1;

        // Seek issued for seeks after the initial start, these are throtted to achive a frame rate
        static final int MSG_TRICKPLAY_TIMED_SEEK = 2;

        // Used to throttle the next seek so as to achieve the frame rate targets
        static final int MSG_TRICKPLAY_FRAMERENDER = 4;

        // Issue delayed seek when frame release to render in last 30ms
        static final int MSG_DELAYED_SEEK_AFTER_RENDER = 5;

        // Time delay between seeks if we are rendering frames each seek
        static final int DEFAULT_SEEK_INTERVAL_MS = 500;

        // Fall back delay between seeks if frame rendering is not reaching the target FPS
        static final int THROTTLED_SEEK_INTERVAL_MS = 1000;

        // FPS target, this is the render rate we hope for
        static final int TARGET_FPS = 1;
        static final int targetFrameIntervalMs = 1000 / TARGET_FPS;

        // The frame rendered event is reported when the frame is released to the output buffer,
        // the actual render may be as much as a VSYNC interval later.  This delay avoids
        // issuing a seek immediately after a frame is cued (as the seek will reset the codec
        // and discard output buffers
        private static final int DELAY_FOR_RENDER_MS = 30;

        private AnalyticsListener.EventTime lastRenderTime;
        private long lastIssuedSeekTimeMs = C.TIME_UNSET;
        private ReversibleMediaClock currentMediaClock;
        private int currentSeekIntervalMs = DEFAULT_SEEK_INTERVAL_MS;

        SeekBasedTrickPlay(long startingPosition) {
            player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
            boolean isForward = getCurrentTrickDirection() == TrickPlayDirection.FORWARD;
            Log.d(TAG, "Create SeekBasedTrickPlay() - initial mode: " + getCurrentTrickMode() + " start at: " + startingPosition);

            // Round position to nearest second, this improves the odds the first seek will hit an I-Frame playlist segment
            // boundary.  Frame intervals for issuing seeks are set to even seconds as well
            //
            long roundedPosition = (startingPosition / 1000L) * 1000L;
            currentMediaClock = new ReversibleMediaClock(isForward, C.msToUs(roundedPosition), Clock.DEFAULT);

            TrickMode currentTrickMode = getCurrentTrickMode();
            updateTrickMode(currentTrickMode);
        }

        private void updateTrickMode(TrickMode currentTrickMode) {
            boolean isForward = getCurrentTrickDirection() == TrickPlayDirection.FORWARD;
            float currentSpeed = getSpeedFor(currentTrickMode);
            PlaybackParameters playbackParameters = new PlaybackParameters(Math.abs(currentSpeed));
            currentMediaClock.setPlaybackParameters(playbackParameters);
            currentMediaClock.setForward(isForward);
            Log.d(TAG, "updateTrickMode() - to: " + currentTrickMode + " current pos: " + currentMediaClock.getPositionUs());
        }

        @Override
        public void handleMessage(Message msg) {
            TrickMode currentTrickMode = getCurrentTrickMode();

            /* if time to exit trick-play reached seek boundry, then switch to normal and discard this last message */
            boolean didExitTrickPlay = exitTrickPlayIfTimelineExceeded(player.getCurrentTimeline());
            if (didExitTrickPlay) {
                Log.d(TAG, "End seek-based trickplay, reached seek boundry.  mode: " + currentTrickMode + " at media time " + player.getCurrentPosition());

                lastRenderTime = null;
            } else if (currentTrickMode != TrickMode.NORMAL) {

                boolean isTargetFrameIntervalMet;  // True if at least making the min. desired frame rate.
                long timeSinceLastRenderMs = C.TIME_UNSET;

                if (lastRenderTime == null) {
                    isTargetFrameIntervalMet = false;
                } else {
                    timeSinceLastRenderMs = currentMediaClock.getClock().elapsedRealtime() - lastRenderTime.realtimeMs;
                    isTargetFrameIntervalMet = timeSinceLastRenderMs <= targetFrameIntervalMs;
                }

                switch (msg.what) {
                    case MSG_TRICKPLAY_STARTSEEK:
                        Log.d(TAG, "msg STARTSEEK - calling seekToMediaClock() - timeSinceLastRenderMs: " + timeSinceLastRenderMs + " targetFrameIntervalMs: " + targetFrameIntervalMs);
                        currentSeekIntervalMs = DEFAULT_SEEK_INTERVAL_MS;
                        sendEmptyMessageDelayed(MSG_TRICKPLAY_TIMED_SEEK, currentSeekIntervalMs);
                        Log.d(TAG, "msg STARTSEEK - set next seek time, currentSeekIntervalMs: " + currentSeekIntervalMs
                            + " timeSinceLastRenderMs: " + timeSinceLastRenderMs);
                        break;

                    case MSG_TRICKPLAY_TIMED_SEEK:
                        long nextSeekDelay = C.TIME_UNSET;

                        if (isTargetFrameIntervalMet) {
                            currentSeekIntervalMs = DEFAULT_SEEK_INTERVAL_MS;
                            if (timeSinceLastRenderMs > DELAY_FOR_RENDER_MS) {
                                Log.d(TAG, "msg TIMED_SEEK - just rendered, delay seek slightly, timeSinceLastRenderMs: " + timeSinceLastRenderMs);
                                sendEmptyMessage(MSG_DELAYED_SEEK_AFTER_RENDER);
                            } else {
                                Log.d(TAG, "msg TIMED_SEEK - had render, so issuing seek. timeSinceLastRenderMs: " + timeSinceLastRenderMs + " next seek in: " + currentSeekIntervalMs +"ms");
                                seekToMediaClock();
                                nextSeekDelay = currentSeekIntervalMs;
                            }
                        } else {
                            Log.d(TAG, "msg TIMED_SEEK - no render, " + debugTimeStr() +"timeSinceLastRenderMs: " + timeSinceLastRenderMs);
                            if (currentSeekIntervalMs != THROTTLED_SEEK_INTERVAL_MS) {
                                Log.d(TAG, "msg TIMED_SEEK - change seek interval to thottled value");
                                currentSeekIntervalMs = THROTTLED_SEEK_INTERVAL_MS;
                            }
                            Log.d(TAG, "msg TIMED_SEEK - no render, issuing seek anyway, timeSinceLastRenderMs: " + timeSinceLastRenderMs + " next seek in: " + currentSeekIntervalMs +"ms");
                            seekToMediaClock();
                            nextSeekDelay = currentSeekIntervalMs;
                        }

                        if (nextSeekDelay != C.TIME_UNSET) {
                            sendEmptyMessageDelayed(MSG_TRICKPLAY_TIMED_SEEK, currentSeekIntervalMs);
                        }
                        break;

                    case MSG_DELAYED_SEEK_AFTER_RENDER:
                        Log.d(TAG, "msg DELAYED_SEEK_AFTER_RENDER - next seek in: " + (currentSeekIntervalMs - DELAY_FOR_RENDER_MS) +"ms");
                        seekToMediaClock();
                        sendEmptyMessageDelayed(MSG_TRICKPLAY_TIMED_SEEK, currentSeekIntervalMs - DELAY_FOR_RENDER_MS);
                        break;

                    case MSG_TRICKPLAY_FRAMERENDER:
                        lastRenderTime = (AnalyticsListener.EventTime) msg.obj;
                        player.setPlayWhenReady(false);
                        boolean isPendingSeek =  hasMessages(MSG_TRICKPLAY_TIMED_SEEK);
                        Log.d(TAG, "msg FRAMERENDER -" + debugTimeStr() + "timeSinceLastRenderMs:" + timeSinceLastRenderMs + " isPendingSeek: " + isPendingSeek);
                        break;

                }
            }
        }

        /**
         * Issue the actual seek to the player.  The seek value is based on the current media
         * time (playback position) reported by the {@link #currentMediaClock}.
         *
         * The position is truncated to a 1 second boundary (to better match the i-Frame intervals) and
         * min'd with the upper bound of the seekable range.
         */
        private void seekToMediaClock() {
            long largestSafeSeekPositionMs = getLargestSafeSeekPositionMs();
            long seekTargetMs = C.usToMs(currentMediaClock.getPositionUs());
            seekTargetMs = (seekTargetMs / 1000L) * 1000L;
            seekTargetMs = Math.min(seekTargetMs, largestSafeSeekPositionMs);

            Log.d(TAG, "seekToMediaClock() - issuing, seekTarget: " + seekTargetMs / 1000.0f + debugTimeStr());
            lastIssuedSeekTimeMs = seekTargetMs;
            player.seekTo(seekTargetMs);
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
        public void startTrickPlay() {
            Log.d(TAG, "starting seek trick-play - mode " + currentTrickMode + " current pos: " + currentMediaClock.getPositionUs());
            sendEmptyMessage(MSG_TRICKPLAY_STARTSEEK);
        }

        // Implement trickplay event listener

        @Override
        public void trickPlayModeChanged(TrickMode newMode, TrickMode prevMode) {
            if (newMode != TrickMode.NORMAL) {
                Log.d(TAG, "trickPlayModeChanged() from: " + prevMode + " to: " + newMode + " current pos: " + currentMediaClock.getPositionUs());

                updateTrickMode(newMode);
            }
        }

        @Override
        public void trickFrameRendered(long frameRenderTimeUs) {
            if (isSmoothPlayAvailable()) {
              long realtimeMs = Clock.DEFAULT.elapsedRealtime();
              AnalyticsListener.EventTime eventTime = new EventTime(realtimeMs, player.getCurrentTimeline(), 0, null,
                  0, player.getCurrentPosition(), 0);
              Log.d(TAG, "Trick frame render " + getCurrentTrickMode() + " position: " + eventTime.currentPlaybackPositionMs);
              removeMessages(SeekBasedTrickPlay.MSG_TRICKPLAY_FRAMERENDER);
              Message msg = obtainMessage(SeekBasedTrickPlay.MSG_TRICKPLAY_FRAMERENDER, eventTime);
              sendMessage(msg);
            }
        }

        // Implement analytics listener.  Methods are called in thread that created this handler

        @Override
        public void onRenderedFirstFrame(EventTime eventTime, @Nullable Surface surface) {
            if (! isSmoothPlayAvailable()) {
                Log.d(TAG, "First frame " + getCurrentTrickMode() + " position: " + eventTime.currentPlaybackPositionMs);
                Message msg = obtainMessage(SeekBasedTrickPlay.MSG_TRICKPLAY_FRAMERENDER, eventTime);
                sendMessage(msg);
            }
        }

        @Override
        public void onSeekProcessed(EventTime eventTime) {

            // Check if it's our seek (we set the lastIssuedSeekTimeMs for each one we source).  Otherwise
            // user pressed 'advance' or some other jump button.
            //
            if (eventTime.currentPlaybackPositionMs == lastIssuedSeekTimeMs) {
                Log.d(TAG, "onSeekProcessed() - mode:  " + currentTrickMode + debugTimeStr());
            } else {
                Log.d(TAG, "onSeekProcessed() - reset clock for advance - " + currentTrickMode + debugTimeStr());
                currentMediaClock.resetPosition(C.msToUs(eventTime.currentPlaybackPositionMs));
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
    public RenderersFactory createRenderersFactory(Context context) {
        return new TrickPlayRendererFactory(context, this);
//        return new DefaultRenderersFactory(context);
    }

    @Override
    public LoadControl createLoadControl(LoadControl delegate) {
        return new AdaptiveLoadControl(this, delegate);
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
        destroySeekBasedTrickplayHandler();
        playbackHandler.removeCallbacksAndMessages(null);
        playbackHandler = null;
        applicatonHandler.removeCallbacksAndMessages(null);
        applicatonHandler = null;
        this.listeners.clear();
        this.player.removeAnalyticsListener(playerEventListener);
        playerEventListener = null;
        this.player = null;
    }

    @Override
    public synchronized TrickMode getCurrentTrickMode() {
        return currentTrickMode;
    }

    @Override
    public TrickPlayDirection getCurrentTrickDirection() {
        return directionForMode(getCurrentTrickMode());
    }

    @Override
    public boolean isSmoothPlayAvailable() {
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

                if (newMode == TrickMode.NORMAL) {
                    lastRendersCount = switchTrickModeToNormal(previousMode);
                } else {
                    switchTrickPlaySpeed(newMode, previousMode);
                    lastRendersCount = 0;
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
        long duration = C.TIME_UNSET;
        if (! timeline.isEmpty()) {
            Timeline.Window window = new Timeline.Window();
            timeline.getWindow(player.getCurrentWindowIndex(), window);
            duration = C.usToMs(window.isDynamic ? window.defaultPositionUs : window.durationUs);
        }
        return duration;
    }

    @Override
    public boolean seekToNthPlayedTrickFrame(int frameNumber) {
        List<Long> renderPositions = lastRenderPositions.lastNPositions();
        boolean success = frameNumber < renderPositions.size();
        if (success) {
            player.seekTo(renderPositions.get(frameNumber));
        }
        return success;
    }

    /////
    // Internal API - used privately in the trickplay library
    ////

    // NOTE this method must be fast and will be called from the ExoPlayer playback thread
    @Override
    public synchronized boolean useTrickPlayRendering() {
      return getCurrentTrickDirection() == TrickPlayDirection.FORWARD && isSmoothPlayAvailable;
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
            player.setPlayWhenReady(true);
            player.setPlaybackParameters(new PlaybackParameters(getSpeedFor(newMode)));
        } else {
            Log.d(TAG, "Start seek-based trickplay " + newMode + " at media time " + player.getCurrentPosition());
            if (previousMode == TrickMode.NORMAL) {
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

        resetTrickPlayState(false);

        setTrackSelectionForTrickPlay(TrickMode.NORMAL, previousMode);
        player.setPlayWhenReady(true);

        if (isSmoothPlayAvailable()) {
            List<Long> lastRenders = lastRenderPositions.lastNPositions();
            Log.d(TAG, "Last rendered frame positions " + lastRenders);
            lastRendersCount = lastRenders.size();

            if (lastRendersCount > 0) {
                long positionMs = lastRenders.get(0);
                Log.d(TAG, "Seek to previous rendered frame, positions " + positionMs + " currentPos: " + player.getCurrentPosition());
                player.seekTo(positionMs);
            }
        }
        Log.d(TAG, "Trickplay stopped - media time: " + currentPosition + " parameters: " + player.getPlaybackParameters().speed + " prev mode: "+ previousMode);

        dispatchTrickModeChanged(TrickMode.NORMAL, previousMode);

        return lastRendersCount;
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
        player.setSeekParameters(SeekParameters.DEFAULT);
        player.setPlaybackParameters(PlaybackParameters.DEFAULT);
        TrickMode prevMode = getCurrentTrickMode();
        setCurrentTrickMode(TrickMode.NORMAL);
        restoreSavedTrackSelection();
        Log.d(TAG, "resetTrickPlayState("+ dispatchEvent + ") - speed: " + player.getPlaybackParameters().speed + " prev mode: " + prevMode);
        if (dispatchEvent && prevMode != TrickMode.NORMAL) {
            dispatchTrickModeChanged(TrickMode.NORMAL, prevMode);
        }
    }

    /**
     * Test if can effect trick-play with the {@link Player#setPlaybackParameters(PlaybackParameters)} call
     * to set playback speed.  {@link PlaybackParameters} does not allow reverse (negative speed).
     *
     * @param mode TrickMode to test if possible.
     * @return true if it is possible to use {@link Player#setPlaybackParameters(PlaybackParameters)} to trickplay
     */
    private boolean usePlaybackSpeedTrickPlay(TrickMode mode) {
        return (directionForMode(mode) == TrickPlayControl.TrickPlayDirection.FORWARD) && isSmoothPlayAvailable();
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
     * @return
     */
    private static Float getDefaultSpeedForMode(TrickMode mode, boolean isIFramesAvailable) {
        Float speed = 0.0f;

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
        }

        return speed;
    }

    private static TrickPlayDirection directionForMode(TrickMode mode) {
        TrickPlayDirection direction = TrickPlayControl.TrickPlayDirection.NONE;

        switch (mode) {
            case FF1:
            case FF2:
            case FF3:
                direction = TrickPlayControl.TrickPlayDirection.FORWARD;
                break;
            case FR1:
            case FR2:
            case FR3:
                direction = TrickPlayControl.TrickPlayDirection.REVERSE;
                break;
        }
        return  direction;
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

        // Last seekable is either the duration (VOD) or the start of the valid live edge,
        // callee must make sure the timeline is valid and duration has been determined for the
        // current window (after mediasouce is prepared.
        //
        long lastSeekablePosition =
            currentWindow.isDynamic ? C.usToMs(currentWindow.defaultPositionUs)
                : C.usToMs(currentWindow.durationUs);

        // TODO - trickplay reverse, as well as being behind the live window can leave the
        // TODO - current position negative, the code below works as expected in this case, but note well.
        //
        TrickPlayDirection direction = directionForMode(requestedMode);
        switch (direction) {
            case FORWARD:
                int tolerance = (int) (100 * getSpeedFor(requestedMode));
                long delta = lastSeekablePosition - playbackPositionMs;
                isPossible = delta >= tolerance;
                Log.d(TAG, "canContinuePlaybackInMode() - isPossible " + isPossible + " tolerance: " + tolerance + " lastSeekable: " + lastSeekablePosition + " positionMs: " + playbackPositionMs + " delta: " + delta);
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
        destroySeekBasedTrickplayHandler();
        player.setSeekParameters(SeekParameters.DEFAULT);
        player.setPlaybackParameters(PlaybackParameters.DEFAULT);
    }

    private void destroySeekBasedTrickplayHandler() {
        if (currentHandler != null) {
            currentHandler.removeCallbacksAndMessages(null);
            player.removeAnalyticsListener(currentHandler);
            removeEventListener(currentHandler);
            currentHandler = null;
        }
    }

    private void startSeekBasedTrickplay() {
        stopSeekBasedTrickplay();
        currentHandler = new SeekBasedTrickPlay(player.getContentPosition());
        player.addAnalyticsListener(currentHandler);
        player.setPlaybackParameters(new PlaybackParameters(0.1f));
        if (isSmoothPlayAvailable()) {
            addEventListener(currentHandler);
        }
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
