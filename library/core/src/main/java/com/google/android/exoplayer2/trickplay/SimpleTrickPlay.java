package com.google.android.exoplayer2.trickplay;

import android.os.Handler;
import android.os.Message;
import android.view.Surface;
import androidx.annotation.Nullable;


import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MediaClock;

/**
 * Implements simple trick-play for video fast forward / reverse without sound.
 *
 * Fast modes are three different rates in each direction including normal playback (which re-enables sound)
 */
public class SimpleTrickPlay {

    public static final String TAG = "TRICK-PLAY";

    private SimpleExoPlayer player;
    private DefaultTrackSelector trackSelector;

    private TrickMode currentTrickMode = TrickMode.NORMAL;
    private AnalyticsListener playerEventListener;
    private int lastSelectedAudioTrack = -1;

    private TrickPlayMessageHandler currentHandler = null;

    /**
     * Trick modes include normal playback and one of 3 fast modes forward and reverse.   The first fast-modes
     * are within the range to allow for playback with changing the source of {@link MediaClock}
     */
    public enum TrickMode {
        FF1, FF2, FF3, NORMAL, FR1, FR2, FR3
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

        }
    }

    private class TrickPlayMessageHandler extends Handler {

        public static final int MSG_TRICKPLAY_STARTSEEK = 1;
        public static final int MSG_TRICKPLAY_FRAMERENDER = 2;
        public static final int MSG_TRICKPLAY_SEEKPROCESSED = 3;
        public static final int MSG_TRICKPLAY_SEEKSTARTED = 3;

        public static final int TARGET_FPS = 3;
        public static final int IDR_INTERVAL_TARGET_MS = 1000 / TARGET_FPS;

        private long startingPosition;
        private long positionAtLastSeek;
        private AnalyticsListener.EventTime lastSeekProcessed;

        TrickPlayMessageHandler(long startingPosition) {
            this.startingPosition = startingPosition;
            this.positionAtLastSeek = startingPosition;
            player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case MSG_TRICKPLAY_STARTSEEK:

                    if (currentTrickMode != TrickMode.NORMAL) {
                        long contentPosition = player.getContentPosition();

                        long positionStepMs = getSpeedFor(currentTrickMode).longValue() * 1000;
                        startingPosition += positionStepMs;

                        Log.d(TAG, "handleMessage STARTSEEK - mode " + currentTrickMode + " position: " + contentPosition + " request position " + startingPosition);

                        player.seekTo(startingPosition);
                    } else {
                        player.setSeekParameters(SeekParameters.DEFAULT);
                    }
                    break;

                case MSG_TRICKPLAY_FRAMERENDER:
                    AnalyticsListener.EventTime renderTime = (AnalyticsListener.EventTime) msg.obj;
                    Log.d(TAG, "handleMessage FRAMERENDER - mode " + currentTrickMode + " position: " + renderTime.eventPlaybackPositionMs
                            + " request position " + startingPosition + " renderTime:" + renderTime.realtimeMs + " lastSeekTime: " + lastSeekProcessed.realtimeMs);

                    // Compute next due time, <0 is considered now by sendMessageDelayed
                    //
                    long nextFrameDue = IDR_INTERVAL_TARGET_MS - (renderTime.realtimeMs - lastSeekProcessed.realtimeMs);

                    Log.d(TAG, "handleMessage issue seek, nextFrameDue:  " + nextFrameDue);

                    currentHandler.sendEmptyMessageDelayed(MSG_TRICKPLAY_STARTSEEK, nextFrameDue);

                    break;

                case MSG_TRICKPLAY_SEEKPROCESSED:
                    AnalyticsListener.EventTime eventTime = (AnalyticsListener.EventTime) msg.obj;
                    Log.d(TAG, "handleMessage SEEKPROCESSED - mode " + currentTrickMode + " position: " + eventTime.eventPlaybackPositionMs + " request position " + startingPosition);
                    lastSeekProcessed = eventTime;
                    break;


            }
        }
    }


    private Float getSpeedFor(TrickMode mode) {
        float speed = 0.0f;

        switch (mode) {
            case FF1:
            case FR1:
                speed = 12.0f;
                break;

            case FF2:
            case FR2:
                speed = 30.0f;
                break;

            case FF3:
            case FR3:
                speed = 60.0f;
                break;

            case NORMAL:
                speed = 1.0f;
                break;
        }

        switch (mode) {
            case FR1:
            case FR2:
            case FR3:
                speed = - speed;
                break;
        }
        return speed;
    }

    private void setAudioTracksDisabled() {
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

    public SimpleTrickPlay(SimpleExoPlayer player, DefaultTrackSelector trackSelector) {
        this.trackSelector = trackSelector;
        setPlayer(player);
    }

    public void setPlayer(SimpleExoPlayer player) {
        currentTrickMode = TrickMode.NORMAL;
        if (player == null) {
            destroyTrickPlayMessageHandler();
            this.player.removeAnalyticsListener(playerEventListener);
            this.player = null;
        } else {
            this.player = player;
            playerEventListener = new PlayerEventListener();
            this.player.addAnalyticsListener(playerEventListener);
        }
    }

    public TrickMode getCurrentTrickMode() {
        return currentTrickMode;
    }

    public TrickMode setTrickMode(TrickMode newMode) {

        if (newMode != currentTrickMode) {
            float speed = getSpeedFor(newMode);

            /*
             * If speed is in range to play all frames (not just iFrames), disable audio and let
             * the DefaultMediaClock handle.  Restore audio and reset playback parameters for normal,
             * otherwise use seek based.
             *
             */
            switch (newMode) {
                case FF1:
                    destroyTrickPlayMessageHandler();
                    setAudioTracksDisabled();
                    player.setPlaybackParameters(new PlaybackParameters(speed));
                    break;

                case NORMAL:
                    destroyTrickPlayMessageHandler();
                    enableLastSelectedAudioTrack();
                    player.setPlaybackParameters(PlaybackParameters.DEFAULT);
                    player.setPlayWhenReady(true);
                    break;

                default:
                    setAudioTracksDisabled();
                    player.setPlayWhenReady(false);
                    startTrickPlayMessageHandler();
                    break;

            }


            currentTrickMode = newMode;
        }
        return newMode;
    }

}
