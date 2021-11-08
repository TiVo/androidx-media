package com.tivo.exoplayer.library.errorhandlers;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Log;

import java.util.ArrayList;
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

  @Nullable private DefaultTrackSelector trackSelector;

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
  public void onPlaybackStateChanged(int playbackState) {
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

    // Find the Video renderer and see if any or all of the tracks are reporting they don't support the
    // format
    //
    int rendererCount = mappedTrackInfo.getRendererCount();
    int videoRendererIndex = 0;
    int mappedVideoTrackCount = 0;
    int /* @MappingTrackSelector.MappedTrackInfo.RendererSupport */ rendererSupport = 0;

    ArrayList<UnsupportedVideoFormatsException.UnsupportedTrack> unsupportedTracks = new ArrayList<>();
    while (mappedTrackInfo.getRendererType(videoRendererIndex) != C.TRACK_TYPE_VIDEO && videoRendererIndex < rendererCount) {
      videoRendererIndex++;
    }
    if (videoRendererIndex < rendererCount) {
      rendererSupport = mappedTrackInfo.getRendererSupport(videoRendererIndex);
      TrackGroupArray videoTrackGroups = mappedTrackInfo.getTrackGroups(videoRendererIndex);

      // Likely there is only one for the video renderer
      for (int groupIndex = 0; groupIndex < videoTrackGroups.length; groupIndex++) {
        TrackGroup trackGroup = videoTrackGroups.get(groupIndex);
        for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
          mappedVideoTrackCount++;
          int trackSupport = mappedTrackInfo.getTrackSupport(videoRendererIndex, groupIndex, trackIndex);
          if (trackSupport != RendererCapabilities.FORMAT_HANDLED) {
            unsupportedTracks.add(new UnsupportedVideoFormatsException.UnsupportedTrack(trackIndex, trackGroup.getFormat(trackIndex), trackSupport));
          }
        }
      }
    } else {
      Log.w(TAG, "no video renderers found in track selection");
    }

    if (unsupportedTracks.size() == mappedVideoTrackCount) {
      UnsupportedVideoFormatsException unsupportedTracksError = new UnsupportedVideoFormatsException(unsupportedTracks);
      ExoPlaybackException error =
          ExoPlaybackException.createForRenderer(
              unsupportedTracksError,
              mappedTrackInfo.getRendererName(videoRendererIndex),
              videoRendererIndex,
              unsupportedTracks.get(0).format,  /* format */
              unsupportedTracks.get(0).formatSupport
              );
      playerErrorHandlerListener.playerErrorProcessed(error, PlayerErrorHandlerListener.HandlingStatus.WARNING);
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

