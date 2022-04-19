package com.google.android.exoplayer2.trickplay.hls;

import android.net.Uri;

import java.util.Collections;
import java.util.List;

import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;

/**
 * gather test code that depends on API's in Google ExoPlayer hls package for one place to
 * update.
 */
public class HlsPlaylistUtils {

  public static HlsMediaPlaylist.Segment createMockSegment(String uri, int byteRangeOffset, int byteRangeLength, long durationUs) {
    return new HlsMediaPlaylist.Segment(
        uri,
        null,
        "",
        durationUs,
        1,
        0,
        null,
        null,
        null,
        byteRangeOffset,
        byteRangeLength,
        false);
  }

  static HlsMediaPlaylist createMockPlaylist(List<HlsMediaPlaylist.Segment> mockSegments, Uri baseUri) {
    return new HlsMediaPlaylist(
        HlsMediaPlaylist.PLAYLIST_TYPE_UNKNOWN,
        baseUri.toString(),
        Collections.emptyList(),
        0,
        1_000_000,
        false,
        0,
        123,
        8,
        6,
        true,
        false,
        true,
        null,
        mockSegments
    );
  }
}
