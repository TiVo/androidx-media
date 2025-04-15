package com.tivo.exoplayer.library.errorhandlers;

import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_DECODING_FAILED;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.video.MediaCodecVideoDecoderException;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;

// This class handles the recovery from MediaCodecVideoDecoderException errors in ExoPlayer.
// Not all deocder errors are handled. Only the MediaCodec.IllegalStateException is handled.
public class MediaCodecVideoDecoderExceptionErrorHandler implements PlaybackExceptionRecovery {
  private static final String TAG = "MediaCodecVideoDecoderExceptionErrorHandler";
  public static final int MAX_ERROR_RETRIES = 3;
  private final PlayerErrorRecoverable playerErrorRecoverable;

  @VisibleForTesting
  int errorRetryCount;
  private @Nullable PlaybackException currentError;

  public MediaCodecVideoDecoderExceptionErrorHandler(PlayerErrorRecoverable playerErrorRecoverable) {
    this.playerErrorRecoverable = playerErrorRecoverable;
  }

  @Nullable
  @Override
  public PlaybackException currentErrorBeingHandled() {
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
      Log.d(TAG, "Recovery completed, resetting error retry count from " + errorRetryCount + " to zero");
      errorRetryCount = 0;
      currentError = null;
    }
    return isRecovered;
  }

  @Override
  public boolean recoverFrom(PlaybackException error) {
    boolean handled = false;

    if (error instanceof ExoPlaybackException && error.errorCode == ERROR_CODE_DECODING_FAILED) {
      ExoPlaybackException exoPlaybackException = (ExoPlaybackException) error;
      if (exoPlaybackException.getRendererException() == null) {
        return handled;
      }
      Throwable cause = exoPlaybackException.getRendererException().getCause();
      if (cause == null) {
        return handled;
      }
      Log.i(TAG, "MediaCodecVideoDecoderException: " + cause.toString());
      if (cause instanceof IllegalStateException) {
        Log.w(TAG, "Attempting to recover from video decoder error. Retry count: " + errorRetryCount, exoPlaybackException);
        if (++errorRetryCount > MAX_ERROR_RETRIES) {
          Log.w(SimpleExoPlayerFactory.TAG, "Retry count exceeded, failing for video decoder error.", exoPlaybackException);
        } else {
          playerErrorRecoverable.retryPlayback();
          handled = true;
          currentError = error;
        }
      }
    }
    return handled;
  }
}
