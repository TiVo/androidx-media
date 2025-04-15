package com.tivo.exoplayer.library.errorhandlers;

import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_DECODING_FAILED;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.util.Log;

/**
 * Handles the recovery from MediaCodecVideoDecoderException errors in ExoPlayer.
 * Not all deocder errors are handled. Only the {@link MediaCodec.IllegalStateException} is handled.
 */
public class MediaCodecVideoDecoderExceptionErrorHandler implements PlaybackExceptionRecovery {
  private static final String TAG = "MediaCodecExceptionErrorHandler";
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
    boolean canRecover = (error.errorCode == ERROR_CODE_DECODING_FAILED &&
                         PlaybackExceptionRecovery.isMediaCodecErrorRecoverable(error) &&
                         errorRetryCount++ < MAX_ERROR_RETRIES);
    if (canRecover) {
      Log.i(TAG, "recoverFrom() - error count " + errorRetryCount + " canRecover: " + canRecover);
      playerErrorRecoverable.retryPlayback();
      currentError = error;
    }
    return canRecover;
  }
}
