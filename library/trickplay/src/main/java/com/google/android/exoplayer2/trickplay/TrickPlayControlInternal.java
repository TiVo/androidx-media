package com.google.android.exoplayer2.trickplay;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.trickplay.hls.FrameRateAnalyzer;

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
   * Frame rate increase as playback speed increases, reverse frame rates are
   * slower then forward as UX dictates humans respond differently to high speed reverse
   * vs forward.
   *
   * NOTE this is based on speed rather then {@link TrickMode} as long term plan is to
   * allow direct speed selection not just buckets
   *
   * @param speed playback speed (<0 is reverse)
   * @return target frame rate
   */
  float getTargetFrameRateForPlaybackSpeed(float speed);

  /**
   * Given an iFrame {@link Format} object look up it's actual normal speed frame rate.
   *
   * This method just a proxy, see {@link FrameRateAnalyzer#getFrameRateFor(Format)} for
   * full description of the operation.
   *
   * @param format the format to lookup frame rate for
   * @return the actual, estimated frame rate, or {@link Format#NO_VALUE} if frame rate is not yet known
   */
  float getFrameRateForFormat(Format format);

  /**
   * Called from the renderer (on the ExoPlayer's player thread) to report rendering a frame
   * while in trick-play mode.
   *
   * @param renderedFramePositionUs
   */
  void dispatchTrickFrameRender(long renderedFramePositionUs);

  /**
   * Used to enable/disable forward playback speed trick play.
   * The
   *
   * @param enabled - new value for playback speed trick play enablement.
   */
  void enablePlaybackSpeedForwardTrickPlay(boolean enabled);

  /**
   * Indicates if the frame rate analyzer has been initialized and is ready to provide
   * frame rate information for iFrame formats. The analyzer is only needed for forward
   * playback speed-based trick play.
   *
   * @return true if the analyzer is initialized (source iFrame format identified), false otherwise
   */
   boolean isAnalyzerInitialized();

  /**
   * Indicates if the trick play controller is configured to use playback speed forward trick play
   *
   * @return false if the trick play controller has been configured to not use playback speed trick play
   */
  boolean isPlaybackSpeedForwardTrickPlayEnabled();

}
