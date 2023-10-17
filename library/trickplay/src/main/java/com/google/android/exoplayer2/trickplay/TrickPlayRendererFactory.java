package com.google.android.exoplayer2.trickplay;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Build;
import android.os.Handler;
import android.os.Bundle;
import androidx.annotation.Nullable;

import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

import java.nio.ByteBuffer;
import java.util.ArrayList;

class TrickPlayRendererFactory extends DefaultRenderersFactory {
  public static final String TAG = "TrickPlayRendererFactory";

  protected static final int MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY = 10;

  private final TrickPlayControlInternal trickPlayController;
  private boolean enableAsyncQueueing;
  private boolean forceAsyncQueueingSynchronizationWorkaround;

  TrickPlayRendererFactory(Context context, TrickPlayControlInternal controller) {
    super(context);
    trickPlayController = controller;
    enableAsyncQueueing = false;
    forceAsyncQueueingSynchronizationWorkaround = false;
    setAllowedVideoJoiningTimeMs(0);
  }

  @Override
  public DefaultRenderersFactory experimentalSetAsynchronousBufferQueueingEnabled(boolean enabled) {
    enableAsyncQueueing = enabled;
    return super.experimentalSetAsynchronousBufferQueueingEnabled(enabled);
  }

  @Override
  public DefaultRenderersFactory experimentalSetForceAsyncQueueingSynchronizationWorkaround(
      boolean enabled) {
    forceAsyncQueueingSynchronizationWorkaround = enabled;
    return super.experimentalSetForceAsyncQueueingSynchronizationWorkaround(enabled);
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

    MediaCodecVideoRenderer videoRenderer = new TrickPlayAwareMediaCodecVideoRenderer(
            trickPlayController,
            context,
            mediaCodecSelector,
            allowedVideoJoiningTimeMs,
            enableDecoderFallback,
            eventHandler,
            eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
    videoRenderer.experimentalSetAsynchronousBufferQueueingEnabled(enableAsyncQueueing);
    videoRenderer.experimentalSetForceAsyncQueueingSynchronizationWorkaround(forceAsyncQueueingSynchronizationWorkaround);
    out.add(videoRenderer);
  }


  private class TrickPlayAwareMediaCodecVideoRenderer extends MediaCodecVideoRenderer {
    public static final String TAG = "TrickPlayAwareMediaCodecVideoRenderer";

    private TrickPlayControlInternal trickPlay;

    private long lastRenderTimeUs = C.TIME_UNSET;
    private long lastRenderPositionUs = C.TIME_UNSET;

    private static final int BCM_DECODE_ALL = 1;
    private static final int BCM_DECODE_IDR = 2;
    private static final int BCM_DECODE_FULL_SPEED = 1000;
    private static final int BCM_DISABLE = 0;
    private static final int BCM_ENABLE = 1;
    private boolean skipFlush = false;

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
      trickPlay.enablePlaybackSpeedForwardTrickPlay(!codecRequiresTunnelingTrickModeVsync());
      super.onStarted();
      Log.d(TAG, "Renderer onStarted() called - lastRenderTimeUs " + lastRenderTimeUs);
    }

    private boolean codecRequiresTunnelingTrickModeVsync() {
      @Nullable MediaCodecAdapter codec = getCodec();
      @Nullable MediaCodecInfo codecInfo = getCodecInfo();
      return (codecInfo!=null && codec!=null &&
              codecInfo.name.contains("tunnel") &&
              codecInfo.name.startsWith("OMX.bcm.vdec"));
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void configureVendorTrickMode() {
      if (codecRequiresTunnelingTrickModeVsync()) {
        Bundle codecParameters = new Bundle();
        if (trickPlay.getCurrentTrickMode() == TrickPlayControl.TrickMode.NORMAL) {
          Log.d(TAG, "disabling vendor.brcm.tunnel-trickmode-vsync");
          // Go back to tunneling TSM
          codecParameters.putInt("vendor.brcm.tunnel-trickmode-vsync", BCM_DISABLE);
          // Decode at normal speed
          codecParameters.putInt("vendor.brcm.tunnel-trickmode-decode-rate", BCM_DECODE_FULL_SPEED);
          codecParameters.putInt("vendor.brcm.tunnel-trickmode-decode-mode", BCM_DECODE_ALL);
        }
        else {
          Log.d(TAG, "enabling vendor.brcm.tunnel-trickmode-vsync");
          //Release frames relative to vsync
          codecParameters.putInt("vendor.brcm.tunnel-trickmode-vsync", BCM_ENABLE);
          codecParameters.putInt("vendor.brcm.tunnel-trickmode-decode-rate", BCM_DECODE_FULL_SPEED);
          codecParameters.putInt("vendor.brcm.tunnel-trickmode-decode-mode", BCM_DECODE_IDR);
        }
        @Nullable MediaCodecAdapter codec = getCodec();
        if (codec!=null) {
          codec.setParameters(codecParameters);
        }
      }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onEnabled(boolean joining, boolean mayRenderStartOfStream) throws ExoPlaybackException {
      Log.d(TAG, "Renderer onEnabled()");
      super.onEnabled(joining, mayRenderStartOfStream);
      configureVendorTrickMode();
      // Skip the flush if we are doing seek-based FW or RW VTP. We are only sending 1 frame per seek
      // so there should be nothing to flush. The flush also resets the BCM decoder into a state where
      // it needs 2 frames to display. The flush also requires us to delay the next sync until it reaches
      // the display on all platforms.
      skipFlush = (trickPlay.getCurrentTrickDirection() == TrickPlayControl.TrickPlayDirection.REVERSE ||
              (trickPlay.getCurrentTrickDirection() == TrickPlayControl.TrickPlayDirection.FORWARD &&
                      !trickPlay.isPlaybackSpeedForwardTrickPlayEnabled()));
    }

    @Override
    protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
      super.onPositionReset(positionUs, joining);
      Log.d(TAG, "onPositionReset() -  renderPosition: " + C.usToMs(positionUs) + " readPosition: " + C.usToMs(getReadingPositionUs()) + " lastRenderTimeUs: " + lastRenderTimeUs);
      lastRenderTimeUs = C.TIME_UNSET;
    }

    @Override
    protected boolean flushOrReleaseCodec() {
      if (!skipFlush) {
        return super.flushOrReleaseCodec();
      } else {
        super.flushForSingleIFrameSeek();
        return false;
      }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onQueueInputBuffer(DecoderInputBuffer buffer) throws ExoPlaybackException {
      super.onQueueInputBuffer(buffer);
      if (skipFlush && buffer.isKeyFrame()) {
        trickPlay.dispatchTrickFrameRender(buffer.timeUs);
        Log.d(TAG, "onQueueInputBuffer() trick play pts: " + buffer.timeUs);
      }

      switch (trickPlay.getCurrentTrickDirection()) {
         case FORWARD:
         case NONE:
           break;

         case SCRUB:
         case REVERSE:
           Log.d(TAG, "queueInputBuffer: timeMs: " + C.usToMs(buffer.timeUs) + " length: " + buffer.data.limit()
               + " isKeyFrame: " + buffer.isKeyFrame()
               + " isDecodeOnly: " + buffer.isDecodeOnly()
               + " isDiscontinuity: " + buffer.isDiscontinuity());
       }
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
      super.render(positionUs, elapsedRealtimeUs);
      lastRenderPositionUs = positionUs;
    }

    @Override
    protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs,
        @Nullable MediaCodecAdapter codec, @Nullable ByteBuffer buffer, int bufferIndex,
        int bufferFlags, int sampleCount, long bufferPresentationTimeUs, boolean isDecodeOnlyBuffer,
        boolean isLastBuffer, Format format) throws ExoPlaybackException {
      switch (trickPlay.getCurrentTrickDirection()) {
        case FORWARD:
        case NONE:
          break;

        case SCRUB:
        case REVERSE:
          isDecodeOnlyBuffer = false;
          break;
      }

      return super.processOutputBuffer(positionUs, elapsedRealtimeUs, codec, buffer, bufferIndex, bufferFlags, sampleCount, bufferPresentationTimeUs, isDecodeOnlyBuffer, isLastBuffer, format);
    }

    @Override
    @RequiresApi(21)
    protected void renderOutputBufferV21(MediaCodecAdapter codec, int index,
        long presentationTimeUs, long releaseTimeNs) {
      super.renderOutputBufferV21(codec, index, presentationTimeUs, releaseTimeNs);
      long timeSinceLastRender =  lastRenderTimeUs == C.TIME_UNSET ? C.TIME_UNSET : (System.nanoTime() / 1000) - lastRenderTimeUs;
      lastRenderTimeUs = System.nanoTime() / 1000;
      long positionDeltaUs = presentationTimeUs - lastRenderPositionUs;

      if (trickPlay.isSmoothPlayAvailable() && trickPlay.getCurrentTrickDirection() != TrickPlayControl.TrickPlayDirection.NONE) {
        Log.d(TAG, "renderOutputBufferV21() in trickplay - timestamp: " + C.usToMs(presentationTimeUs) + " delta(us): " + positionDeltaUs + " releaseTimeUs: " + (releaseTimeNs / 1000) + " index:" + index + " timeSinceLastUs: " + timeSinceLastRender);
        trickPlay.dispatchTrickFrameRender(presentationTimeUs);
      }
    }

  }

}
