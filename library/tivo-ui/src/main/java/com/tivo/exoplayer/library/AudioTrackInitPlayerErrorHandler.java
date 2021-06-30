package com.tivo.exoplayer.library;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.tivo.exoplayer.library.PlayerErrorRecoverable;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;
import com.tivo.exoplayer.library.errorhandlers.PlaybackExceptionRecovery;

public class AudioTrackInitPlayerErrorHandler implements PlaybackExceptionRecovery {
  private static final String TAG = "AudioTrackInitPlayerErrorHandler";
  public static final int MAX_ERROR_RETRIES = 3;
  private final PlayerErrorRecoverable playerErrorRecoverable;

  /**
   * Number of attempts to recover from an error, reset when recovery completes, is canceled or
   * playback error is determined to be fatal (retries exceeded)
   */
  @VisibleForTesting
  int errorRetryCount;
  private @Nullable ExoPlaybackException currentError;

  public AudioTrackInitPlayerErrorHandler(PlayerErrorRecoverable playerErrorRecoverable) {
    this.playerErrorRecoverable = playerErrorRecoverable;
  }

  @Nullable
  @Override
  public ExoPlaybackException currentErrorBeingHandled() {
    return currentError;
  }

  @Override
  public boolean isRecoveryInProgress() {
    return errorRetryCount > 0;
  }

  @Override
  public boolean isRecoveryFailed() {
    return errorRetryCount == MAX_ERROR_RETRIES;
  }

  @Override
  public boolean checkRecoveryCompleted() {
    SimpleExoPlayer player = playerErrorRecoverable.getCurrentPlayer();
    boolean isRecovered = player != null && player.isPlaying();
    if (isRecovered) {
      Log.d(TAG, "recovery completed, resetting error retry count from " + errorRetryCount + " to zero");
      errorRetryCount = 0;
      currentError = null;
    }
    return isRecovered;
  }

  @Override
  public boolean recoverFrom(ExoPlaybackException e) {
    boolean handled = false;

    if (e.type == ExoPlaybackException.TYPE_RENDERER) {
      Exception renderException = e.getRendererException();
      if (renderException instanceof AudioSink.InitializationException) {
        AudioSink.InitializationException initException = (AudioSink.InitializationException) renderException;

        if (initException.audioTrackState == 0 && e.rendererFormat != null && Util
            .areEqual(e.rendererFormat.sampleMimeType, "audio/ac3")) {
          if (++errorRetryCount > MAX_ERROR_RETRIES) {
            Log.w(SimpleExoPlayerFactory.TAG,
                "Retry count exceeded, failing for AudioSink.InitializationException for AC3 audio.",
                initException);
          } else {
            Log.w(SimpleExoPlayerFactory.TAG,
                "Attempting to recover AudioSink.InitializationException for AC3 audio. retry count: "
                    + errorRetryCount, initException);
            if (playerErrorRecoverable.isTunnelingMode()) {
              playerErrorRecoverable.setTunnelingMode(false);
            }
            playerErrorRecoverable.retryPlayback();
            handled = true;
            currentError = e;
          }
        }
      }
    }

    return handled;
  }
}