package com.tivo.exoplayer.demo;

import android.content.Context;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.trickplay.TrickPlayEventListener;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.util.Log;
import com.tivo.exoplayer.demo.views.PlaybackStateImageView;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages the View state for the Trick-Play control buttons, transport (Play/Pause) focus
 * and scrub bar, and transport buttons
 */
public class TransportControlHandler implements TrickPlayEventListener, Player.EventListener {
    private static final String TAG = "TransportControlHandler";
    private final View timeBar;
    private final ImageButton ffButton;
    private final ImageButton rwdButton;
    private final Map<TrickPlayControl.TrickMode, Drawable> trickPlayButtonImages;
    private TrickPlayControl trickPlayControl;
    private SimpleExoPlayer player;
    private ButtonListener handler;
    private final PlaybackStateImageView playbackStateView;

    private class ButtonListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            if (player != null) {
                if (v == ffButton) {
                    handleFastForward();
                } else if (v == rwdButton) {
                    handleRewind();
                }
            }
        }
    }

    public TransportControlHandler(View playerView, Context context) {
        ffButton = playerView.findViewById(R.id.fast_forward);
        rwdButton = playerView.findViewById(R.id.rewind);

        handler = new ButtonListener();
        ffButton.setOnClickListener(handler);
        rwdButton.setOnClickListener(handler);
        trickPlayButtonImages = new HashMap<>();

        trickPlayButtonImages.put(TrickPlayControl.TrickMode.FF1, context.getDrawable(R.drawable.ic_fwd_1_x));
        trickPlayButtonImages.put(TrickPlayControl.TrickMode.FF2, context.getDrawable(R.drawable.ic_fwd_2_x));
        trickPlayButtonImages.put(TrickPlayControl.TrickMode.FF3, context.getDrawable(R.drawable.ic_fwd_3_x));
        trickPlayButtonImages.put(TrickPlayControl.TrickMode.FR1, context.getDrawable(R.drawable.ic_rwd_1_x));
        trickPlayButtonImages.put(TrickPlayControl.TrickMode.FR2, context.getDrawable(R.drawable.ic_rwd_2_x));
        trickPlayButtonImages.put(TrickPlayControl.TrickMode.FR3, context.getDrawable(R.drawable.ic_rwd_3_x));

        timeBar = playerView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_progress);
        playbackStateView = playerView.findViewById(R.id.playback_state_view);
    }

    public void setPlayer(SimpleExoPlayer player, TrickPlayControl trickPlayControl) {
        this.player = player;
        this.trickPlayControl = trickPlayControl;
        this.trickPlayControl.addEventListener(this);
        this.player.addListener(this);
    }

    public void playerDestroyed() {
        trickPlayControl.removeEventListener(this);
        player.removeListener(this);
        player = null;
        trickPlayControl = null;
    }


    private void handleRewind() {
        switch (trickPlayControl.getCurrentTrickMode()) {
            case FR1:
                trickPlayControl.setTrickMode(TrickPlayControl.TrickMode.FR2);
                break;

            case FR2:
                trickPlayControl.setTrickMode(TrickPlayControl.TrickMode.FR3);
                break;

            case FR3:
                trickPlayControl.setTrickMode(TrickPlayControl.TrickMode.NORMAL);
                timeBar.requestFocus();
                break;

            default:
                trickPlayControl.setTrickMode(TrickPlayControl.TrickMode.FR1);
                break;
        }
    }

    private void handleFastForward() {
        switch (trickPlayControl.getCurrentTrickMode()) {
            case FF1:
                trickPlayControl.setTrickMode(TrickPlayControl.TrickMode.FF2);
                break;

            case FF2:
                trickPlayControl.setTrickMode(TrickPlayControl.TrickMode.FF3);
                break;

            case FF3:
                trickPlayControl.setTrickMode(TrickPlayControl.TrickMode.NORMAL);
                timeBar.requestFocus();
                break;

            default:
                trickPlayControl.setTrickMode(TrickPlayControl.TrickMode.FF1);
                break;
        }
    }

    // TrickplayEventHandler

    @Override
    public void playlistMetadataValid(boolean isMetadataValid) {
        if (trickPlayControl != null) {
            ffButton.setEnabled(trickPlayControl.isSmoothPlayAvailable());
            rwdButton.setEnabled(trickPlayControl.isSmoothPlayAvailable());
        }
    }

    @Override
    public void trickPlayModeChanged(TrickPlayControl.TrickMode newMode, TrickPlayControl.TrickMode prevMode) {
        switch (TrickPlayControl.directionForMode(prevMode)) {
            case FORWARD:
                rwdButton.setImageResource(R.drawable.ic_rwd_all_filled);
                break;

            case REVERSE:
                ffButton.setImageResource(R.drawable.ic_ff_all_filled);
                break;
        }
        switch (TrickPlayControl.directionForMode(newMode)) {
            case FORWARD:
                updateTPButtonForMode(newMode, ffButton);
                break;

            case REVERSE:
                updateTPButtonForMode(newMode, rwdButton);
                break;

            case SCRUB:
            case NONE:
                updateTPButtonsDefault();
                break;
        }
        playbackStateView.trickPlayStateChanged(prevMode, newMode);
    }

    /**
     * Handle the MEDIA event KeyCodes.  In addition to the ones handled by {@link PlayerControlView},
     * also augumenting what PlayerControlView does (like ending trickplay when PLAY button is
     * pressed.
     *
     * @param event KeyEvent to possibly handle
     * @return true if it was dispatched here.
     */
    public boolean handleFunctionKeys(KeyEvent event) {
        boolean handled = false;

        if (trickPlayControl == null || player == null) {
            handled = false;
        } else {

            if (event.getAction() == KeyEvent.ACTION_UP) {
                int keyCode = event.getKeyCode();

                switch (keyCode) {
                    case KeyEvent.KEYCODE_MEDIA_STEP_FORWARD:

                        long targetPositionMs = player.getCurrentPosition();

                        int currentPositionMinutes = (int) Math.floor(player.getCurrentPosition() / 60_000);
                        int minutesIntoPeriod = currentPositionMinutes % 15;
                        switch (trickPlayControl.getCurrentTrickDirection()) {
                            case FORWARD:
                                targetPositionMs = (currentPositionMinutes + (15 - minutesIntoPeriod)) * 60_000;
                                break;

                            case REVERSE:
                                targetPositionMs = (currentPositionMinutes - (15 - minutesIntoPeriod)) * 60_000;
                                break;

                            case NONE:
                                targetPositionMs = player.getCurrentPosition() + 20_000;
                                break;
                        }

                        ViewActivity.boundedSeekTo(player, trickPlayControl, targetPositionMs);

                        handled = true;

                        break;

                    case KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD:
                        if (trickPlayControl.getCurrentTrickMode() == TrickPlayControl.TrickMode.NORMAL) {
                            ViewActivity.boundedSeekTo(player, trickPlayControl, player.getContentPosition() - 20_000);
                        }
                        handled = true;
                        break;

                    case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    case KeyEvent.KEYCODE_MEDIA_REWIND:
                        TrickPlayControl.TrickMode newMode = nextTrickMode(trickPlayControl.getCurrentTrickMode(), keyCode);

                        if (trickPlayControl.setTrickMode(newMode) >= 0) {
                            Log.d(TAG, "request to play trick-mode " + newMode + " succeeded");
                        } else {
                            Log.d(TAG, "request to play trick-mode " + newMode + " not possible");
                        }
                        handled = true;
                        break;

                    case KeyEvent.KEYCODE_3:
                        trickPlayControl.setTrickMode(TrickPlayControl.TrickMode.FF1);
                        handled = true;
                        break;

                    case KeyEvent.KEYCODE_1:
                        trickPlayControl.setTrickMode(TrickPlayControl.TrickMode.FR1);
                        handled = true;
                        break;

                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    case KeyEvent.KEYCODE_2:
                        if (trickPlayControl.getCurrentTrickMode() != TrickPlayControl.TrickMode.NORMAL) {
                            trickPlayControl.setTrickMode(TrickPlayControl.TrickMode.NORMAL);
                            handled = true;
                        }
                        break;

                    case KeyEvent.KEYCODE_6:
                        trickPlayControl.setTrickMode(TrickPlayControl.TrickMode.FF2);
                        handled = true;
                        break;

                    case KeyEvent.KEYCODE_4:
                        trickPlayControl.setTrickMode(TrickPlayControl.TrickMode.FR2);
                        handled = true;
                        break;
                }
            }
        }

        return handled;
    }

    // Player.EventListener

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
//        updatePlaybackStateDisplay();
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        Log.d(TAG, "onIsPlayingChanged(" + isPlaying + ") - state: " + player.getPlaybackState()
                + " mode: " + trickPlayControl.getCurrentTrickMode() + " playWhenReady: " + player.getPlayWhenReady());

        switch (trickPlayControl.getCurrentTrickDirection()) {
            case NONE:
            case SCRUB:
                if (isPlayOrPause() && player.getPlayWhenReady()) {
                    playbackStateView.showPlayState();
                } else if (isPaused()) {
                    playbackStateView.showPausedState();
                }
                break;
            default:
                break;
        }
    }

    // Internal methods

    private boolean isPlayOrPause() {
        return player != null &&
                (player.getPlaybackState() == Player.STATE_READY || player.getPlaybackState() == Player.STATE_BUFFERING);
    }

    private boolean isPaused() {
        return isPlayOrPause() && !player.getPlayWhenReady();
    }

    /**
     * Get the UX expected trick mode if sequencing through the modes with a single media
     * forward or media rewind key.
     *
     * @param currentMode - current trickplay mode
     * @param keyCode - key event with indicated direction
     * @return next TrickMode to set
     */
    private static TrickPlayControl.TrickMode nextTrickMode(TrickPlayControl.TrickMode currentMode, int keyCode) {
        TrickPlayControl.TrickMode value;

        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                switch (currentMode) {
                    case NORMAL:
                        value = TrickPlayControl.TrickMode.FF1;
                        break;
                    case FF1:
                        value = TrickPlayControl.TrickMode.FF2;
                        break;
                    case FF2:
                        value = TrickPlayControl.TrickMode.FF3;
                        break;
                    case FF3:    // FF3 keeps going in FF3
                        value = TrickPlayControl.TrickMode.FF3;
                        break;
                    default:    // FR mode with FF keypress goes back to normal
                        value = TrickPlayControl.TrickMode.NORMAL;
                        break;
                }
                break;

            case KeyEvent.KEYCODE_MEDIA_REWIND:
                switch (currentMode) {
                    case NORMAL:
                        value = TrickPlayControl.TrickMode.FR1;
                        break;
                    case FR1:
                        value = TrickPlayControl.TrickMode.FR2;
                        break;
                    case FR2:
                        value = TrickPlayControl.TrickMode.FR3;
                        break;
                    case FR3:    // FR3 keeps going in FR3
                        value = TrickPlayControl.TrickMode.FR3;
                        break;
                    default:    // FF mode with REW keypress goes back to normal
                        value = TrickPlayControl.TrickMode.NORMAL;
                        break;
                }
                break;

            default:
                throw new RuntimeException("nextTrickMode() must be on FF or REW button");
        }

        Log.d(TAG, "Trickplay in currentMode: " + currentMode + ", next is: " + value);

        return value;
    }

    private void updateTPButtonsDefault() {
        rwdButton.setImageResource(R.drawable.ic_rwd_all_filled);
        ffButton.setImageResource(R.drawable.ic_ff_all_filled);
    }

    private void updateTPButtonForMode(TrickPlayControl.TrickMode mode, ImageButton button) {
        Drawable img = trickPlayButtonImages.get(mode);
        button.setImageDrawable(img);
        if (img instanceof AnimatedVectorDrawable) {
            ((AnimatedVectorDrawable) img).start();
        }
    }
}
