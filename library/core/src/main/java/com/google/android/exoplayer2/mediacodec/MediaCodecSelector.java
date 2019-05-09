/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.mediacodec;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.os.Build;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.util.Log;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Selector of {@link MediaCodec} instances.
 */
public interface MediaCodecSelector {

  /**
   * Default implementation of {@link MediaCodecSelector}, which returns the preferred decoder for
   * the given format.
   */
  MediaCodecSelector DEFAULT =
      new MediaCodecSelector() {
        @Override
        public List<MediaCodecInfo> getDecoderInfos(String mimeType, boolean requiresSecureDecoder)
            throws DecoderQueryException {
          List<MediaCodecInfo> decoderInfos =
              MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder);
          return decoderInfos.isEmpty()
              ? Collections.emptyList()
              : Collections.singletonList(decoderInfos.get(0));
        }

        @Override
        public @Nullable MediaCodecInfo getPassthroughDecoderInfo() throws DecoderQueryException {
          return MediaCodecUtil.getPassthroughDecoderInfo();
        }
      };
    /**
     * Default implementation of {@link MediaCodecSelector}, which returns the preferred decoder for
     * the given format.
     */
    MediaCodecSelector TUNNELING =
        new MediaCodecSelector() {
          @TargetApi(Build.VERSION_CODES.N)
          @Override
          public List<MediaCodecInfo> getDecoderInfos(String mimeType, boolean requiresSecureDecoder)
              throws DecoderQueryException {
            List<MediaCodecInfo> decoderInfos =
                MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder);

            Optional<MediaCodecInfo> matchingInfo = decoderInfos.stream()
                    .filter(mediaCodecInfo -> mediaCodecInfo.tunneling)
                    .findFirst();
            return matchingInfo.isPresent() ? Collections.singletonList(matchingInfo.get()) : Collections.emptyList();
          }

          @Override
          public @Nullable MediaCodecInfo getPassthroughDecoderInfo() throws DecoderQueryException {
            return MediaCodecUtil.getPassthroughDecoderInfo();
          }
        };

  /**
   * A {@link MediaCodecSelector} that returns a list of decoders in priority order, allowing
   * fallback to less preferred decoders if initialization fails.
   *
   * <p>Note: if a hardware-accelerated video decoder fails to initialize, this selector may provide
   * a software video decoder to use as a fallback. Using software decoding can be inefficient, and
   * the decoder may be too slow to keep up with the playback position.
   */
  MediaCodecSelector DEFAULT_WITH_FALLBACK =
      new MediaCodecSelector() {
        @Override
        public List<MediaCodecInfo> getDecoderInfos(String mimeType, boolean requiresSecureDecoder)
            throws DecoderQueryException {
          return MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder);
        }

        @Override
        public @Nullable MediaCodecInfo getPassthroughDecoderInfo() throws DecoderQueryException {
          return MediaCodecUtil.getPassthroughDecoderInfo();
        }
      };

  MediaCodecSelector TUNNELING_VIDEO_AND_AUDIO_ALL = new MediaCodecSelector() {
      // This will be returned as a list sorted with tunneling supporting
      // codecs first, so that ExoPlayer's fairly dumb codec selection will
      // just happen to get a tunneling version when selecting the first
      // element, which it does
      public List<MediaCodecInfo> getDecoderInfos(String mimeType, boolean requiresSecureDecoder)
              throws MediaCodecUtil.DecoderQueryException {
          Log.i("TAG", "Asked for mimeType=" + mimeType +
                  ", requresSecureDecoder=" + requiresSecureDecoder);

          java.util.ArrayList<MediaCodecInfo> ret =
                  new java.util.ArrayList<MediaCodecInfo>();

          List<MediaCodecInfo> decoderInfos = MediaCodecUtil.getDecoderInfos
                  (mimeType, requiresSecureDecoder);

          if (false || !mimeType.startsWith("video/")) {
              Log.i("TAG", "gDisableTunneling=" + false +
                      ", mimeType.startsWith(\"video\")=" +
                      mimeType.startsWith("video/"));
              if (!decoderInfos.isEmpty()) {
                  Log.i("TAG", "Found a decoder for it: " +
                          decoderInfos.get(0).name);
                  ret.add(decoderInfos.get(0));
              }
              return ret;
          }

          for (int i = 0; i < decoderInfos.size(); i++) {
              MediaCodecInfo codecInfo = decoderInfos.get(i);
              if (codecInfo.tunneling &&
                      (!requiresSecureDecoder || codecInfo.secure)) {
                  Log.i("TAG", "Found tunneling codec: " + codecInfo.name);
                  ret.add(codecInfo);
                  return ret;
              }
          }

          // Add non-tunneling codecs now
          for (int i = 0; i < decoderInfos.size(); i++) {
              MediaCodecInfo codecInfo = decoderInfos.get(i);
              if (!requiresSecureDecoder || codecInfo.secure) {
                  Log.i("TAG", "Found non-tunneling codec: " + codecInfo.name);
                  ret.add(codecInfo);
                  return ret;
              }
          }

          // Nothing matches, return empty list
          return ret;
      }

      @Nullable
      @Override
      public MediaCodecInfo getPassthroughDecoderInfo() throws DecoderQueryException {
          return null;
      }
  };

  /**
   * Returns a list of decoders that can decode media in the specified MIME type, in priority order.
   *
   * @param mimeType The MIME type for which a decoder is required.
   * @param requiresSecureDecoder Whether a secure decoder is required.
   * @return A list of {@link MediaCodecInfo}s corresponding to decoders. May be empty.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  List<MediaCodecInfo> getDecoderInfos(String mimeType, boolean requiresSecureDecoder)
      throws DecoderQueryException;

  /**
   * Selects a decoder to instantiate for audio passthrough.
   *
   * @return A {@link MediaCodecInfo} describing the decoder, or null if no suitable decoder exists.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  @Nullable
  MediaCodecInfo getPassthroughDecoderInfo() throws DecoderQueryException;
}
