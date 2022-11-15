package com.google.android.exoplayer2.trickplay;// Copyright 2010 TiVo Inc.  All rights reserved.

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;

public class AdaptiveLoadControl implements LoadControl, TrickPlayEventListener {

  private static final String TAG = "AdaptiveLoadControl";

  private final TrickPlayControlInternal trickPlayController;
  private final LoadControl delegate;

  public AdaptiveLoadControl(TrickPlayControlInternal controller, LoadControl delegate) {
    trickPlayController = controller;
    this.delegate = delegate;
    trickPlayController.addEventListenerInternal(this);
  }

  @Override
  public void onPrepared() {
    delegate.onPrepared();
  }

  @Override
  public void onTracksSelected(Renderer[] renderers, TrackGroupArray trackGroups, ExoTrackSelection[] trackSelections) {
    delegate.onTracksSelected(renderers, trackGroups, trackSelections);
  }

  @Override
  public void onStopped() {
    delegate.onStopped();
  }

  @Override
  public void onReleased() {
    delegate.onReleased();
  }

  @Override
  public Allocator getAllocator() {
    return delegate.getAllocator();
  }

  @Override
  public long getBackBufferDurationUs() {

    // NOTE this currently is only called when the player is created :-(, so we need to retain back for trickplay
    // regardless of the state
    return 10 * 1000 * 1000;
  }

  @Override
  public boolean retainBackBufferFromKeyframe() {
    return delegate.retainBackBufferFromKeyframe();
  }

  @Override
  public boolean shouldContinueLoading(long playbackPositionUs, long bufferedDurationUs, float playbackSpeed) {
    boolean shouldContinue = delegate.shouldContinueLoading(playbackPositionUs, bufferedDurationUs, playbackSpeed);
    long requiredBufferedTimeUs = C.TIME_UNSET;

    if (trickPlayController.isSmoothPlayAvailable()) {
      switch (trickPlayController.getCurrentTrickDirection()) {
        case FORWARD:
          if (trickPlayController.isPlaybackSpeedForwardTrickPlayEnabled()) {
            requiredBufferedTimeUs = Util.getMediaDurationForPlayoutDuration(50_000_000, playbackSpeed);
          } else {
            // We're doing only seek-based VTP we only want 1 iframe. Hopefully, the iframes are > 1 second apart.
            requiredBufferedTimeUs = 1_000_000L;
          }
          break;

        case SCRUB:
          requiredBufferedTimeUs = 10_000_000L;
          break;
          
        case REVERSE:
          requiredBufferedTimeUs = 1_000_000L;    // Pointless to buffer, each seek will flush it.
          break;

        case NONE:
          break;
      }
    }

    if (requiredBufferedTimeUs != C.TIME_UNSET) {
      shouldContinue = bufferedDurationUs < requiredBufferedTimeUs;
    }
    return shouldContinue;
  }

  /**
   * This override uses trick-play specific values depending on the mode.
   *
   * For forward playback (here playbackSpeed indicates the rate at which media will be consumed)
   * we ignore rebuffering (set to false always) in order to use the minimum required buffer duration
   * for playback start ({@link com.google.android.exoplayer2.DefaultLoadControl.Builder#setBufferDurationsMs(int, int, int, int)}'s
   * third argument, bufferForPlaybackMs).  For frame by frame based modes (SCRUB and REVERSE) return true always,
   * as play when ready is false (we are only rendering first frame).
   *
   * @param bufferedDurationUs The duration of media that's currently buffered.
   * @param playbackSpeed The current factor by which playback is sped up.
   * @param rebuffering Whether the player is rebuffering. A rebuffer is defined to be caused by
   *     buffer depletion rather than a user action. Hence this parameter is false during initial
   *     buffering and when buffering as a result of a seek operation.
   * @param targetLiveOffsetUs The desired playback position offset to the live edge in
   *     microseconds, or {@link C#TIME_UNSET} if the media is not a live stream or no offset is
   *     configured.
   * @return value of super or modified value if in trick-play mode.
   */
  @Override
  public boolean shouldStartPlayback(long bufferedDurationUs, float playbackSpeed,
      boolean rebuffering, long targetLiveOffsetUs) {
    boolean defaultShouldStart =
        delegate.shouldStartPlayback(bufferedDurationUs, playbackSpeed, rebuffering, targetLiveOffsetUs);

    switch (trickPlayController.getCurrentTrickDirection()) {

      case FORWARD:
        // ignore rebuffering flag during VTP to avoid trying to be overly aggressive in rebuffering before starting playback.
        defaultShouldStart = delegate.shouldStartPlayback(bufferedDurationUs, playbackSpeed, false, targetLiveOffsetUs);
        if (!defaultShouldStart) {
          Log.d(TAG, "shouldStartPlayback() delaying for buffering - buffered(ms): " + C.usToMs(bufferedDurationUs) + " speed: " + playbackSpeed);
        }
        break;
      case NONE:
      case REVERSE:
        break;
      case SCRUB:
        defaultShouldStart = true;    // TODO probably does not matter as we are paused
        break;
    }

    return defaultShouldStart;
  }
}
