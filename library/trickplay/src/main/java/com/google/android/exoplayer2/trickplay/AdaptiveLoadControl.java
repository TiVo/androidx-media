package com.google.android.exoplayer2.trickplay;// Copyright 2010 TiVo Inc.  All rights reserved.

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.Allocator;
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
  public void onTracksSelected(Renderer[] renderers, TrackGroupArray trackGroups,
      TrackSelectionArray trackSelections) {
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
            requiredBufferedTimeUs = 5_000_000L;
          }
          break;

        case SCRUB:
          requiredBufferedTimeUs = 10_000_000L;
          break;
          
        case REVERSE:
          requiredBufferedTimeUs = 5_000_000L;    // Pointless to buffer, each seek will flush it.
          break;

        case NONE:
          requiredBufferedTimeUs = C.TIME_UNSET;
          break;
      }
    }

    if (requiredBufferedTimeUs != C.TIME_UNSET) {
      shouldContinue = bufferedDurationUs < requiredBufferedTimeUs;
    }
//
//    if (trickPlayController.getCurrentTrickDirection() == TrickPlayControl.TrickPlayDirection.REVERSE) {
//      Log.d(TAG, "shouldContinueLoading() - speed: " + playbackSpeed + " buffered: " + bufferedDurationUs);
//    }
    return shouldContinue;
  }

  @Override
  public boolean shouldStartPlayback(long bufferedDurationUs, float playbackSpeed, boolean rebuffering) {
    boolean defaultShouldStart = delegate.shouldStartPlayback(bufferedDurationUs, 1.0f, rebuffering);

    if (trickPlayController.getCurrentTrickDirection() == TrickPlayControl.TrickPlayDirection.SCRUB) {
//      Log.d(TAG, "shouldStartPlayback() - speed: " + playbackSpeed + " buffered: " + bufferedDurationUs + " rebuffer: " + rebuffering + " super:shouldStartPlayback(): " + defaultShouldStart);
      defaultShouldStart = true;
    }

    return defaultShouldStart;
  }

  @Override
  public void playlistMetadataValid(boolean isMetadataValid) {

  }

  @Override
  public void trickPlayModeChanged(TrickPlayControl.TrickMode newMode, TrickPlayControl.TrickMode prevMode) {

  }

  @Override
  public void trickFrameRendered(long frameRenderTimeUs) {

  }
}
