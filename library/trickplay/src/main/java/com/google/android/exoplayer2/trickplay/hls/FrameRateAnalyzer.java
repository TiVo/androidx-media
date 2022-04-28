package com.google.android.exoplayer2.trickplay.hls;

import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.util.Log;

/**
 * Keeps track of a set of iFrame tracks in a manifest and determines the frame rate they present.
 *
 * Object is constructed each time a manifest is parsed.  When a playlist owned by that manifest
 * is parsed, the class updates the frame rate associated with the playlist.
 *
 * This class is updated in the Main Application thread (from player EventListener callback) and used
 * by track selection in the player thread, so it must be thread safe.
 */
public class FrameRateAnalyzer {
  private static final String TAG = "FrameRateAnalyzer";

  @NonNull private final Map<FormatKey, Float> frameRatesByFormat;
  @Nullable private final Format iFrameOnlySourceFormat;

  /**
   * The HlsSampleStreamWrapper clones the sample {@link Format} object and combines it with
   * information from the {@link Format} from the manifest.  We are only interested in matching
   * based on the latter, so {@link Format#equals(Object)} will not work.
   */
  @VisibleForTesting
  static class FormatKey {
    private final Format baseFormat;

    FormatKey(Format format) {
      baseFormat = format;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      FormatKey formatKey = (FormatKey) o;
      return baseFormat.bitrate == formatKey.baseFormat.bitrate
          && baseFormat.width == formatKey.baseFormat.width
          && baseFormat.height == formatKey.baseFormat.height
          && Float.compare(formatKey.baseFormat.frameRate, baseFormat.frameRate) == 0;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public int hashCode() {
      return Objects.hash(baseFormat.bitrate, baseFormat.width, baseFormat.height, baseFormat.frameRate);
    }
  }

  public FrameRateAnalyzer(@Nullable Format sourceIframeOnly) {
    iFrameOnlySourceFormat = sourceIframeOnly;
    frameRatesByFormat = new HashMap<>();
  }

  /**
   * Handle playlist update.
   *
   * This method must be called on playlist update. It updates the frame rate based on the playlist
   * segments.
   *
   * @param masterPlaylist -- The master playlist containing the updated playlist
   * @param mediaPlaylist -- The updated playlist.
   * @return true if there was a match (if not, then the playlist must be messed up)
   */
   public synchronized boolean playlistUpdated(@NonNull HlsMasterPlaylist masterPlaylist, @NonNull HlsMediaPlaylist mediaPlaylist) {
    Uri targetUri = Uri.parse(mediaPlaylist.baseUri);
    HlsMasterPlaylist.Variant matched = null;
    Iterator<HlsMasterPlaylist.Variant> it = masterPlaylist.variants.listIterator();
    while (it.hasNext() && matched == null) {
      HlsMasterPlaylist.Variant candidate = it.next();
      if (candidate.url.equals(targetUri) ) {
        matched = candidate;
      }
    }
    if (matched != null) {
      if ((matched.format.roleFlags & C.ROLE_FLAG_TRICK_PLAY) == C.ROLE_FLAG_TRICK_PLAY) {
        float frameRate = analyzeFrameRate(mediaPlaylist);
        if (frameRate != Format.NO_VALUE) {
          final FormatKey key = new FormatKey(matched.format);
          if (! frameRatesByFormat.containsKey(key)) {
            Log.d(TAG, "initial frame rate of " + frameRate + " for format " + Format.toLogString(matched.format));
          }
          frameRatesByFormat.put(key, frameRate);
        } else {
          Log.w(TAG, "Skiping empty playlist frame rate calculation, for format "
              + Format.toLogString(matched.format) + " playlist URI: " + mediaPlaylist.baseUri);
        }
      }
    } else {
      Log.w(TAG, "playlist URI " + targetUri + " not matched in master playlist variants.");
    }

    return matched != null;
  }

