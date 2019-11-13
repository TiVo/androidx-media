package com.google.android.exoplayer2.trickplay;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Handler;
import androidx.annotation.Nullable;

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
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys,
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
            drmSessionManager,
            playClearSamplesWithoutKeys,
            eventHandler,
            eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY));

  }


  private class TrickPlayAwareMediaCodecVideoRenderer extends MediaCodecVideoRenderer {
    public static final String TAG = "TrickPlayAwareMediaCodecVideoRenderer";

    private TrickPlayControlInternal trickPlay;

    public static final int TARGET_FRAME_RATE = 15;

    private int targetInterFrameTimeUs = 1000000 / TARGET_FRAME_RATE;
    private long lastRenderTimeUs = C.TIME_UNSET;
    private long lastRenderedPositionUs = C.TIME_UNSET;

    TrickPlayAwareMediaCodecVideoRenderer(
        TrickPlayControlInternal trickPlayController,
        Context context,
        MediaCodecSelector mediaCodecSelector,
        long allowedJoiningTimeMs,
        @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
        boolean playClearSamplesWithoutKeys,
        @Nullable Handler eventHandler,
        @Nullable VideoRendererEventListener eventListener,
        int maxDroppedFramesToNotify) {
      super(context, mediaCodecSelector, allowedJoiningTimeMs, drmSessionManager, playClearSamplesWithoutKeys,
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
      Log.d(TAG, "Renderer onStarted() called - lastRenderTimeUs " + lastRenderTimeUs + " targetInterFrameTimeUs:" + targetInterFrameTimeUs);
    }

    @Override
    protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
      super.onPositionReset(positionUs, joining);
      Log.d(TAG, "onPositionReset() -  readPosUs: " + getReadingPositionUs() + " lastRenderTimeUs: " + lastRenderTimeUs);
      lastRenderTimeUs = C.TIME_UNSET;    // Force a render on a discontinuity
    }


    @Override
    protected int readSource(FormatHolder formatHolder, DecoderInputBuffer buffer, boolean formatRequired) {
      int result;
      if (trickPlay.useTrickPlayRendering()) {
        long elapsedRealtimeNowUs = System.nanoTime() / 1000;
        boolean nextFrameIsDue = lastRenderTimeUs == C.TIME_UNSET || (elapsedRealtimeNowUs - lastRenderTimeUs) >= targetInterFrameTimeUs;
        Log.d(TAG, "readSource() -  readPosUs: " + getReadingPositionUs() + " lastRenderTimeUs: " + lastRenderTimeUs + " frameDue: " + nextFrameIsDue + " formatRequired: " + formatRequired + " track: " + formatHolder.format.id);

        if (nextFrameIsDue) {
          result = super.readSource(formatHolder, buffer, formatRequired);
          if (result != C.RESULT_BUFFER_READ) {
            lastRenderTimeUs = C.TIME_UNSET;    // Force nextFrameDue again till we read an actual sample buffer.
          } else {
            Log.d(TAG, "super.readSource() - readPosUs: " + getReadingPositionUs() + " returned buffer - PTS: "+ buffer.timeUs + " decodeOnly: " + buffer.isDecodeOnly() + " keyFrame: " + buffer.isKeyFrame());
          }
        } else {
          result = C.RESULT_NOTHING_READ;
        }
      } else {
        result = super.readSource(formatHolder, buffer, formatRequired);
      }

      return result;
    }

    @Override
    protected void renderOutputBufferV21(MediaCodec codec, int index, long presentationTimeUs,
        long releaseTimeNs) {
      super.renderOutputBufferV21(codec, index, presentationTimeUs, releaseTimeNs);
//      Log.d(TAG, "onRenderOutputBuffer() - pts: " + presentationTimeUs + " releaseTimeUs: " + (releaseTimeNs / 1000) + " index:" +index);
      lastRenderTimeUs = System.nanoTime() / 1000;
      lastRenderedPositionUs = presentationTimeUs;

      if (trickPlay.useTrickPlayRendering()) {
        trickPlay.dispatchTrickFrameRender(lastRenderedPositionUs);
      }
    }

  }

}
