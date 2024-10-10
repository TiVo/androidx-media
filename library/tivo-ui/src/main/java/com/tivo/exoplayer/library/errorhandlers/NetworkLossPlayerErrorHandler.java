package com.tivo.exoplayer.library.errorhandlers;

import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.Log;
import java.math.BigInteger;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.Random;

/**
 * Handle transient network lost errors.
 * <p></p>
 * If ExoPlayer runs out of buffer while the network is lost ({@link ConnectivityManager#isDefaultNetworkActive()} is false)
 * this handler will attempt to retry playback when the network is reconnected, if the loss is un-detected by ConnectivityManager
 * then timed retries, randomly between {@link #RETRY_WITH_NETWORK_MIN_MS} and {@link #RETRY_WITH_NETWORK_MAX_MS} are
 * issued until the network returns.  This randomization of retries avoids attacking a potentially down Edge/CDN interface
 * with all clients at the same time
 * <p></p>
 * This handler will retry forever as long as no channel tune is issued.
 */
public class NetworkLossPlayerErrorHandler implements PlaybackExceptionRecovery, Player.Listener {
  public static final String TAG = "NetworkLossPlayerErrorHandler";
  public static int RETRY_WITH_NETWORK_MAX_MS = 3000;
  public static int RETRY_WITH_NETWORK_MIN_MS = 1000;

  private final PlayerErrorRecoverable errorRecoverable;
  private final Context androidContext;
  private final RetryEnabledHandler retryEnabledHandler;
  private PlaybackException currentError;
  private long positionAtError;
  private Timeline.Window windowAtError;
  private final Random retryRandomizer;
  private boolean shouldRetry;

  private Handler timedRetryHandler = new Handler(Looper.getMainLooper());

