package com.tivo.exoplayer.library.errorhandlers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Log;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * Currently ExoPlayer handles DRM retries in DefaultDrmSession, an instance of this
 * class is passed to it via {@link DefaultDrmSessionManager.Builder#setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy)}
 *
 * The DefaultDrmSession issues the HTTP requests to the Widevine proxy for both provisioning and
 * license requests.  There are two mechanisms:
 * <ul>
 *   <li>Follows redirects (307 or 308 HTTP Status with a location header. This is class is not involved in this, and it is
 *   an already in-place so the recommended way for Vermiatrix to delay retries.</li>
 *   <li>Other recoverable error codes, example 429 Too Many Requests.  This handler is called for these</li>
 * </ul>
 *
 * All errors from the {@link com.google.android.exoplayer2.upstream.DataSource} that are un-wrapped to the
 * underlying cause (an IOException) which is passed in the {@link LoadErrorInfo} passed to this class.
 *
 * Each error calls two methods in sequence:
 * <ul>
 *   <li>getMinimumLoadableRetryCount() - coded to return </li>
 *   <li>getRetryDelayMsFor()</li>
 * </ul>
 * Successful completion calls {@link LoadErrorHandlingPolicy#onLoadTaskConcluded(long)}
 *
 * Each DefaultDrmSession has a loader thread and there is only one instance of this object.  The requests are
 * identified by a unique {@link LoadEventInfo#loadTaskId}.
 */
public class DrmLoadErrorHandlerPolicy implements LoadErrorHandlingPolicy {
  public static final String TAG = "DrmLoadErrorHandlerPolicy";

  public static final long MAX_RETRY_COUNT = 3;    // 3 times limit on retries at this level
  public static final int MIN_RETRY_DELAY = 200;   // 200ms min delay
  private final Random retryRandomizer;

  static class ActiveRetryState {
    final LoadErrorInfo errorInfo;
    final long firstErrorTime;
    int errorCount;

    ActiveRetryState(LoadErrorInfo errorInfo) {
      this.errorInfo = errorInfo;
      firstErrorTime = Clock.DEFAULT.elapsedRealtime();
    }
  }

  public DrmLoadErrorHandlerPolicy() {
    activeRetries = new HashMap<>();
    byte[] seed = new SecureRandom().generateSeed(20); // 20 bytes of seed
    retryRandomizer = new Random(new BigInteger(seed).longValue());
  }


  private final HashMap<Long, ActiveRetryState> activeRetries;

  /**
   * There is no fallback for MDRM license / provisioning requests
   *
   * @param fallbackOptions The available fallback options.
   * @param loadErrorInfo A {@link LoadErrorInfo} holding information about the load error.
   * @return always null, no fallback for Drm errors.
   */
  @Nullable
  @Override
  public FallbackSelection getFallbackSelectionFor(@NonNull FallbackOptions fallbackOptions,
      @NonNull LoadErrorInfo loadErrorInfo) {
    return null;
  }

  /**
   * This method is called once one of the retries finally completes successfully.
   * We remove any state and timers for the indicated load task.
   *
   * @param loadTaskId the {@link LoadEventInfo#loadTaskId} that completed retries.
   */
  @Override
  public void onLoadTaskConcluded(long loadTaskId) {
    removeActiveRetryState(loadTaskId);
  }

  /**
   * Core logic to determine if we should retry the error and how long to delay the retry for.
   *
   *
   * @param loadErrorInfo A {@link LoadErrorInfo} holding information about the load error.
   * @return delay requested from server, random value, or C.TIME_UNSET to not retry
   */
  @Override
  public long getRetryDelayMsFor(@NonNull LoadErrorInfo loadErrorInfo) {
    long delayFor;
    ActiveRetryState retryState = getOrCreateActiveRetry(loadErrorInfo);
    retryState.errorCount++;
    if (loadErrorInfo.exception instanceof HttpDataSource.HttpDataSourceException) {
      delayFor = handleDataSourceException((HttpDataSource.HttpDataSourceException) loadErrorInfo.exception, loadErrorInfo, retryState);
    } else {    // TODO - handle other errors?
      Log.w(TAG, "Unsupported load error type, return don't retry.", loadErrorInfo.exception);
      delayFor = C.TIME_UNSET;
    }

    return delayFor;
  }

  /**
   * Returns value that always allows retries, this forces the decision to {@link #getRetryDelayMsFor(LoadErrorInfo)}}
   *
   * @param dataType One of the {@link C C.DATA_TYPE_*} constants indicating the type of data being
   *     loaded.
   * @return always allows retries
   */
  @Override
  public int getMinimumLoadableRetryCount(int dataType) {
    if (dataType != C.DATA_TYPE_DRM) {
      throw new UnsupportedOperationException("DrmLoadErrorHandlerPolicy handler only supports DefaultDrmSession loads");
    }
    return Integer.MAX_VALUE;
  }


  private long handleDataSourceException(HttpDataSource.HttpDataSourceException exception, LoadErrorInfo loadErrorInfo,
      ActiveRetryState retryState) {
    long timeSinceFirstRetry = Clock.DEFAULT.elapsedRealtime() - retryState.firstErrorTime;
    long delayFor = C.TIME_UNSET;   // Default is don't retry

    if (exception instanceof HttpDataSource.InvalidResponseCodeException) {
      HttpDataSource.InvalidResponseCodeException invalidResponseCodeException = (HttpDataSource.InvalidResponseCodeException) exception;
      Log.d(TAG, "handleDataSourceException() HTTP response: " + invalidResponseCodeException.responseCode);
      switch (invalidResponseCodeException.responseCode) {
        case 429:   // To Many Requests (see https://www.rfc-editor.org/rfc/rfc6585#section-4)
          delayFor = toManyRequestsDelayTime(loadErrorInfo, retryState);
          break;
        case 500:
        case 503:
          if (retryState.errorCount <= MAX_RETRY_COUNT) {
            delayFor = retryRandomizer.nextInt(
                    (int) ((retryState.errorCount * 1000 - MIN_RETRY_DELAY) + 1)) + 
                    (MIN_RETRY_DELAY + ((retryState.errorCount > 0) ? (retryState.errorCount - 1) * 1000 : 1000));
          }
          break;
      }
    }
    Log.i(TAG, "handleDataSourceException(): delayFor " + delayFor);
    return delayFor;
  }

  private long toManyRequestsDelayTime(LoadErrorInfo loadErrorInfo, ActiveRetryState retryState) {
    List<String> retryAfter = loadErrorInfo.loadEventInfo.responseHeaders.get("Retry-After");
    long retryDelay = C.TIME_UNSET;
    if (retryAfter != null) {
      try {
        retryDelay = Long.parseLong(retryAfter.get(0));
      } catch (NumberFormatException e) {
        Log.d(TAG, "invalid Retry-After header, fallback to random, header: " + retryAfter.get(0));
      }
    }

    if (retryDelay == C.TIME_UNSET) {
        // If the "Retry-After" is not set in the response header, limit the
        // number of retries to MAX_RETRY_COUNT to avoid indefinite retry loop
        if (retryState.errorCount <= MAX_RETRY_COUNT) {
            int maxDelay = retryState.errorCount * 1000;
            retryDelay = retryRandomizer.nextInt((int) ((maxDelay - MIN_RETRY_DELAY) + 1)) + 
                (MIN_RETRY_DELAY + ((retryState.errorCount > 0) ? (retryState.errorCount - 1) * 1000 : 1000));
        }
    }
    return retryDelay;
  }

  private synchronized ActiveRetryState getOrCreateActiveRetry(LoadErrorInfo loadErrorInfo) {
    long loadTaskId = loadErrorInfo.loadEventInfo.loadTaskId;
    ActiveRetryState activeRetryState = activeRetries.get(loadTaskId);
    if (activeRetryState == null) {
      activeRetryState = new ActiveRetryState(loadErrorInfo);
      activeRetries.put(loadTaskId, activeRetryState);
    }
    return activeRetryState;
  }

  private synchronized void removeActiveRetryState(long loadTaskId) {
    activeRetries.remove(loadTaskId);
  }
}
