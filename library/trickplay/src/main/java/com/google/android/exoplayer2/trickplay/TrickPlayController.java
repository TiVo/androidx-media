package com.google.android.exoplayer2.trickplay;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.LoadControl;
import java.util.Collections;
import java.util.EnumMap;
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
import com.google.android.exoplayer2.util.MediaClock;

/**
 * Implements simple trick-play for video fast forward / reverse without sound.
 *
 * Fast modes are three different rates in each direction including normal playback (which re-enables sound)
 */
class TrickPlayController implements TrickPlayControl {

    private static final String TAG = "TRICK-PLAY";

    private @MonotonicNonNull SimpleExoPlayer player;
    private final DefaultTrackSelector trackSelector;

    private final CopyOnWriteArraySet<TrickPlayEventListener> listeners;

    private TrickMode currentTrickMode = TrickMode.NORMAL;
    private AnalyticsListener playerEventListener;
    private int lastSelectedAudioTrack = -1;

    private TrickPlayMessageHandler currentHandler = null;
    private ReversibleMediaClock currentMediaClock = null;

    private Map<TrickMode, Float> speedsForMode;

    TrickPlayController(DefaultTrackSelector trackSelector) {
        this.trackSelector = trackSelector;
        this.listeners = new CopyOnWriteArraySet<>();

        // Keep a seperate map defining the mapping from TrickMode enum to speed (to keep it a pure enum)
        //
        EnumMap<TrickMode, Float> initialSpeedsForMode = new EnumMap<TrickMode, Float>(TrickMode.class);
        for (TrickMode trickMode : TrickMode.values()) {
            initialSpeedsForMode.put(trickMode, getDefaultSpeedForMode(trickMode));
        }

        this.speedsForMode = Collections.synchronizedMap(initialSpeedsForMode);
    }

    /**
     * Keeps track of render position based on the existing ExoPlayer {@link MediaClock}
     * this interface is used by the renderers, so using it will facilite
     * using either seek based of IDR sample flow based hig speed playback.
     *
     */
    private class ReversibleMediaClock implements MediaClock {

        private boolean isForward;
        private Clock clock;
        private long baseElapsedMs;
        private PlaybackParameters playbackParameters;
        private long baseUs;

        /**
         * Construct the clock source with the current position to start and the
         * intended playback direction (as PlaybackParameters does not support speed < 0)
         *
         * @param isForward - true if playback is forward else false.
         * @param positionUs - current position at start of clock
         * @param clock - source of time
         */
        ReversibleMediaClock(boolean isForward, long positionUs, Clock clock) {
            this.isForward = isForward;
            this.clock = clock;
            baseElapsedMs = clock.elapsedRealtime();
            this.baseUs = positionUs;
        }

        @Override
        public long getPositionUs() {
            long positionUs = baseUs;
            long elapsedSinceBaseMs = clock.elapsedRealtime() - baseElapsedMs;
            long elapsedSinceBaseUs = C.msToUs(elapsedSinceBaseMs);
            if (playbackParameters.speed == 1f) {
                positionUs += isForward ? elapsedSinceBaseUs : -elapsedSinceBaseUs;
            } else if (isForward) {
                positionUs += playbackParameters.getMediaTimeUsForPlayoutTimeMs(elapsedSinceBaseMs);
            } else {
                positionUs -= playbackParameters.getMediaTimeUsForPlayoutTimeMs(elapsedSinceBaseMs);
            }
            return positionUs;
        }

        @Override
        public PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters) {

            this.playbackParameters = playbackParameters;
            return playbackParameters;
        }

