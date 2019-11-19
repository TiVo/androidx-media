package com.tivo.exoplayer.library;


import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;

/**
 * Callback for changes to the playback state the current {@link MediaSource}
 */
public interface MediaSourceEventCallback {

  /**
   * Called when the current {@link MediaSource} was prepared for playback.
   *
   * When this callback is called the initial playlist has been loaded and parsed and
   * initial {@link com.google.android.exoplayer2.trackselection.TrackSelection} has completed.
   *
   * The player is ready to execute transport operations (like {@link SimpleExoPlayer#seekTo(long)}
   *
   * @param mediaSource - the MediaSource that was prepared
   * @param player - the current SimpleExoPlayer instance
   */
  void mediaSourcePrepared(MediaSource mediaSource, SimpleExoPlayer player);
}
