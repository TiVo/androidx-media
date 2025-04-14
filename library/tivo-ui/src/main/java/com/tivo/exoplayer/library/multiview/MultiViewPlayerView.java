package com.tivo.exoplayer.library.multiview;

import android.content.Context;
import android.util.AttributeSet;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.Log;
import com.tivo.exoplayer.library.R;

/**
 * A custom PlayerView that extends the default PlayerView to handle UX
 * specific to the multi-view use case.
 *
 */
public class MultiViewPlayerView extends PlayerView implements Player.Listener {

  private static final String TAG = "MultiViewPlayerView";

  // The delay before showing the buffering spinner when the player is buffering.
  // this default of 3 seconds is enough to avoid showing the spinner when changing
  // the selected player for audio (which could involve a track selection).
  //
  static final int DEFAULT_BUFFERING_INDICATOR_DELAY = 3000;

  @Nullable Player currentPlayer;
  private int bufferingIndicatorDelay = DEFAULT_BUFFERING_INDICATOR_DELAY;
  private PlaybackErrorView errorView;

  public MultiViewPlayerView(Context context) {
    this(context, null);
  }

  public MultiViewPlayerView(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public MultiViewPlayerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    // We will detect buffering state changes ourselves, and only after a timeout
    // show the buffering spinner. This is to avoid flickering of the spinner when buffering
    // is only for a short time.
    setShowBuffering(SHOW_BUFFERING_NEVER);

    errorView = findViewById(R.id.error_recovery_view);
  }

  /**
   * Sets the delay before showing the buffering spinner when the player is buffering.
   * This is the minimum amount of time in buffering state before the spinner is shown.
   *
   * @param delay The delay in milliseconds.
   */
  public void setBufferingIndicatorDelay(int delay) {
    bufferingIndicatorDelay = delay;
  }

  // Overrides from PlayerView

  @Override
  public void setPlayer(@Nullable Player player) {
    super.setPlayer(player);

    if (player != null) {
      player.addListener(this);
    } else if (currentPlayer != null) {
      currentPlayer.removeListener(this);
      errorView.onErrorStateChanged(Player.STATE_IDLE, currentPlayer);
    }

    currentPlayer = player;
  }

  // Listen to playback state changes to show/hide the buffering spinner

  @Override
  public void onPlaybackStateChanged(int playbackState) {
    Log.d(TAG, "onPlaybackStateChanged: " + playbackState);
    if (playbackState == Player.STATE_BUFFERING) {
      updateBufferingStarted();
    } else {
      // Playback started or ended, hide the buffering spinner
      updateBufferingEnded();
    }

    errorView.onErrorStateChanged(playbackState, currentPlayer);
  }

  private void updateBufferingEnded() {
    removeCallbacks(this::displayBufferingIndicator);
    setShowBuffering(SHOW_BUFFERING_NEVER);
  }

  // Internal methods
  private void updateBufferingStarted() {
    // Show the buffering spinner after a timeout
    postDelayed(this::displayBufferingIndicator, bufferingIndicatorDelay);
  }

  private void displayBufferingIndicator() {
    Log.d(TAG, "displayBufferingIndicator - " + (currentPlayer == null ? "no player" : currentPlayer.getPlaybackState()));
    if (currentPlayer != null && currentPlayer.getPlaybackState() == Player.STATE_BUFFERING) {
      setShowBuffering(SHOW_BUFFERING_WHEN_PLAYING);
    }
  }

  public @Nullable PlaybackErrorView getPlaybackErrorView() {
    return errorView;
  }
}
