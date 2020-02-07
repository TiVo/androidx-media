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
   * Remove previously added event listener from {@link #addEventListenerInternal}
   *
   * @param eventListener - listener to remove.
   */
  void removeEventListenerInternal(TrickPlayEventListener eventListener);

  /**
   * If the renderer should control frame rate.
   *
   * @return true to use trick-play rendering.
   */
  boolean useTrickPlayRendering();

  /**
   * Called from the renderer (on the ExoPlayer's player thread) to report rendering a frame
   * while in trick-play mode.
   *
   * @param renderedFramePositionUs
   */
  void dispatchTrickFrameRender(long renderedFramePositionUs);

}
