package com.google.android.exoplayer2.trickplay;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Handler;
import androidx.annotation.Nullable;

import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.logging.Logger;

class TrickPlayRendererFactory extends DefaultRenderersFactory {

  private final TrickPlayControlInternal trickPlayController;

  TrickPlayRendererFactory(Context context, TrickPlayControlInternal controller) {
    super(context);
    trickPlayController = controller;
    setAllowedVideoJoiningTimeMs(0);
  }

  @Override
  protected void buildVideoRenderers(
      Context context,
      @ExtensionRendererMode int extensionRendererMode,
      MediaCodecSelector mediaCodecSelector,
      boolean enableDecoderFallback,
      Handler eventHandler,
      VideoRendererEventListener eventListener,
      long allowedVideoJoiningTimeMs,
      ArrayList<Renderer> out) {

    out.add(
        new TrickPlayAwareMediaCodecVideoRenderer(
            trickPlayController,
            context,
            mediaCodecSelector,
            allowedVideoJoiningTimeMs,
            enableDecoderFallback,
            eventHandler,
            eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY));

  }


  private class TrickPlayAwareMediaCodecVideoRenderer extends MediaCodecVideoRenderer {
    public static final String TAG = "TrickPlayAwareMediaCodecVideoRenderer";

    private TrickPlayControlInternal trickPlay;

    private long lastRenderTimeUs = C.TIME_UNSET;

    TrickPlayAwareMediaCodecVideoRenderer(
        TrickPlayControlInternal trickPlayController,
        Context context,
        MediaCodecSelector mediaCodecSelector,
        long allowedJoiningTimeMs,
        boolean enableDecoderFallback,
        @Nullable Handler eventHandler,
        @Nullable VideoRendererEventListener eventListener,
        int maxDroppedFramesToNotify) {
      super(context, mediaCodecSelector, allowedJoiningTimeMs, enableDecoderFallback,
          eventHandler, eventListener, maxDroppedFramesToNotify);

      trickPlay = trickPlayController;
      trickPlay.getCurrentTrickMode();
    }

    /**
     * Override render stop.
     *
     * When ExoPlayer starts buffering, reaches the end of stream, has a seek issued or is paused it
     * stops the MediaClock and tells the renderers to stop.  We clear the last render time to force
     * processing the next queued output buffer.
     */
    @Override
    protected void onStopped() {
      super.onStopped();
      Log.d(TAG, "Renderer onStopped() called - lastRenderTimeUs " + lastRenderTimeUs);
      lastRenderTimeUs = C.TIME_UNSET;
    }

    @Override
    protected void onStarted() {
      super.onStarted();
      Log.d(TAG, "Renderer onStarted() called - lastRenderTimeUs " + lastRenderTimeUs);
    }

    @Override
    protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
      super.onPositionReset(positionUs, joining);
      Log.d(TAG, "onPositionReset() -  readPosUs: " + getReadingPositionUs() + " lastRenderTimeUs: " + lastRenderTimeUs);
      lastRenderTimeUs = C.TIME_UNSET;    // Force a render on a discontinuity
    }

    @Override
    @RequiresApi(21)
    protected void renderOutputBufferV21(MediaCodec codec, int index, long presentationTimeUs,
        long releaseTimeNs) {
      super.renderOutputBufferV21(codec, index, presentationTimeUs, releaseTimeNs);
      long timeSinceLastRender =  lastRenderTimeUs == C.TIME_UNSET ? C.TIME_UNSET : (System.nanoTime() / 1000) - lastRenderTimeUs;
      lastRenderTimeUs = System.nanoTime() / 1000;

      if (trickPlay.isSmoothPlayAvailable() && trickPlay.getCurrentTrickDirection() != TrickPlayControl.TrickPlayDirection.NONE) {
        Log.d(TAG, "renderOutputBufferV21() in trickplay - pts: " + presentationTimeUs + " releaseTimeUs: " + (releaseTimeNs / 1000) + " index:" + index + " timeSinceLastUs: " + timeSinceLastRender);
        trickPlay.dispatchTrickFrameRender(presentationTimeUs);
      }
    }

  }

}
