package com.google.android.exoplayer2.trickplay;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Handler;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Renderer;
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

class TrickPlayRendererFactory extends DefaultRenderersFactory {

  public static final String TAG = "TRICK-PLAY";
  private final TrickPlayController trickPlayController;

  public TrickPlayRendererFactory(Context context, TrickPlayController controller) {
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

    private TrickPlayController trickPlay;

    private TrickPlayControl.TrickMode previousTrickMode;

    public static final int TARGET_FRAME_RATE = 1;        // Target 4 frames per second.

    private int targetInterFrameTimeUs = 1000000 / TARGET_FRAME_RATE;
    private long lastRenderTimeUs = C.TIME_UNSET;
    private long lastRenderedPositionUs = C.TIME_UNSET;

    TrickPlayAwareMediaCodecVideoRenderer(
        TrickPlayController trickPlayController,
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
      previousTrickMode = trickPlay.getCurrentTrickMode();
    }

    /**
     * When the trickplay mode changes, we need to flush the codec, as when we change back to normal mode this method
     * flushes the codec, as all the to be decoded/rendered frames are IDR frames only, conversely when we change to
     * a trickplay mode we only want the IDR frames.
     *
     * @param positionUs
     * @param elapsedRealtimeUs
     * @throws ExoPlaybackException
     */
    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
      TrickPlayControl.TrickMode currentTrickMode = trickPlayController.getCurrentTrickMode();
      if (previousTrickMode != currentTrickMode) {
        lastRenderTimeUs = C.TIME_UNSET;        // Reset frame rate counter

        Log.d(TAG, "flushing codec, mode changed from " + previousTrickMode + " to " + currentTrickMode);
        flushOrReinitializeCodec();
      }
      previousTrickMode = currentTrickMode;
      super.render(positionUs, elapsedRealtimeUs);
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
      lastRenderedPositionUs = positionUs;
    }

    @Override
    protected void onQueueInputBuffer(DecoderInputBuffer buffer) {
      if (useTrickPlayRendering()) {
        if (buffer.isKeyFrame()) {
          Log.d(TAG,"Queued trick buffer: bufferTs: " + buffer.timeUs + " lastRenderedPostionUs: " + lastRenderedPositionUs + " lastRenderTimeUs: " + lastRenderTimeUs);
        } else {
          buffer.data.limit(0);       // Silently discard non-key frame
        }
        super.onQueueInputBuffer(buffer);

      } else {
//                Log.d(TAG,"Queued input buffer: bufferTs: " + buffer.timeUs + " lastRenderedPostionUs: " + lastRenderedPositionUs + " lastRenderTimeUs: " + lastRenderTimeUs + "isKey: " + buffer.isKeyFrame());

        super.onQueueInputBuffer(buffer);
      }
    }

    @Override
    protected boolean processOutputBuffer(
        long positionUs,
        long elapsedRealtimeUs,
        MediaCodec codec,
        ByteBuffer buffer,
        int bufferIndex,
        int bufferFlags,
        long bufferPresentationTimeUs,
        boolean isDecodeOnlyBuffer,
        boolean isLastBuffer,
        Format format)
        throws ExoPlaybackException {
      boolean didProcess;
      long elapsedRealtimeNowUs = System.nanoTime() / 1000;

      if (isDecodeOnlyBuffer && !isLastBuffer) {
        skipOutputBuffer(codec, bufferIndex, bufferPresentationTimeUs);
        return true;
      }

      if (useTrickPlayRendering()) {
        long earlyUs = bufferPresentationTimeUs - positionUs;
//                long earlyUs = 0;

        if (earlyUs < 0) {
          Log.d(TAG, bufferPresentationTimeUs + " discard late trick frame " + bufferPresentationTimeUs + " releaseTime: " + elapsedRealtimeNowUs + " earlyUs: " + earlyUs + " sinceLastRenderUs: " + (elapsedRealtimeNowUs - lastRenderTimeUs));
          dropOutputBuffer(codec, bufferIndex, bufferPresentationTimeUs);
          didProcess = true;
        } else {

          boolean renderIsDue = frameRenderIsDue(elapsedRealtimeNowUs, earlyUs);
//                    boolean renderIsDue = true;
          if (renderIsDue) {

            Log.d(TAG, bufferPresentationTimeUs + " rendered trick frame " + bufferPresentationTimeUs + " releaseTime: " + elapsedRealtimeNowUs + " earlyUs: " + earlyUs + " sinceLastRenderUs: " + (elapsedRealtimeNowUs - lastRenderTimeUs) + " bufferIndex:" + bufferIndex);

            // TODO - this is private, but it shouldn't matter.
            //                      notifyFrameMetadataListener(bufferPresentationTimeUs, elapsedRealtimeNowUs, format);

            if (Util.SDK_INT >= 21) {
              try {
                renderOutputBufferV21(codec, bufferIndex, bufferPresentationTimeUs, elapsedRealtimeNowUs * 1000);
              } catch (Exception e) {
                e.printStackTrace();
              }
            } else {
              // TODO, if we care... Even fireTV is at 22
              didProcess = super.processOutputBuffer(positionUs, elapsedRealtimeUs, codec, buffer, bufferIndex, bufferFlags, bufferPresentationTimeUs, isDecodeOnlyBuffer, isLastBuffer, format);
            }

            lastRenderTimeUs = elapsedRealtimeNowUs;
            lastRenderedPositionUs = positionUs;

          } else {
            Log.d(TAG, bufferPresentationTimeUs + " holding trick frame " + bufferPresentationTimeUs + " releaseTime: " + elapsedRealtimeNowUs + " earlyUs: " + earlyUs + " sinceLastRenderUs: " + (elapsedRealtimeNowUs - lastRenderTimeUs));
          }

          didProcess = renderIsDue;
        }
      } else {
        lastRenderTimeUs = elapsedRealtimeNowUs;
        lastRenderedPositionUs = positionUs;
        didProcess = super.processOutputBuffer(positionUs, elapsedRealtimeUs, codec, buffer, bufferIndex, bufferFlags, bufferPresentationTimeUs, isDecodeOnlyBuffer, isLastBuffer, format);
      }

      return didProcess;
    }

    /**
     * If should override with trickplay rendering operations.
     *
     * Currently only forward modes are supported for trickplay
     *
     * @return true to use trick-play rendering.
     */
    private boolean useTrickPlayRendering() {
      boolean shouldUse;

      switch (trickPlay.getCurrentTrickMode()) {
        case FF2:
        case FF3:
          shouldUse = true;
          break;
        default:
          shouldUse = false;
          break;
      }
      return shouldUse;
    }

    /**
     * Is it time to render the output buffer?
     *
     * If the buffer is ready to be rendered (PTS is less then 30ms from  current program render time) and we are
     * not exceeding the target frame rate for trick play then yes it's time, return true.
     *
     * @param currentTimeUs - current time, in microseconds
     * @param earlyUs - buffer's PTS relative to current program render time
     * @return
     */
    private boolean frameRenderIsDue(long currentTimeUs, long earlyUs) {
      boolean frameMeetsFrameRateTarget = (currentTimeUs - lastRenderTimeUs) >= targetInterFrameTimeUs;
      return lastRenderTimeUs == C.TIME_UNSET || (earlyUs < 30000 && frameMeetsFrameRateTarget);

    }
  }

}
