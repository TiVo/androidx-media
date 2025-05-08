package com.tivo.exoplayer.library.errorhandlers;

import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.drm.MediaDrmCallbackException;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Log;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles the recovery from DRM-related errors in ExoPlayer.
 */
public class DrmPlayerErrorHandler implements PlaybackExceptionRecovery {

  private static final String TAG = "DrmPlayerErrorHandler";
  public static final int MAX_ERROR_RETRIES = 3;
  private final PlayerErrorRecoverable playerErrorRecoverable;
  private DrmLicenseServerAuthenticationListener drmLicenseServerAuthenticationListener;
  private int responseCode;
  @VisibleForTesting
  int errorRetryCount;
  private @Nullable PlaybackException currentError;
  private @Nullable MediaItem errorMediaItem;
  private Handler timedRetryHandler = new Handler(Looper.getMainLooper());
  private class TimedRetryAction implements Runnable {
    @Override
    public void run() {
      Log.d(TAG, "do timed retry");
      recoverFrom(currentError);
    }
  }
  private final TimedRetryAction timedRetryAction = new TimedRetryAction();

  public DrmPlayerErrorHandler(PlayerErrorRecoverable playerErrorRecoverable, DrmLicenseServerAuthenticationListener listener) {
    this.playerErrorRecoverable = playerErrorRecoverable;
    this.drmLicenseServerAuthenticationListener = listener;
  }

