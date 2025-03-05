package com.tivo.exoplayer.library.multiview;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Player;

/**
 * API for managing audio focus for multiple players.
 */
public interface MultiPlayerAudioFocusManagerApi {

  /**
   * Set the currently selected player.  The selected player is the one that
   * will play audio, the remaining players will be "muted" (play with no audio).
   *
   * <p>Once there is no selected player, audio focus is released.</p>
   *
   * @param selectedPlayer The player to select, or null to release audio focus.
   */
  default void setSelectedPlayer(@Nullable Player selectedPlayer) {};

  /**
   * Check if we already have audio focus.
   *
   * @return true if we have audio focus, false otherwise.
   */
  default boolean hasAudioFocus() { return true; }
}
