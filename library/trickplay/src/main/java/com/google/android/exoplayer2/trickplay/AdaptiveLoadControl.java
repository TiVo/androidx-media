package com.google.android.exoplayer2.trickplay;// Copyright 2010 TiVo Inc.  All rights reserved.

import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Log;

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
  public boolean shouldContinueLoading(long bufferedDurationUs, float playbackSpeed) {
    long iFrameDurationUs = 2 * 1000 * 1000;    // TODO should come from playlist parse somehow
    boolean shouldContinue = delegate.shouldContinueLoading(bufferedDurationUs, playbackSpeed);
    float requiredBufferedTime = 0.0f;

    if (trickPlayController.isSmoothPlayAvailable() && false) {
      switch (trickPlayController.getCurrentTrickDirection()) {
        case FORWARD:
          if (playbackSpeed > 6.0) {    // Buffering more then one frame when jumps are large is pointless
            requiredBufferedTime = iFrameDurationUs;
          } else {
            requiredBufferedTime = playbackSpeed * iFrameDurationUs;
          }
          break;

        case REVERSE:
          requiredBufferedTime = iFrameDurationUs;
          break;

        case NONE:
          requiredBufferedTime = 0.0f;
          break;
      }
    }

    if (requiredBufferedTime > 0.0f) {
      shouldContinue = bufferedDurationUs < requiredBufferedTime;
    }
//
//    if (trickPlayController.getCurrentTrickDirection() == TrickPlayControl.TrickPlayDirection.REVERSE) {
//      Log.d(TAG, "shouldContinueLoading() - speed: " + playbackSpeed + " buffered: " + bufferedDurationUs);
//      shouldContinue = minForIframePlayback;
//    }
    return shouldContinue;
  }

  @Override
  public boolean shouldStartPlayback(long bufferedDurationUs, float playbackSpeed, boolean rebuffering) {
    boolean defaultShouldStart = delegate.shouldStartPlayback(bufferedDurationUs, 1.0f, rebuffering);

    if (trickPlayController.getCurrentTrickDirection() == TrickPlayControl.TrickPlayDirection.REVERSE) {
      Log.d(TAG, "shouldStartPlayback() - speed: " + playbackSpeed + " buffered: " + bufferedDurationUs + " rebuffer: " + rebuffering + " super:shouldStartPlayback(): " + defaultShouldStart);
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
