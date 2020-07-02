package com.tivo.exoplayer.library;// Copyright 2010 TiVo Inc.  All rights reserved.

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.analytics.AnalyticsListener;

/**
 * Implement this interface to monitor playback errors processed by the {@link SimpleExoPlayerFactory}
 * recovered or not.
 *
 * To extend this library default error recovery mechanism, extend the {@link DefaultExoPlayerErrorHandler}, override
 * the constructor to add to the list of {@link DefaultExoPlayerErrorHandler.PlaybackExceptionRecovery}
 */
public interface PlayerErrorHandlerListener {

  /**
   * The {@link DefaultExoPlayerErrorHandler} handles playback errors reported via the
   * {@link AnalyticsListener#onPlayerError(AnalyticsListener.EventTime, ExoPlaybackException)} by calling
   * {@link DefaultExoPlayerErrorHandler.PlaybackExceptionRecovery} implementations in turn until one
   * recovers from the error or their are none left.
   *
   * This method is called at the end of that sequence to allow listeners to log errors or stop playback
   * in the case the error was not recovered.
   *
   * @param eventTime time and details for the error event.
   * @param error the actual reported {@link ExoPlaybackException}
   * @param recovered true if a recovery PlaybackExceptionRecovery recovered from the error
   */
  void playerErrorProcessed(AnalyticsListener.EventTime eventTime, ExoPlaybackException error, boolean recovered);
}
