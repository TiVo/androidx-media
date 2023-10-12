package com.tivo.exoplayer.library.timebar.controllers;

import android.view.KeyEvent;
import androidx.annotation.NonNull;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.Log;
import com.tivo.exoplayer.library.timebar.views.DualModeTimeBar;

public class ScrubOnlyTimeBarViewHandler extends BaseTimeBarViewHandler {
  public static final String TAG = "ScrubOnlyTimeBarViewHandler";

  public ScrubOnlyTimeBarViewHandler(TrickPlayControl control,
      @NonNull PlayerView playerView, @NonNull DualModeTimeBar timeBar,
      SimpleExoPlayer simpleExoPlayer) {
    super(control, playerView, timeBar, simpleExoPlayer);
  }

  @Override
  public boolean showForEvent(KeyEvent event) {
    Log.d(TAG, "showForEvent - playWhenReady: " + player.getPlayWhenReady() + " trickMode: " + control.getCurrentTrickMode() + " dualMode: " + getCurrentDualMode() + " event: " + event);

    boolean triggered = super.showForEvent(event);

    if (triggered) {
      setCurrentDualMode(DualModeMode.SCRUB);
      player.setPlayWhenReady(false);
    } else {
      setCurrentDualMode(DualModeMode.NONE);
    }
    return triggered;
  }
}
