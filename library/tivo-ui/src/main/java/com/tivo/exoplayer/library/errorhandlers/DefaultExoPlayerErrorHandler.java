package com.tivo.exoplayer.library.errorhandlers;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.util.Log;

import java.util.List;

/**
 * ExoPlayer reports errors via the {@link Player.EventListener#onPlayerError(ExoPlaybackException)}
 * method.  The errors reported to this method may be recovered, the player is transitions to the
 * {@link Player#STATE_IDLE} and playback stops.
 *
 * This handler listens to the @link Player.EventListener} and calls handlers that implement
 * {@link PlaybackExceptionRecovery}, in the order added until the list is exhausted or one of
 * the handlers returns true from {@link PlaybackExceptionRecovery#recoverFrom(ExoPlaybackException)}
 *
 */
public class DefaultExoPlayerErrorHandler implements Player.EventListener {

  private static final String TAG = "ExoPlayerErrorHandler";
  private final List<PlaybackExceptionRecovery> handlers;

  @Nullable private final PlayerErrorHandlerListener playerErrorHandlerListener;

  /**
   * If you subclass implement this method. You can choose to add to the list of
   * {@link PlaybackExceptionRecovery} handlers, it is recommmeded you add to the end of the
   * list.  Most all common errors that ExoPlayer indicates can be recovered are handled
   * (eg. {@link com.google.android.exoplayer2.source.BehindLiveWindowException}
   *
   * @param handlers ordred list of {@link PlaybackExceptionRecovery} handlers
   * @param playerErrorHandlerListener optional (but recommended) callback for error handling.
   */
  public DefaultExoPlayerErrorHandler(List<PlaybackExceptionRecovery> handlers, @Nullable PlayerErrorHandlerListener playerErrorHandlerListener) {
    this.handlers = handlers;
    this.playerErrorHandlerListener = playerErrorHandlerListener;
  }

  public void releaseResources() {
    for (PlaybackExceptionRecovery handler : handlers) {
      handler.releaseResources();
    }
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    for (PlaybackExceptionRecovery handler : handlers) {
      if (handler.isRecoveryInProgress()) {
        handler.checkRecoveryCompleted();
      }
    }
  }

  @Override
  @CallSuper
  public void onPlayerError(ExoPlaybackException error) {
    Log.w(TAG, "onPlayerError: error: " + error);
    PlaybackExceptionRecovery activeHandler = null;

    for (PlaybackExceptionRecovery handler : handlers) {
      if (handler.recoverFrom(error)) {
        Log.d(TAG, "onPlayerError recovery handler " + handler + " returned true");
        activeHandler = handler;
        break;
      }
    }

    if (activeHandler == null) {
      playerErrorProcessed(error, PlayerErrorHandlerListener.HandlingStatus.FAILED);
    } else {
      reportErrorStatus(activeHandler);
    }
  }

  private void reportErrorStatus(PlaybackExceptionRecovery handler) {
    PlayerErrorHandlerListener.HandlingStatus status = PlayerErrorHandlerListener.HandlingStatus.FAILED;
    ExoPlaybackException error = null;

    if (handler.isRecoveryFailed()) {
      status = PlayerErrorHandlerListener.HandlingStatus.FAILED;
    } else if (handler.isRecoveryInProgress()) {
      error = handler.currentErrorBeingHandled();
      if (handler.checkRecoveryCompleted()) {
        status = PlayerErrorHandlerListener.HandlingStatus.SUCCESS;
      } else {
        status = PlayerErrorHandlerListener.HandlingStatus.IN_PROGRESS;
      }
    }
    if (status != null) {
      playerErrorProcessed(error, status);
    }
  }

  private void playerErrorProcessed(ExoPlaybackException error, PlayerErrorHandlerListener.HandlingStatus status) {
    Log.d(TAG, "playerError was processed, status: " + status);
    if (playerErrorHandlerListener != null) {
      playerErrorHandlerListener.playerErrorProcessed(error, status);
    }
  }
}