  private class NetworkChangeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      Log.d(TAG, "onReceive() - intent: " + intent);
      boolean isConnected = isNetworkConnected();
      if (isConnected) {
        Log.d(TAG, "Connected to the internet");
        attemptRecovery();
      } else {
        Log.d(TAG, "Still no network connection");
      }
    }
  }
  private @Nullable NetworkChangeReceiver networkChangeReceiver;

  private class TimedRetryAction implements Runnable {
    @Override
    public void run() {
      Log.d(TAG, "do timed retry");
      attemptRecovery();
    }
  }
  private final TimedRetryAction timedRetryAction = new TimedRetryAction();

  private class RetryEnabledHandler implements Player.Listener {
    @Override
    public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
      String uri = "none";
      if (mediaItem != null && mediaItem.playbackProperties != null) {
        uri = String.valueOf(mediaItem.playbackProperties.uri);
      }
      Log.d(TAG, "MediaItem changed to: " + uri + " shouldRetry set false, was: " + shouldRetry);
      shouldRetry = false;
    }

    @Override
    public void onTimelineChanged(@NonNull Timeline timeline,  @Player.TimelineChangeReason int reason) {
      if (!shouldRetry) {
        Log.d(TAG, "onTimelineChanged() - reason: " + reason + " shouldRetry was false, setting true");
      }
      shouldRetry = true;
    }
  }

  public NetworkLossPlayerErrorHandler(PlayerErrorRecoverable playerErrorRecoverable, Context context) {
    this(playerErrorRecoverable, context, false);
  }

  public NetworkLossPlayerErrorHandler(PlayerErrorRecoverable playerErrorRecoverable, Context context, boolean retryFirstError) {
    errorRecoverable = playerErrorRecoverable;
    androidContext = context;
    byte[] seed = new SecureRandom().generateSeed(20); // 20 bytes of seed
    retryRandomizer = new Random(new BigInteger(seed).longValue());

    Log.i(TAG, "contruct - retryFirstError: " + retryFirstError);

    if (retryFirstError) {
      shouldRetry = true;
      retryEnabledHandler = null;
    } else {
      shouldRetry = false;
      retryEnabledHandler = new RetryEnabledHandler();
      Objects.requireNonNull(playerErrorRecoverable.getCurrentPlayer()).addListener(retryEnabledHandler);
    }
  }

    // implement PlaybackExceptionRecovery

  /**
   * Recovers for network error codes that indicate the path to the CDN/Origin was lost
   * (SocketTimeout for example).
   * <p></p>
   * There are two main failure scenarios that are handled slightly differently:
   * <ol>
   *   <li>link lost -- The device local link indicates it is not connected, e.g. un-plug wired lan cable</li>
   *   <li>path fail -- Some other link along the path to the CDN/Origin is lost (home router down, etc)</li>
   * </ol>
   * For the first case we listen to {@link ConnectivityManager#CONNECTIVITY_ACTION} broadcasts where the
   * network interface is returned to link active state, then retry.   The second case requires timed
   * retries.  Both cases call back to {@link #attemptRecovery()} which simply calls {@link PlayerErrorRecoverable#retryPlayback()}
   * <p></p>
   * When this method returns true, the following post conditions are true:
   * <ol>
   *   <li>A retry callback (timed or event based) setup</li>
   *   <li>Playback state (position, timeline, etc) is saved</li>
   *   <li>The value {@link #isRecoveryInProgress()} is true, so the {@link DefaultExoPlayerErrorHandler} will report
   *        {@link com.tivo.exoplayer.library.errorhandlers.PlayerErrorHandlerListener.HandlingStatus#IN_PROGRESS}</li>
   * </ol>
   *
   * @param error the {@link PlaybackException} signaled
   * @return true if the error is a supported {@link PlaybackException#errorCode}
   */
  @Override
  public boolean recoverFrom(PlaybackException error) {
    boolean handled = isHandledPlaybackException(error) && shouldRetry;
    if (handled && ! isRecoveryInProgress()) {
      currentError = error;
      addPlayerListener();
      gatherStateAtError();
      setupToRetry();
    } else if (isRecoveryInProgress()) {
      setupToRetry();
    }
    return handled;
  }

  /**
   * Reports status of the recovery, for this handler recovery is in-progress until the
   * first playlist load completes following internal call to {@link #attemptRecovery()}
   *
   * @return true when the first playlist loads and we have issued the seek to the position when the error occurred.
   */
  @Override
  public boolean checkRecoveryCompleted() {
    SimpleExoPlayer player = errorRecoverable.getCurrentPlayer();
    Log.d(TAG, "checkRecoveryCompleted() - check complete, inProgress: " + isRecoveryInProgress()
        + " player state: " + (player == null ? "no player" : player.getPlaybackState()));
    return !isRecoveryInProgress();
  }

  @Override
  public void abortRecovery() {
    recoveryIsCompleted();
  }

  @Override
  public boolean isRecoveryInProgress() {
    return currentError != null;
  }

  @Nullable
  @Override
  public PlaybackException currentErrorBeingHandled() {
    return currentError;
  }

  @Override
  public void releaseResources() {
    recoveryIsCompleted();
    SimpleExoPlayer player = errorRecoverable.getCurrentPlayer();
    if (player != null) {
      player.removeListener(retryEnabledHandler);
    }
  }

  // Implement Player.Listener

  /**
   * Listen for the first playlist re-load following the retry (SOURCE_UPDATE), then seek
   * to the closest position (see {@link #seekToPositionAtFailure(Timeline, SimpleExoPlayer)}) to
   * the position where the error occurred then mark recovery completed.
   * <p></p>
   * If a tune away happens then the playlist will change so we abort any error recovery.
   *
   * @param timeline The latest timeline. Never null, but may be empty.
   * @param reason The {@link Player.TimelineChangeReason} responsible for this timeline change.
   */
  @Override
  public void onTimelineChanged(Timeline timeline, @Player.TimelineChangeReason int reason) {
    Log.d(TAG, "onTimelineChanged() - timeline empty: " + timeline.isEmpty() + " reason: " + reason + " shouldRetry: " + shouldRetry);
    switch (reason) {
      case Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED:
        abortRecovery();
        break;
      case Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE:
        if (isRecoveryInProgress()) {
          SimpleExoPlayer player = errorRecoverable.getCurrentPlayer();
          assert player != null;
          seekToPositionAtFailure(timeline, player);
        }
        recoveryIsCompleted();
        break;
    }

  }

  // Internal methods

  /**
   * Returns if the error should be recovered by this class, that is the error is caused
   * by a network connection failure.  Presumably a <b>recoverable</b> temporary issue, including user induced like
   * cable disconnect.
   * <p></p>
   * Specific handled errors are the two NETWORK_CONNECTION errorCodes, with these root causes:
   * <ol>
   *   <li>{@link SocketTimeoutException} -- occurs when the origin is non responsive, not listening on the socket, network path fails</li>
   *   <li>{@link UnknownHostException} -- local network is down and CDN/Edge host cannot be reached (also could be a bad URL, but unlikely)</li>
   *   <li>{@link SocketException} -- occurs when the local network is down (@link ConnectException}, note it is possible the subclasses
   *                                  of this error are more permanent failures (e.g. BindException).</li>
   * </ol>
   *
   * Note the {@link java.net.Socket} open could also fail for more non-recoverable causes which are not retried, including:
   * <ol>
   *   <li>{@link java.net.UnknownHostException}</li>
   *   <li>{@link java.net.MalformedURLException}</li>
   *   <li>{@link java.net.URISyntaxException}</li>
   * </ol>
   *
   * @param error PlaybackException to check
   * @return true if it is a network lost type of error recovered by this class.
   */
  private boolean isHandledPlaybackException(PlaybackException error) {
    return isHandledErrorCode(error) && isHandledRootCause(error);
  }

  private static boolean isHandledRootCause(PlaybackException error) {
    return PlaybackExceptionRecovery.isSourceErrorOfType(error, SocketTimeoutException.class)
        || PlaybackExceptionRecovery.isSourceErrorOfType(error, SocketException.class)
        || PlaybackExceptionRecovery.isSourceErrorOfType(error, UnknownHostException.class);
  }

  private static boolean isHandledErrorCode(PlaybackException error) {
    int errorCode = error.errorCode;
    return errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT;
  }

  private boolean isNetworkConnected() {
    ConnectivityManager connectivityManager = (ConnectivityManager) androidContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
    boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    Log.d(TAG, "ConnectivityManager - networkInfo: " + activeNetwork);
    return isConnected;
  }

  /**
   * Check how we will retry playback, Either timed retries or waiting for network
   * connection state to change, and setup listener callback to issue the actual retry.
   */
  private void setupToRetry() {
    Log.d(TAG, "setupToRetry() - recoveryInProgress: " + isRecoveryInProgress());
    boolean isConnected = isNetworkConnected();
    if (isConnected) {
      int nextRetryDelay = retryRandomizer.nextInt((RETRY_WITH_NETWORK_MAX_MS - RETRY_WITH_NETWORK_MIN_MS) + 1) + RETRY_WITH_NETWORK_MIN_MS;
      Log.d(TAG, "network is connected, but path to origin is not. Retry in " + nextRetryDelay + "ms");
      timedRetryHandler.removeCallbacks(timedRetryAction);
      timedRetryHandler.postDelayed(timedRetryAction, nextRetryDelay);
    } else {
      Log.d(TAG, "network is not connected, register for retry on state change");
      registerNetworkChangeReceiver();
    }
  }

  private void recoveryIsCompleted() {
    if (currentError != null) {
      Log.d(TAG, "recoveryIsCompleted()");
    }
    currentError = null;
    timedRetryHandler.removeCallbacks(timedRetryAction);
    unregisterNetworkChangeReceiver();
    removePlayerListener();
  }

  private void registerNetworkChangeReceiver() {
    unregisterNetworkChangeReceiver();
    Log.d(TAG, "registerNetworkChangeReceiver() - recoveryInProgress: " + isRecoveryInProgress());
    networkChangeReceiver = new NetworkChangeReceiver();
    IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
    androidContext.registerReceiver(networkChangeReceiver, intentFilter);
  }

  private void unregisterNetworkChangeReceiver() {
    if (networkChangeReceiver != null) {
      Log.d(TAG, "unregisterNetworkChangeReceiver() - recoveryInProgress: " + isRecoveryInProgress());
      androidContext.unregisterReceiver(networkChangeReceiver);
      networkChangeReceiver = null;
    }
  }

  private void addPlayerListener() {
    SimpleExoPlayer player = removePlayerListener();
    if (player != null) {
      player.addListener(this);
    }

  }

  private SimpleExoPlayer removePlayerListener() {
    SimpleExoPlayer player = errorRecoverable.getCurrentPlayer();
    if (player != null) {
      player.removeListener(this);
    }
    return player;
  }

  private void gatherStateAtError() {
    SimpleExoPlayer player = errorRecoverable.getCurrentPlayer();
    windowAtError = getCurrentWindow(player);
    positionAtError = player == null ? C.TIME_UNSET : player.getCurrentPosition();
  }

  private static Timeline.Window getCurrentWindow(SimpleExoPlayer player) {
    Timeline.Window currentWindow = null;
    Timeline timeline = player == null ? null : player.getCurrentTimeline();
    if (timeline != null && !timeline.isEmpty()) {
      int windowIndex = player.getCurrentWindowIndex();
      currentWindow = timeline.getWindow(windowIndex, new Timeline.Window());
    }
    return currentWindow;
  }

  private void attemptRecovery() {
    SimpleExoPlayer player = errorRecoverable.getCurrentPlayer();
    Log.d(TAG, "attemptRecovery() - playerState: " + ((player != null) ? player.getPlaybackState() : "no player") + " currentError: " + currentError);
    if (currentError != null && player != null) {
      Timeline.Window currentWindow = getCurrentWindow(player);
      Log.d(TAG, "retry playback.  currentPosition: " + player.getContentPosition()  + " positionAtError: " + positionAtError
              + " currentWindow " + windowLogString(currentWindow)
              + " windowAtError " + windowLogString(windowAtError)
      );
      errorRecoverable.retryPlayback();
    }
  }

  /**
   * For non-live this method does nothing (error position is still valid position for
   * restarting playback).  Live has a few use cases, based on the time relation between the new and
   * the error live window:
   * <ul>
   *   <li>Error position is still in the new live window -- simply seek to it and playback resumes where it left off</li>
   *   <li>Error position past save live start position -- seek backwards to the default position (this may replay content)</li>
   *   <li>Error position is outside of the new live window -- simply seek to it and playback resumes where it left off</li>
   * </ul>
   *
   * @param timeline timeline on first SOURCE_UPDATE after error recovery attempt.
   * @param player current player
   */
  private void seekToPositionAtFailure(Timeline timeline, SimpleExoPlayer player) {
    int windowIndex = player.getCurrentWindowIndex();
    Timeline.Window currentWindow = timeline.getWindow(windowIndex, new Timeline.Window());
    long contentPosition = player.getContentPosition();
    Log.d(TAG, "seekToPositionAtFailure() - currentPosition: " + contentPosition + " positionAtError: " + positionAtError
        + " currentWindow " + windowLogString(currentWindow)
        + " windowAtError " + windowLogString(windowAtError)
    );
    if (currentWindow.isLive()) {
      long windowStartDeltaMs = currentWindow.windowStartTimeMs - windowAtError.windowStartTimeMs;
      if (windowStartDeltaMs >= 0) {
        long seekTargetMs = positionAtError - windowStartDeltaMs;
        if (seekTargetMs >= 0 && seekTargetMs <= C.usToMs(currentWindow.defaultPositionUs)) {   // use case 1
          Log.d(TAG, "recover complete, seek to position when error occurred - windowStart delta: " + windowStartDeltaMs);
          player.seekTo(seekTargetMs);
        } else {    // use case 2
          Log.d(TAG, "recover complete, seekTargetMs " + seekTargetMs + " out of range. Do seekToDefaultPosition(), windowStart delta: " + windowStartDeltaMs);
          player.seekToDefaultPosition();
        }
      } else {  // use case 3
        Log.d(TAG, "recover complete, Do seekToDefaultPosition() as invalid windowStart delta: " + windowStartDeltaMs);
        player.seekToDefaultPosition();
      }
    } else {
      Log.d(TAG, "seekToPositionAtFailure() - non-live window so seek not required");
    }
  }

  private String windowLogString(Timeline.Window window) {
    return "[isLive " + window.isLive() + " startTimeMs " + window.windowStartTimeMs + " defaultPositionUs " + window.defaultPositionUs + "]";
  }

}
