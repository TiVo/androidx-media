package com.google.android.exoplayer2.trickplay;

/**
 * Internal interface (used by trickplay code that overides ExoPlayer classes internally) to
 * TrickPlayController.
 *
 */
public interface TrickPlayControlInternal extends TrickPlayControl {

  /**
   * Add event listener, internal to the player logic. Listeners are called back on the main player thread
   *
   * @param eventListener - listener to call back.
   */
  void addEventListenerInternal(TrickPlayEventListener eventListener);

  /**
   * Remove previously added event listener from {@see #addEventListenerInternal}
   */
  void removeEventListenerInternal(TrickPlayEventListener eventListener);
}
