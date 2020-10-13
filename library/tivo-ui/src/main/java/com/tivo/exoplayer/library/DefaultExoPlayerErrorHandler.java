package com.tivo.exoplayer.library;

import android.view.Surface;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Log;
import java.io.IOException;
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

  @Nullable protected PlayerErrorHandlerListener playerErrorHandlerListener;

  /**
   * If you add a handler to the list it must implement this interface.
   */
  public interface PlaybackExceptionRecovery {

    /**
     * Handlers are called with the exception, returning true stops the rest of
     * the handlers on the list from being called
     *
     * @param e the {@link ExoPlaybackException} signaled
     * @return true to stop the recovery chain (assumes your handler recovered from it)
     */
    boolean recoverFrom(ExoPlaybackException e);


    /**
     * Called when playback starts (or restarts) successfully to allow stateful
     * error recover (e.g. retry count based) to reset any internal state.
     */
    default void recoveryComplete() {};

    /**
     * Called when the {@link SimpleExoPlayerFactory} destroys the player, use this
     * to release any listeners
     */
    default void releaseResources() {};
  }

  /**
   * If you subclass implement this method. You can choose to add to the list of
   * {@link PlaybackExceptionRecovery} handlers, it is recommmeded you add to the end of the
   * list.  Most all common errors that ExoPlayer indicates can be recovered are handled
   * (eg. {@link com.google.android.exoplayer2.source.BehindLiveWindowException}
   *
   * @param handlers ordred list of {@link PlaybackExceptionRecovery} handlers
   */
  public DefaultExoPlayerErrorHandler(List<PlaybackExceptionRecovery> handlers) {
    this.handlers = handlers;
  }

  public void releaseResources() {
    for (PlaybackExceptionRecovery handler : handlers) {
      handler.releaseResources();
    }
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    // Any playback state change to READY resets error state
    if (playbackState == Player.STATE_READY) {

      for (PlaybackExceptionRecovery handler : handlers) {
        handler.recoveryComplete();
      }
    }
  }

  @Override
  @CallSuper
  public void onPlayerError(ExoPlaybackException error) {
    Log.w(TAG, "onPlayerError: error: " + error);
    boolean recovered = false;

    for (PlaybackExceptionRecovery handler : handlers) {
      if (handler.recoverFrom(error)) {
        Log.d(TAG, "onPlayerError recovery returned success");
        recovered = true;
        break;
      }
    }

    playerErrorProcessed(error, recovered);
  }

  /**
   * This is the hook for subclasses to be notified of the playback error
   * from {@link #onPlayerError(ExoPlaybackException)} and the status of attempts to
   * recover from the error.
   *
   * @param error the acutal reported {@link ExoPlaybackException}
   * @param recovered true if recovery handler handled the error
   */
  protected void playerErrorProcessed(ExoPlaybackException error, boolean recovered) {
    Log.d(TAG, "playerError was processed, " + (recovered ? "recovery succeed" : "recovery failed."));
    if (playerErrorHandlerListener != null) {
      playerErrorHandlerListener.playerErrorProcessed(error, recovered);
    }
  }
}

