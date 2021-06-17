package com.tivo.exoplayer.library;// Copyright 2010 TiVo Inc.  All rights reserved.

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.UnrecognizedInputFormatException;
import com.tivo.exoplayer.library.errorhandlers.PlaybackExceptionRecovery;

public interface MediaSourceLifeCycle extends PlaybackExceptionRecovery {

  /**
   * Stops playback of the current URL and re-starts playback of the indicated URL.
   *
   * This method Creates a {@link MediaSource} and makes it the current mediasource.  If the
   * player is set to play when ready playback will begin as soon as buffering completes.
   *
   * @param uri - URI (must be HTTP[x] schema, to play with HLS
   * @param drmInfo - DRM information
   * @param enableChunkless - sets the chunkless prepare option on mediasource
   * @throws UnrecognizedInputFormatException - if the URI is not in a supported container format.
   */
  void playUrl(Uri uri, DrmInfo drmInfo, boolean enableChunkless) throws UnrecognizedInputFormatException;

  /**
   * Set (or remove for null) a callback to notified when the MediaSource is prepared initially.
   *
   * The {@link SimpleExoPlayerFactory#setMediaSourceEventCallback(MediaSourceEventCallback)} uses
   * this method internally and maintains a reference to the callback object across create and
   * destroy of this {@link MediaSourceLifeCycle} object.
   *
   * @param callback callback notified of prepare event, or null to remove reference
   */
  void setMediaSourceEventCallback(@Nullable MediaSourceEventCallback callback);


  /**
   * Restart playback with the current media source but reset the position
   * to the default position (starting at 0 for VOD and the live offset for live)
   *
   */
  void resetAndRestartPlayback();
}
