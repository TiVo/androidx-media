package com.google.android.exoplayer2.trickplay;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.os.Build;
import android.os.Handler;
import androidx.annotation.Nullable;

import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

import java.nio.ByteBuffer;
import java.util.ArrayList;

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
    private long lastRenderPositionUs = C.TIME_UNSET;
    private int duplicateIframeCount = 0;
    private ByteBuffer lastIFrameData = null;
    private boolean needsMultipleInputBuffersWorkaround;

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
    protected void configureCodec(MediaCodecInfo codecInfo, MediaCodecAdapter codecAdapter, Format format, @Nullable MediaCrypto crypto, float codecOperatingRate) {
      super.configureCodec(codecInfo, codecAdapter, format, crypto, codecOperatingRate);
      needsMultipleInputBuffersWorkaround = codecNeedsMultipleInputWorkaround(codecInfo.name);
    }

    /**
     * Broadcom video codec implementations seem to require some number of frames to be queued for
     * decode before they will produce the first decoded output frame.
     *
     * @param name the codec name to check
     * @return true if workaround is requiried
     */
    private boolean codecNeedsMultipleInputWorkaround(@Nullable String name) {
      return "OMX.broadcom.video_decoder".equals(name);
    }

    /**
     * Override to queue the same i-Frame mulitple times to the decoder, if this is required to
     * make it produce an output frame.
     *
     * Algorithm is simple, keep a copy of the last frame's data in an buffer, copy it to the
     * input DecoderInputBuffer without calling super.readSource() until the required frame count is
     * reached.  The value of 3 frames was empirically determined
     *
     * This is a workaround for https://jira.xperi.com/browse/PARTDEFECT-11896
     *
     * @param formatHolder A {@link FormatHolder} to populate in the case of reading a format.
     * @param buffer A {@link DecoderInputBuffer} to populate in the case of reading a sample or the
     *     end of the stream. If the end of the stream has been reached, the {@link
     *     C#BUFFER_FLAG_END_OF_STREAM} flag will be set on the buffer.
     * @param formatRequired Whether the caller requires that the format of the stream be read even if
     *     it's not changing. A sample will never be read if set to true, however it is still possible
     *     for the end of stream or nothing to be read.
     *
     * @return super value, or RESULT_BUFFER_READ to re-queue the last iframe
     */
    @Override
    protected int readSource(FormatHolder formatHolder, DecoderInputBuffer buffer, boolean formatRequired) {
      int returnValue;
      if (trickPlay.getCurrentTrickDirection() == TrickPlayControl.TrickPlayDirection.REVERSE && needsMultipleInputBuffersWorkaround) {
        if (lastIFrameData == null) {
          returnValue = super.readSource(formatHolder, buffer, /* formatRequired= */ formatRequired);
          if (returnValue == C.RESULT_BUFFER_READ && buffer.isKeyFrame()) {
            lastIFrameData = buffer.data.duplicate();
          }
        } else {
          if (duplicateIframeCount++ > 2) {
            duplicateIframeCount = 0;
            returnValue = super.readSource(formatHolder, buffer, /* formatRequired= */ formatRequired);
            lastIFrameData = null;
          } else {
            buffer.data = lastIFrameData;
            returnValue = C.RESULT_BUFFER_READ;
          }
        }
      } else {
        returnValue = super.readSource(formatHolder, buffer, /* formatRequired= */ formatRequired);
      }
      return returnValue;
    }

    @Override
    protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
      super.onPositionReset(positionUs, joining);
      Log.d(TAG, "onPositionReset() -  renderPosition: " + C.usToMs(positionUs) + " readPosition: " + C.usToMs(getReadingPositionUs()) + " lastRenderTimeUs: " + lastRenderTimeUs);
      lastRenderTimeUs = C.TIME_UNSET;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onQueueInputBuffer(DecoderInputBuffer buffer) throws ExoPlaybackException {
      super.onQueueInputBuffer(buffer);
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
    protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, @Nullable MediaCodec codec, @Nullable ByteBuffer buffer, int bufferIndex, int bufferFlags, int sampleCount, long bufferPresentationTimeUs, boolean isDecodeOnlyBuffer, boolean isLastBuffer, Format format) throws ExoPlaybackException {
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
    protected void renderOutputBufferV21(MediaCodec codec, int index, long presentationTimeUs,
        long releaseTimeNs) {
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
