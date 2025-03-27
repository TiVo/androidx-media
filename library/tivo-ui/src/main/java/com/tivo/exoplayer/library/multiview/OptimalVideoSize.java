package com.tivo.exoplayer.library.multiview;

import com.google.android.exoplayer2.Format;

/**
 * Represents the optimal video size for a player in a multiview set.
 *
 * <p>The optimal video size is the maximum size that the player can display without scaling the video,
 * this is used by the {@link MultiViewTrackSelector} to select the best video track for the player.
 * At some point this class will become package private and be computed automatically when layout
 * completes for the view</p>
 */
public class OptimalVideoSize {

  public final int width;
  public final int height;

  public OptimalVideoSize(int width, int height) {
    this.width = width;
    this.height = height;
  }

  public boolean meetsOptimalSize(Format format) {
    boolean meets = format.height == Format.NO_VALUE || format.width == Format.NO_VALUE;
    if (!meets) {
      meets = format.width <= width && format.height <= height;
    }
    return meets;
  }
}
