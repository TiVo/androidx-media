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
 * This handler listens to the {@link AnalyticsListener}  interface
 * which includes events from the {@link Player.EventListener} as well as the
 * {@link com.google.android.exoplayer2.source.MediaSourceEventListener} events which indicate
 * load errors that may eventually become ExoPlaybackExceptions.
 *
 */
public class DefaultExoPlayerErrorHandler implements AnalyticsListener {

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
  public void onPlayerStateChanged(EventTime eventTime, boolean playWhenReady, int playbackState) {
    // Any playback state change to READY resets error state
    if (playbackState == Player.STATE_READY) {

      for (PlaybackExceptionRecovery handler : handlers) {
        handler.recoveryComplete();
      }
    }
  }

  @Override
  @CallSuper
  public void onPlayerError(EventTime eventTime, ExoPlaybackException error) {
    Log.w(TAG, "onPlayerError: eventTime: " + eventTime + ", error: " + error);
    boolean recovered = false;

    for (PlaybackExceptionRecovery handler : handlers) {
      if (handler.recoverFrom(error)) {
        Log.d(TAG, "onPlayerError recovery returned success");
        recovered = true;
        break;
      }
    }

    playerErrorProcessed(eventTime, error, recovered);
  }

  /**
   * This is the hook for subclasses to be notified of the playback error
   * from {@link #onPlayerError(EventTime, ExoPlaybackException)} and the status of attempts to
   * recover from the error.
   *
   * @param eventTime time and details for the error event.
   * @param error the acutal reported {@link ExoPlaybackException}
   * @param recovered true if recovery handler handled the error
   */
  protected void playerErrorProcessed(EventTime eventTime, ExoPlaybackException error, boolean recovered) {
    Log.d(TAG, "playerError was processed, " + (recovered ? "recovery succeed" : "recovery failed."));
    if (playerErrorHandlerListener != null) {
      playerErrorHandlerListener.playerErrorProcessed(eventTime, error, recovered);
    }
  }

  @Override
  public void onLoadError(EventTime eventTime, MediaSourceEventListener.LoadEventInfo loadEventInfo,
      MediaSourceEventListener.MediaLoadData mediaLoadData, IOException error,
      boolean wasCanceled) {
    Log.d(TAG, "onLoadError - URL: " + loadEventInfo.uri + " io error: "+error);
  }
}