  /**
   * Look up the actual frame rate for the media playlist (assumed to be iFrame only) associated with
   * the <i>format</i> and return it.
   *
   * The HLS 'FRAME-RATE' is in the {@link Format#frameRate}, however the intention of this is
   * for specifying normal video bitrate is over 30FPS, so in the spec it's OPTIONAL.
   *
   * For curated (internally generated) iFrame only variants, the {@link Format#frameRate} is set
   * to the fraction of the original source variant.  For example, if the curation half's the
   * frame rate of the source it's {@link Format#frameRate} would be 0.5f.  So once we measure any
   * the variant's frame rate, all other variants frame rates can be estimated.  So there are three
   * possible cases:
   *
   * <ol>
   *   <li>Playlist matching the input Format has loaded at least once</li>
   *   <li>Any other playlist has loaded at least once</li>
   *   <li>No playlist has loaded</li>
   * </ol>
   *
   * Case 1 -- The format has seen at least one update then the actual frame rate was computed by
   * {@link #analyzeFrameRate(HlsMediaPlaylist)}, return it.
   *
   * Case 2 -- if any other media playlist was updated, use it's measured frame rate times the the
   * ratio of the input {@link Format#frameRate} to the measured playlist's {@link Format#frameRate}
   * as an estimate of the input {@link Format}'s frame rate.  Say you have 2 dervied playlists and
   * a source playlist, given frame rates F1, F2, and Fs, this is the relationships
   *
   * F1 = Fs * Format1.frameRate
   * F2 = Fs * Format2.frameRate
   *
   * So if you know F1 from measuring and don't know Fs then you have:
   *   Fs = F1 / Format1.frameRate, thus
   *   F2 = F1 * Format2.frameRate / Format1.frameRate
   *
   * Case 3 -- No playlist has loaded, And the original source frame rate is completely unknown return
   * {@link Format#NO_VALUE}, else even if no playlist was loaded, if we know the original source
   * playlist frame rate, then compute similar to case 2 use the ratio of the two {@link Format#frameRate}
   * values
   *
   * @param format format to match to a playlist variant
   * @return the analyzed frame rate (if playlist update), an approximate (guessed) rate, or {@link Format#NO_VALUE}
   */
   public synchronized float getFrameRateFor(Format format) {
    Float value = frameRatesByFormat.get(new FormatKey(format));
    if (frameRatesByFormat.size() > 0 && value == null) {   // case 2
      Map.Entry<FormatKey, Float> entry = frameRatesByFormat.entrySet().iterator().next();
      float rateRatio = frameRateMultiplier(format) / frameRateMultiplier(entry.getKey().baseFormat);
      value = rateRatio * entry.getValue();
    } else if (frameRatesByFormat.size() == 0) {            // case 3
      value = (float) Format.NO_VALUE;

      // If original source frame rate is known, return it or use the ratio method
      if (iFrameOnlySourceFormat != null) {
        if (iFrameOnlySourceFormat.equals(format)) {
          value = iFrameOnlySourceFormat.frameRate;
        } else if (iFrameOnlySourceFormat.frameRate != Format.NO_VALUE) {
          value = iFrameOnlySourceFormat.frameRate * format.frameRate;
        }
      }
    }
     return value;
  }

  /**
   * Return the ratio to the source iFrame only format, or 1.0f if the input format
   * is a source iFrame only playlist
   *
   * @param format input iFrame format, for curated playlists the {@link Format#frameRate}
   *               is the ratio to the source iFrame playlist's frame rate
   * @return ratio
   */
  private float frameRateMultiplier(Format format) {
    boolean isSourceFormat = AugmentedPlaylistParser.SRC_FORMAT_LABEL.equals(format.label);
    return isSourceFormat ? 1.0f : format.frameRate;
  }

  /**
   * Looks at the set of iFrame only segments in an iFrame only playlist and
   * computes the average (mean) segment duration in seconds.  This can be used
   * to determine the frame rate (FPS) of the playlist, simply 1/returned value.
   *
   * If there are no segments in the playlist (duration is thus 0) returns
   * {@link Format#NO_VALUE}
   *
   * @param mediaPlaylist - media HLS playlist to analyze segments in
   * @return the average duration of a segment in seconds
   */
  @VisibleForTesting
  float analyzeFrameRate(@NonNull HlsMediaPlaylist mediaPlaylist) {
    long totalDurationUs = 0L;
    int frameCount = 0;
    for (HlsMediaPlaylist.Segment segment : mediaPlaylist.segments) {
      frameCount++;
      totalDurationUs += segment.durationUs;
    }
    return totalDurationUs > 0L ? (frameCount * 1_000_000.0f) / totalDurationUs : Format.NO_VALUE;
  }


  public static Format findIFrameOnlySourceFormat(HlsMasterPlaylist masterPlaylist) {
    Format sourceFormat = null;
    for (int i=0; i < masterPlaylist.variants.size() && sourceFormat == null; i++) {
      HlsMasterPlaylist.Variant variant = masterPlaylist.variants.get(i);
      if (AugmentedPlaylistParser.SRC_FORMAT_LABEL.equals(variant.format.label)) {
        sourceFormat = variant.format;
      }
    }
    return sourceFormat;
  }

}
