package com.google.android.exoplayer2.trickplay.hls;

import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
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

  private final Map<FormatKey, Float> frameRatesByFormat;

  /**
   * The HlsSampleStreamWrapper clones the sample {@link Format} object and combines it with
   * information from the {@link Format} from the manifest.  We are only interested in matching
   * based on the latter, so {@link Format#equals(Object)} will not work.
   */
  @VisibleForTesting
  static class FormatKey {
    private final int bitrate;
    private final int width;
    private final int height;
    private final float frameRate;

    FormatKey(Format format) {
      bitrate = format.bitrate;
      width = format.width;
      height = format.height;
      frameRate = format.frameRate;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      FormatKey formatKey = (FormatKey) o;
      return bitrate == formatKey.bitrate
          && width == formatKey.width
          && height == formatKey.height
          && Float.compare(formatKey.frameRate, frameRate) == 0;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public int hashCode() {
      return Objects.hash(bitrate, width, height, frameRate);
    }
  }

  public FrameRateAnalyzer() {
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
        final FormatKey key = new FormatKey(matched.format);
        if (! frameRatesByFormat.containsKey(key)) {
          Log.d(TAG, "initial frame rate of " + frameRate + " for format " + Format.toLogString(matched.format));
        }
        frameRatesByFormat.put(key, frameRate);
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
   * If the media playlist has seen at least one update then the actual frame rate was computed by
   * {@link #analyzeFrameRate(HlsMediaPlaylist)}, return this.  Otherwise if any other media playlist
   * was updated, use the ratio of the {@link Format#frameRate} values between the two playlists times the
   * updated playlist's measured frame rate value.  Lastly, return the input format's {@link Format#frameRate}
   * value, these are set assuming the original source iFrame playlist is 1 second duration iFrames, less then
   * perfect, but will update on first playlist load.
   *
   * This method is called by the player thread for track selection
   *
   * @param format format to match to a playlist variant
   * @return the analyzed frame rate (if playlist update) or approximate (guessed) rate
   */
   public synchronized float getFrameRateFor(Format format) {
    Float value = frameRatesByFormat.get(new FormatKey(format));
    if (value == null && frameRatesByFormat.size() > 0) {
      Map.Entry<FormatKey, Float> entry = frameRatesByFormat.entrySet().iterator().next();

      // If playlist for the requested format has not loaded, guess the frame rate by using
      // the ratio of frame rates from any other playlist that was loaded.
      value = (format.frameRate / entry.getKey().frameRate) * entry.getValue();
      Log.d(TAG, "getFrameRateFor() estimate for " + Format.toLogString(format) + " value " + value);
    } else if (value != null) {
      Log.d(TAG, "getFrameRateFor() format " + Format.toLogString(format) + " is " + value);
    }
    return value == null ? format.frameRate : value;
  }

  @VisibleForTesting
  float analyzeFrameRate(@NonNull HlsMediaPlaylist mediaPlaylist) {
    long totalDurationUs = 0L;
    int frameCount = 0;
    for (HlsMediaPlaylist.Segment segment : mediaPlaylist.segments) {
      frameCount++;
      totalDurationUs += segment.durationUs;
    }
    return (frameCount * 1_000_000.0f) / totalDurationUs;
  }
}