        @Override
        public PlaybackParameters getPlaybackParameters() {
            return playbackParameters;
        }
    }

    private class PlayerEventListener implements AnalyticsListener {


        @Override
        public void onTracksChanged(EventTime eventTime, TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
            TrackSelectionArray selections = player.getCurrentTrackSelections();
            for (int i = 0; i < selections.length; i++) {
              if (player.getRendererType(i) == C.TRACK_TYPE_AUDIO && selections.get(i) != null && lastSelectedAudioTrack == -1) {
                lastSelectedAudioTrack = i;
              }
            }
        }

        @Override
        public void onRenderedFirstFrame(EventTime eventTime, @Nullable Surface surface) {
            Log.d(TAG, "First frame " + getCurrentTrickMode() + " position: " + eventTime.currentPlaybackPositionMs);

            if (currentHandler != null) {
                Message msg = currentHandler.obtainMessage(TrickPlayMessageHandler.MSG_TRICKPLAY_FRAMERENDER, eventTime);
                currentHandler.sendMessage(msg);
            }
        }

        @Override
        public void onSeekStarted(EventTime eventTime) {
            if (currentHandler != null) {
                Message msg = currentHandler.obtainMessage(TrickPlayMessageHandler.MSG_TRICKPLAY_SEEKSTARTED, eventTime);
                currentHandler.sendMessage(msg);
            }
        }

        @Override
        public void onSeekProcessed(EventTime eventTime) {
            if (currentHandler != null) {
                Message msg = currentHandler.obtainMessage(TrickPlayMessageHandler.MSG_TRICKPLAY_SEEKPROCESSED, eventTime);
                currentHandler.sendMessage(msg);
            }
        }
    }

    /**
     * Handles messages in the callers thread (thread that created the {@see TrickPlayController} and called
     * {@see #setTrickMode}.
     */
    private class TrickPlayMessageHandler extends Handler {

        public static final int MSG_TRICKPLAY_STARTSEEK = 1;
        public static final int MSG_TRICKPLAY_FRAMERENDER = 2;
        public static final int MSG_TRICKPLAY_SEEKPROCESSED = 3;
        public static final int MSG_TRICKPLAY_SEEKSTARTED = 3;

        public static final int TARGET_FPS = 3;
        public static final int IDR_INTERVAL_TARGET_MS = 1000 / TARGET_FPS;

        private AnalyticsListener.EventTime lastSeekProcessed;

        TrickPlayMessageHandler(long startingPosition) {
            player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
            float currentSpeed = getSpeedFor(getCurrentTrickMode());
            boolean isForward = currentSpeed > 0.0f;
            Log.d(TAG, "TrickPlayMessageHandler() - mode: " + getCurrentTrickMode() + " start at: " + startingPosition);

            PlaybackParameters playbackParameters = new PlaybackParameters(Math.abs(currentSpeed));
            currentMediaClock = new ReversibleMediaClock(isForward, C.msToUs(startingPosition), Clock.DEFAULT);
            currentMediaClock.setPlaybackParameters(playbackParameters);
        }

        @Override
        public void handleMessage(Message msg) {
            TrickMode currentTrickMode = getCurrentTrickMode();

            switch (msg.what) {
                case MSG_TRICKPLAY_STARTSEEK:

                    if (currentTrickMode != TrickMode.NORMAL) {
                        long contentPosition = player.getContentPosition();
                        long seekTarget = C.usToMs(currentMediaClock.getPositionUs());

                        Log.d(TAG, "handleMessage STARTSEEK - mode " + currentTrickMode + " position: " + contentPosition + " request position " + seekTarget);

                        player.seekTo(seekTarget);
                    } else {
                        player.setSeekParameters(SeekParameters.DEFAULT);
                    }
                    break;

                case MSG_TRICKPLAY_FRAMERENDER:
                    AnalyticsListener.EventTime renderTime = (AnalyticsListener.EventTime) msg.obj;
                    // Compute next due time, <0 is considered now by sendMessageDelayed
                    //
                    long nextFrameDue = IDR_INTERVAL_TARGET_MS - (renderTime.realtimeMs - lastSeekProcessed.realtimeMs);
                    Log.d(TAG, "handleMessage FRAMERENDER - mode " + currentTrickMode + " position: " + renderTime.eventPlaybackPositionMs
                            + " renderTime:" + renderTime.realtimeMs + " lastSeekTime: " + lastSeekProcessed.realtimeMs
                            + " nextFrameDue: " + nextFrameDue);

                    currentHandler.sendEmptyMessageDelayed(MSG_TRICKPLAY_STARTSEEK, nextFrameDue);

                    break;

                case MSG_TRICKPLAY_SEEKPROCESSED:
                    AnalyticsListener.EventTime eventTime = (AnalyticsListener.EventTime) msg.obj;
                    Log.d(TAG, "handleMessage SEEKPROCESSED - mode " + currentTrickMode + " position: " + eventTime.eventPlaybackPositionMs);
                    lastSeekProcessed = eventTime;
                    break;


            }
        }
    }

    /**
     * Default speeds used to initialize the map.  These are the speeds that
     * are used until it is known that smooth fast play is possible
     *
     * @param mode
     * @return
     */
    private Float getDefaultSpeedForMode(TrickMode mode) {
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

    @Override
    public Float getSpeedFor(TrickMode mode) {
        return speedsForMode.get(mode);
    }

    private void switchToTrickPlayTracks() {
        DefaultTrackSelector.Parameters trackSelectorParameters = trackSelector.getParameters();
        DefaultTrackSelector.ParametersBuilder builder = trackSelectorParameters.buildUpon();

        TrackSelectionArray trackSelections = player.getCurrentTrackSelections();
        for (int i = 0; i < player.getRendererCount() && i < trackSelections.length; i++) {
          if (player.getRendererType(i) == C.TRACK_TYPE_AUDIO && trackSelections.get(i) != null) {
              builder.setRendererDisabled(i, true);
          }
        }
        trackSelector.setParameters(builder);
    }

    private void destroyTrickPlayMessageHandler() {
        currentHandler = null;
        player.setSeekParameters(SeekParameters.DEFAULT);
    }

    private void startTrickPlayMessageHandler() {
        destroyTrickPlayMessageHandler();
        currentHandler = new TrickPlayMessageHandler(player.getContentPosition());
        currentHandler.sendEmptyMessage(TrickPlayMessageHandler.MSG_TRICKPLAY_STARTSEEK);
    }

    private void enableLastSelectedAudioTrack() {
        if (lastSelectedAudioTrack >= 0 && lastSelectedAudioTrack < player.getRendererCount()) {
            DefaultTrackSelector.Parameters trackSelectorParameters = trackSelector.getParameters();
            DefaultTrackSelector.ParametersBuilder builder = trackSelectorParameters.buildUpon();
            builder.setRendererDisabled(lastSelectedAudioTrack, false);
            trackSelector.setParameters(builder);
        }
    }

    @Override
    public RenderersFactory createRenderersFactory(Context context) {
//        return new TrickPlayRendererFactory(context, this);
        return new DefaultRenderersFactory(context);
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
        playerEventListener = new PlayerEventListener();
        this.player.addAnalyticsListener(playerEventListener);
    }

    private void removePlayerReference() {
        destroyTrickPlayMessageHandler();
        this.listeners.clear();
        this.player.removeAnalyticsListener(playerEventListener);
        this.player = null;
    }

    @Override
    public synchronized TrickMode getCurrentTrickMode() {
        return currentTrickMode;
    }

    synchronized void setCurrentTrickMode(TrickMode mode) {
        currentTrickMode = mode;
    }

    @Override
    public TrickMode setTrickMode(TrickMode newMode) {

        if (newMode != getCurrentTrickMode()) {
            float speed = getSpeedFor(newMode);

            // Pause playback
            Log.d(TAG, "setTrickMode(" + newMode + ") pausing playback");

            player.setPlayWhenReady(false);

            setCurrentTrickMode(newMode);

            /*
             * If speed is in range to play all frames (not just iFrames), disable audio and let
             * the DefaultMediaClock handle.  Restore audio and reset playback parameters for normal,
             * otherwise use seek based.
             *
             */
            switch (newMode) {
                case FF1:
                case FF2:
                case FF3:
                    Log.d(TAG, "Start trickplay " + newMode + " at media time " + player.getCurrentPosition());
                    destroyTrickPlayMessageHandler();
                    switchToTrickPlayTracks();
                    player.setPlayWhenReady(true);
                    player.setPlaybackParameters(new PlaybackParameters(speed));
                    break;

                case NORMAL:
                    destroyTrickPlayMessageHandler();
                    player.setPlaybackParameters(PlaybackParameters.DEFAULT);

                    long currentPosition = player.getCurrentPosition();

                    Log.d(TAG, "Stop trickplay at media time " + currentPosition);

                    enableLastSelectedAudioTrack();

                    // Seek 1us off from the current position, forces a discontinuity which empties the decoder
                    // which may only have i-frames buffered.
                    //
                    player.seekTo(currentPosition > 0 ? currentPosition - 1 : currentPosition + 1);

                    player.setPlayWhenReady(true);
                    break;

                default:
                    Log.d(TAG, "Start trickplay " + newMode + " at media time " + player.getCurrentPosition());

                    switchToTrickPlayTracks();
                    player.setPlayWhenReady(false);
                    startTrickPlayMessageHandler();
                    break;

            }


        }
        return newMode;
    }

    @Override
    public void addEventListener(TrickPlayEventListener eventListener) {
        listeners.add(eventListener);
    }

    @Override
    public void removeEventListener(TrickPlayEventListener eventListener) {
        listeners.remove(eventListener);
    }
}
