package com.tivo.exoplayer.library.errorhandlers;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * ExoPlayer reports errors via the {@link Player.Listener#onPlayerError(PlaybackException)}
 * method.  The errors reported to this method may be recovered, the player is transitions to the
 * {@link Player#STATE_IDLE} and playback stops.
 *
 * This handler listens to the @link Player.EventListener} and calls handlers that implement
 * {@link PlaybackExceptionRecovery}, in the order added until the list is exhausted or one of
 * the handlers returns true from {@link PlaybackExceptionRecovery#recoverFrom(PlaybackException)}
 *
 */
public class DefaultExoPlayerErrorHandler implements Player.Listener {

  private static final String TAG = "ExoPlayerErrorHandler";
  private final List<PlaybackExceptionRecovery> handlers;

  @Nullable private final PlayerErrorHandlerListener playerErrorHandlerListener;

  // NOTE this is nullable only till the deprecated constructor is removed.
  @Nullable private final Player player;

  @Nullable private DefaultTrackSelector trackSelector;
  @Nullable private PlaybackExceptionRecovery currentRetryingHandler;

  /**
   * If you subclass implement this method. You can choose to add to the list of
   * {@link PlaybackExceptionRecovery} handlers, it is recommmeded you add to the end of the
   * list.  Most all common errors that ExoPlayer indicates can be recovered are handled
   * (eg. {@link com.google.android.exoplayer2.source.BehindLiveWindowException}
   *
   * // DEPRECATED - used the method that passes in the player for optimal operation
   * @param handlers ordred list of {@link PlaybackExceptionRecovery} handlers
   * @param playerErrorHandlerListener optional (but recommended) callback for error handling.
   */
  @Deprecated
  public DefaultExoPlayerErrorHandler(List<PlaybackExceptionRecovery> handlers,
      @Nullable PlayerErrorHandlerListener playerErrorHandlerListener) {
    this(handlers, null, playerErrorHandlerListener);
  }

  /**
   * If you subclass implement this method. You can choose to add to the list of
   * {@link PlaybackExceptionRecovery} handlers, it is recommmeded you add to the end of the
   * list.  Most all common errors that ExoPlayer indicates can be recovered are handled
   * (eg. {@link com.google.android.exoplayer2.source.BehindLiveWindowException}
   *
   * @param handlers ordered list of {@link PlaybackExceptionRecovery} handlers
   * @param exoPlayer the current ExoPlayer instance the handler is listening to errors for.
   * @param playerErrorHandlerListener optional (but recommended) callback for error handling.
   */
  public DefaultExoPlayerErrorHandler(
      List<PlaybackExceptionRecovery> handlers,
      @Nullable Player exoPlayer,
      @Nullable PlayerErrorHandlerListener playerErrorHandlerListener) {
    this.handlers = handlers;
    this.playerErrorHandlerListener = playerErrorHandlerListener;
    player = exoPlayer;
  }

  public void releaseResources() {
    trackSelector = null;
    for (PlaybackExceptionRecovery handler : handlers) {
      handler.releaseResources();
    }
  }

  public void setCurrentTrackSelector(DefaultTrackSelector trackSelector) {
    this.trackSelector = trackSelector;
  }

  // Implement Player.EventListener


  @Override
  public void onMediaItemTransition(@Nullable MediaItem mediaItem, @Player.TimelineChangeReason int reason) {
    for (PlaybackExceptionRecovery handler : handlers) {
      if (handler.isRecoveryInProgress()) {
        Log.d(TAG, "onMediaItemTransition() - aborting recovery for " + handler);
        playerErrorProcessed(handler.currentErrorBeingHandled(), PlayerErrorHandlerListener.HandlingStatus.ABANDONED);
        handler.abortRecovery();
        currentRetryingHandler = null;
      }
    }
  }

  @Override
  public void onPlaybackStateChanged(int playbackState) {
    PlaybackException error = null;
    for (PlaybackExceptionRecovery handler : handlers) {
      if (handler.isRecoveryInProgress()) {
        error = handler.currentErrorBeingHandled();
        handler.checkRecoveryCompleted();
      }
    }

    if (currentRetryingHandler != null && !currentRetryingHandler.isRecoveryInProgress()) {
      Log.d(TAG, "onPlaybackStateChanged() - recovery was completed for " + currentRetryingHandler);
      error = error == null ? new PlaybackException("unknown", null, PlaybackException.ERROR_CODE_UNSPECIFIED) : error;
      playerErrorProcessed(error, PlayerErrorHandlerListener.HandlingStatus.SUCCESS);
      currentRetryingHandler = null;
    }
  }

  @Override
  @CallSuper
  public void onPlayerError(PlaybackException error) {
    Log.w(TAG, "onPlayerError() - error: " + error);
    PlaybackExceptionRecovery activeHandler = null;

    // This error is reported by player.release() on some Android platforms,
    // it is impossbile to recover from
    //  see: https://github.com/google/ExoPlayer/issues/4352
    //  and commit: https://github.com/google/ExoPlayer/commit/008c80812b06384b416649196c7601543832cc13
    // Since our 2.12 release this error is possible.
    //
    if (error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT) {
     Log.e(TAG, "release timeout error, bypass error recovery as not possible.", error);
    } else if (player != null && player.isPlayingAd()) {
      Log.i(TAG, "onPlayerError() - error during ad playback ignored, already reported via IMA SDK");
    } else {
      for (PlaybackExceptionRecovery handler : handlers) {
        if (handler.recoverFrom(error)) {
          Log.d(TAG, "onPlayerError recovery handler " + handler + " returned true");
          activeHandler = handler;
          break;
        }
      }

      if (activeHandler == null) {
        currentRetryingHandler = null;
        playerErrorProcessed(error, PlayerErrorHandlerListener.HandlingStatus.FAILED);
      } else {
        reportErrorStatus(activeHandler);
      }
    }
    Log.w(TAG, "onPlayerError() - return, activeHandler: " + activeHandler);
  }

  @Override
  public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    // Check entry conditions for early exit, must have a trackselector current and a selection
    if (trackSelector == null) {
      Log.w(TAG, "Not checking selected tracks, no TrackSelector supplied");
      return;
    }

    MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
    if (mappedTrackInfo == null) {
      Log.w(TAG, "Not checking selected tracks, no active selection");
      return;
    }

    // check and throw exception if any or all of the video tracks are unsupported
    checkForUnsupportedTracks(mappedTrackInfo, C.TRACK_TYPE_VIDEO);
    // check and throw exception if any or all of the audio tracks are unsupported
    checkForUnsupportedTracks(mappedTrackInfo, C.TRACK_TYPE_AUDIO);
  }

  /**
   * Finds the renderer (based on trackType) and see if any or all of the tracks are reporting they don't support the
   * format.
   * In case all the tracks are unsupported, we throw an exception
   *
   * @param mappedTrackInfo The mapped track information.
   * @param trackType The track type.
   */
  private void checkForUnsupportedTracks(MappingTrackSelector.MappedTrackInfo mappedTrackInfo, int trackType) {

    int rendererCount = mappedTrackInfo.getRendererCount();
    int rendererIndex = 0;
    int mappedTrackCount = 0;
    int /* @MappingTrackSelector.MappedTrackInfo.RendererSupport */ rendererSupport = 0;

    ArrayList<UnsupportedFormatsException.UnsupportedTrack> unsupportedTracks = new ArrayList<>();
    while (mappedTrackInfo.getRendererType(rendererIndex) != trackType && rendererIndex < rendererCount) {
      rendererIndex++;
    }

    if (rendererIndex < rendererCount) {
      rendererSupport = mappedTrackInfo.getRendererSupport(rendererIndex);
      TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);

      // Likely there is only one for the video/audio renderer
      for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
        TrackGroup trackGroup = trackGroups.get(groupIndex);
        for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
          mappedTrackCount++;
          int trackSupport = mappedTrackInfo.getTrackSupport(rendererIndex, groupIndex, trackIndex);
          if (trackSupport != RendererCapabilities.FORMAT_HANDLED) {
            unsupportedTracks.add(new UnsupportedFormatsException.UnsupportedTrack(trackIndex, trackGroup.getFormat(trackIndex), trackSupport));
          }
        }
      }
    } else {
        Log.w(TAG, "no type" + trackType + " renderers found in track selection");
    }

    if (unsupportedTracks.size() == mappedTrackCount) {
      UnsupportedFormatsException unsupportedTracksError = new UnsupportedFormatsException(unsupportedTracks);
      ExoPlaybackException error =
          ExoPlaybackException.createForRenderer(
              unsupportedTracksError,
              mappedTrackInfo.getRendererName(rendererIndex),
              rendererIndex,
              unsupportedTracks.isEmpty() ? null : unsupportedTracks.get(0).format,  /* format */
              unsupportedTracks.isEmpty() ? RendererCapabilities.FORMAT_HANDLED : unsupportedTracks.get(0).formatSupport,
              false,
              PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
          );
      playerErrorHandlerListener.playerErrorProcessed(error, PlayerErrorHandlerListener.HandlingStatus.WARNING, player);
    }
  }

  private void reportErrorStatus(PlaybackExceptionRecovery handler) {
    PlayerErrorHandlerListener.HandlingStatus status = null;
    PlaybackException error = null;

    if (handler.isRecoveryFailed()) {
      status = PlayerErrorHandlerListener.HandlingStatus.FAILED;
    } else if (handler.isRecoveryInProgress()) {
      error = handler.currentErrorBeingHandled();
      if (handler.checkRecoveryCompleted()) {
        status = PlayerErrorHandlerListener.HandlingStatus.SUCCESS;
      } else {
        status = PlayerErrorHandlerListener.HandlingStatus.IN_PROGRESS;
        currentRetryingHandler = handler;
      }
    }
    playerErrorProcessed(error, status);
  }

  private void playerErrorProcessed(PlaybackException error, PlayerErrorHandlerListener.HandlingStatus status) {
    Log.d(TAG, "playerError was processed, status: " + status);
    if (playerErrorHandlerListener != null) {
      playerErrorHandlerListener.playerErrorProcessed(error, status, player);
    }
  }
}