  @Override
  public void abortRecovery() {
    Log.d(TAG, "abortRecovery() - resetting error retry count from " + errorRetryCount + " to zero");
    resetErrorState();
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
      resetErrorState();
    }
    return isRecovered;
  }

  @Override
  public boolean recoverFrom(PlaybackException error) {
    responseCode = 0;
    boolean canRecover = (isDrmErrorRecoverable(error) && errorRetryCount++ < MAX_ERROR_RETRIES);
    if (canRecover) {
      Log.i(TAG, "recoverFrom() - error count " + errorRetryCount + " canRecover: " + canRecover);
      currentError = error;
      this.updatePlaybackTokenAndRestartPlayback(drmLicenseServerAuthenticationListener.getLatestAuthHeaders());
    }
    return canRecover;
  }

  /**
   * Called when the playback is abandoned and the error handler should reset its state.
   * Abort is requires in most cases. Only exception is when the auth token changes, i.e.
   * {@link MediaItem.DrmConfiguration.requestHeaders} are different
   * @param : the new media item
   * return true if the error handler should abort the current recovery
   */
  @Override
  public boolean abortIfMediaItemChanged(MediaItem mediaItem) {
    boolean requiresAbort = true;
    if (errorMediaItem == null || mediaItem == null) {
      return requiresAbort;
    }
    if (mediaItem.playbackProperties != null) {
      Uri mediItemUri = mediaItem.playbackProperties.uri;
      MediaItem.DrmConfiguration drmConfig = mediaItem.playbackProperties.drmConfiguration;
      if (drmConfig != null && drmConfig.requestHeaders != null) {
        Uri mediaItemLicenseUri = drmConfig.licenseUri;
        /**
         * The logic here is to check if only the token changed within the MediaItem.
         * During channel changes, playback uri will change. The license uri might change too.
         * If any one of these uri is different, we need to abort the recovery.
         * otherwise, we can continue the recovery only if the headers are different.
         */
          if (mediItemUri.equals(errorMediaItem.playbackProperties.uri) &&
                  mediaItemLicenseUri.equals(errorMediaItem.playbackProperties.drmConfiguration.licenseUri) &&
                  !drmConfig.requestHeaders.equals(errorMediaItem.playbackProperties.drmConfiguration.requestHeaders)) {
            // The headers are different, so we can continue the recovery
            Log.i(TAG, "abortIfMediaItemChanged() - property headers are different, continuing recovery");
            requiresAbort = false;
          }
        }
      }
    return requiresAbort;
  }

  private void resetErrorState() {
    errorRetryCount = 0;
    currentError = null;
    responseCode = 0;
    timedRetryHandler.removeCallbacks(timedRetryAction);
    if (errorMediaItem != null) {
      errorMediaItem = null;
    }
  }
  public void updatePlaybackTokenAndRestartPlayback(Map<String, String> headers) {
    Log.d(TAG,"updatePlaybackTokenAndRestartPlayback() - headers: " + headers + " errorRetryCount: " + errorRetryCount);
    // Update the playback token in the current media item
    // and restart playback
    if (playerErrorRecoverable == null) {
      Log.e(TAG, "updatePlaybackTokenAndRestartPlayback() - playerErrorRecoverable is null");
      return;
    }
    // Get the current player
    // and update the media item with new headers
    // and prepare it for playback
    SimpleExoPlayer player = playerErrorRecoverable.getCurrentPlayer();
    if (player != null) {
      MediaItem mediaItem = player.getCurrentMediaItem();
      if (mediaItem != null) {
        Map<String, String> currentHeaders = null;
        if (mediaItem.playbackProperties != null && mediaItem.playbackProperties.drmConfiguration != null) {
          currentHeaders = mediaItem.playbackProperties.drmConfiguration.requestHeaders;
          errorMediaItem = mediaItem;
        }
        // Then set the new media item with updated headers
        MediaItem.Builder builder = mediaItem.buildUpon();
        if (headers != null) {
          builder.setDrmLicenseRequestHeaders(headers);
        }
        if ((currentHeaders != null && currentHeaders.equals(headers))
                && errorRetryCount < MAX_ERROR_RETRIES) {
          // Its the same token. Get the token again.
          // Allow a bit longer for application to to refresh the token.
          // It goes through only 2 iterations with dealy of total (3 + 5)sec
          int delay = (errorRetryCount * 2000) + 1000;
          Log.i(TAG, "updatePlaybackTokenAndRestartPlayback() - token is the same, getting new token with delay: " + delay);
          timedRetryHandler.removeCallbacks(timedRetryAction);
          timedRetryHandler.postDelayed(timedRetryAction, delay);
        } else {
            player.setMediaItem(builder.build());
            // The setMediaItem() method might have called the abortRecovery() method.
          // In that case do not restart playback
            if (isRecoveryInProgress()) {
              // Prepare the player with the new media item
              Log.i(TAG, "updatePlaybackTokenAndRestartPlayback() - preparing player with new media item");
              playerErrorRecoverable.retryPlayback();
            }
        }
      }
    }
  }

  private boolean isDrmErrorRecoverable(PlaybackException error) {
    Log.d(TAG, "isDrmErrorRecoverable() - error code: " + error.errorCode);
    boolean isRecoverable = false;
    if (error.errorCode == ERROR_CODE_DRM_CONTENT_ERROR ||
            error.errorCode == ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED) {
      isRecoverable =  ((error instanceof ExoPlaybackException) &&
                          isRecoverablePlaybackException((ExoPlaybackException)error));
    }
    return isRecoverable;
  }

  private boolean isRecoverablePlaybackException(ExoPlaybackException error) {
    Log.d(TAG, "isRecoverablePlaybackException() - error code: " + error.errorCode);
    boolean isRecoverable = false;
    Exception exception = null;
    if (error instanceof ExoPlaybackException) {
      ExoPlaybackException exoPlaybackException = (ExoPlaybackException) error;
      if (exoPlaybackException.type == ExoPlaybackException.TYPE_SOURCE) {
        Log.d(TAG, "isDrmErrorRecoverable() - DRM error detected TYPE_SOURCE");
        exception = exoPlaybackException.getSourceException();
      } else if (exoPlaybackException.type == ExoPlaybackException.TYPE_RENDERER) {
        Log.d(TAG, "isDrmErrorRecoverable() - DRM error detected TYPE_RENDERER");
        exception = exoPlaybackException.getRendererException();
      }
    }
    isRecoverable = ((exception != null && exception.getCause() instanceof MediaDrmCallbackException &&
                    isRetryableMediaDrmCallbackException((MediaDrmCallbackException) exception.getCause())));
    Log.d(TAG, "isRecoverablePlaybackException() - isRecoverable: " + isRecoverable);
    return isRecoverable;
  }

  /** Three cases to take care here:
   * 1. 401 or 409 error from Verimatrix Proxy running older version, NOT recoverable
   * 2. 401 or 409 error from Verimatrix Proxy running 19.05 or later. There is a responseBidy. Not recoverable
   * 3. 401 or 409 error from TiVo Proxy, recoverable
   * @param error (@link MediaDrmCallbackException) to check
   * @return true if the error is recoverable
   */
  private boolean isRetryableMediaDrmCallbackException(MediaDrmCallbackException error) {
    Log.d(TAG, "isRetryableMediaDrmCallbackException()");
    boolean isRecoverable = false;
    if (error.getCause() instanceof HttpDataSource.InvalidResponseCodeException) {
      HttpDataSource.InvalidResponseCodeException httpException =
              (HttpDataSource.InvalidResponseCodeException) error.getCause();
      Log.d(TAG, "isRetryableMediaDrmCallbackException() - HTTP response code: " + httpException.responseCode);
      if (httpException.responseCode == 401 || httpException.responseCode == 409) {
        String body = new String(httpException.responseBody);
        if (body != null) {
          if (body.contains("VCAS-612") || body.contains("VCAS-613")) {
            Log.i(TAG, "isRetryableMediaDrmCallbackException() - 401 or 409 error from Verimatrix Proxy, NOT recoverable");
            isRecoverable = false;
          } else {
            Log.i(TAG, "isRetryableMediaDrmCallbackException() - 401 or 409 error from TiVo Proxy, recoverable");
            isRecoverable = true;
          }
        }
      }
    }
    return isRecoverable;
  }
}
