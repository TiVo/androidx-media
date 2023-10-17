package com.tivo.exoplayer.demo.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.util.Log;
import com.tivo.exoplayer.demo.R;

@SuppressLint("AppCompatCustomView")
public class PlaybackStateImageView extends ImageView {
    private static final String TAG = "PlaybackStateImageView";
    private final Map<TrickPlayControl.TrickMode, Drawable> enterFastPlayIcons;
    private final Map<TrickPlayControl.TrickMode, Drawable> exitFastPlayIcons;
    private final Drawable pauseToPlay;
    private final Drawable play;
    private final Drawable pause;
    private final Drawable playToPause;
    private TrickPlayControl.TrickMode currentMode;

    public PlaybackStateImageView(Context context) {
        this(context, null);
    }

    public PlaybackStateImageView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PlaybackStateImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        pauseToPlay = getIconResource(R.drawable.ic_playback_state_pause_to_play);
        pause = getIconResource(R.drawable.ic_playback_state_pause);
        playToPause = getIconResource(R.drawable.ic_playback_state_play_to_pause);
        play = getIconResource(R.drawable.ic_playback_state_play);

        enterFastPlayIcons = new HashMap<>();
        enterFastPlayIcons.put(TrickPlayControl.TrickMode.FF1, getIconResource(R.drawable.ic_playback_state_pause_to_ff1));
        enterFastPlayIcons.put(TrickPlayControl.TrickMode.FF2, getIconResource(R.drawable.ic_playback_state_trickplay_ff1_ff2));
        enterFastPlayIcons.put(TrickPlayControl.TrickMode.FF3, getIconResource(R.drawable.ic_playback_state_trickplay_ff2_ff3));
        enterFastPlayIcons.put(TrickPlayControl.TrickMode.FR1, getIconResource(R.drawable.ic_playback_state_pause_to_fr1));
        enterFastPlayIcons.put(TrickPlayControl.TrickMode.FR2, getIconResource(R.drawable.ic_playback_state_trickplay_fr1_fr2));
        enterFastPlayIcons.put(TrickPlayControl.TrickMode.FR3, getIconResource(R.drawable.ic_playback_state_trickplay_fr2_fr3));

        exitFastPlayIcons = new HashMap<>();
        exitFastPlayIcons.put(TrickPlayControl.TrickMode.FF1, getIconResource(R.drawable.ic_playback_state_trickplay_ff1_to_play));
        exitFastPlayIcons.put(TrickPlayControl.TrickMode.FF2, getIconResource(R.drawable.ic_playback_state_trickplay_ff2_to_play));
        exitFastPlayIcons.put(TrickPlayControl.TrickMode.FF3, getIconResource(R.drawable.ic_playback_state_trickplay_ff3_to_play));
        exitFastPlayIcons.put(TrickPlayControl.TrickMode.FR1, getIconResource(R.drawable.ic_playback_state_trickplay_fr1_to_play));
        exitFastPlayIcons.put(TrickPlayControl.TrickMode.FR2, getIconResource(R.drawable.ic_playback_state_trickplay_fr2_to_play));
        exitFastPlayIcons.put(TrickPlayControl.TrickMode.FR3, getIconResource(R.drawable.ic_playback_state_trickplay_fr3_to_play));
    }

    private Drawable getIconResource(int resId) {
        return getContext().getDrawable(resId);
    }

    private void updateDrawable(Drawable drawable) {
        Drawable previousDrawable = getDrawable();
        setImageDrawable(drawable);
        if (previousDrawable != drawable) {
            Log.d(TAG, "updatingIcon - old: " + previousDrawable + " new: " + drawable);
            if (previousDrawable != null && previousDrawable instanceof AnimatedVectorDrawable) {
                ((AnimatedVectorDrawable) previousDrawable).stop();
            }
            if (drawable instanceof AnimatedVectorDrawable) {
                ((AnimatedVectorDrawable) drawable).start();
            }
        }
    }

    public void trickPlayStateChanged(TrickPlayControl.TrickMode oldMode, TrickPlayControl.TrickMode newMode) {
        Drawable drawable = null;
        Log.d(TAG, "trickPlayStateChanged - from: " + oldMode + " to: " + newMode);
        currentMode = newMode;


        TrickPlayControl.TrickPlayDirection newDirection = TrickPlayControl.directionForMode(newMode);
        TrickPlayControl.TrickPlayDirection oldDirection = TrickPlayControl.directionForMode(oldMode);
        boolean exitingTp = newDirection == TrickPlayControl.TrickPlayDirection.NONE &&
                (oldDirection == TrickPlayControl.TrickPlayDirection.FORWARD || oldDirection == TrickPlayControl.TrickPlayDirection.REVERSE);

        if (exitingTp) {
            drawable = exitFastPlayIcons.get(oldMode);
        } else {
            drawable = enterFastPlayIcons.get(newMode);
        }

        if (drawable != null) {
            updateDrawable(drawable);
        }
    }

    public void showPausedState() {
        if (getDrawable() == null) {
            updateDrawable(pause);
        } else {
            updateDrawable(playToPause);
        }
    }

    public void showPlayState() {
        if (getDrawable() == null) {
            updateDrawable(play);
        } else {
            updateDrawable(pauseToPlay);
        }
    }
}
