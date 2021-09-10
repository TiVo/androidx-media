package com.google.android.exoplayer2.source.hls;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParser;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistTracker;
import com.google.android.exoplayer2.testutil.ExoPlayerTestRunner;
import com.google.android.exoplayer2.util.Util;


@RunWith(AndroidJUnit4.class)
public class HlsChunkSourceTest {

  public static final String TEST_PLAYLIST = "#EXTM3U\n" +
      "#EXT-X-MEDIA-SEQUENCE:1606273114\n" +
      "#EXT-X-VERSION:6\n" +
      "#EXT-X-PLAYLIST-TYPE:VOD\n" +
      "#EXT-X-I-FRAMES-ONLY\n" +
      "#EXT-X-INDEPENDENT-SEGMENTS\n" +
      "#EXT-X-MAP:URI=\"init-CCUR_iframe.tsv\"\n" +
      "#EXT-X-PROGRAM-DATE-TIME:2020-11-25T02:58:34+00:00\n" +
      "#EXTINF:4,\n" +
      "#EXT-X-BYTERANGE:52640@19965036\n" +
      "1606272900-CCUR_iframe.tsv\n" +
      "#EXTINF:4,\n" +
      "#EXT-X-BYTERANGE:77832@20253992\n" +
      "1606272900-CCUR_iframe.tsv\n" +
      "#EXTINF:4,\n" +
      "#EXT-X-BYTERANGE:168824@21007496\n" +
      "1606272900-CCUR_iframe.tsv\n" +
      "#EXTINF:4,\n" +
      "#EXT-X-BYTERANGE:177848@21888840\n" +
      "1606272900-CCUR_iframe.tsv\n" +
      "#EXTINF:4,\n" +
      "#EXT-X-BYTERANGE:69560@22496456\n" +
      "1606272900-CCUR_iframe.tsv\n" +
      "#EXTINF:4,\n" +
      "#EXT-X-BYTERANGE:41360@22830156\n" +
      "1606272900-CCUR_iframe.tsv\n" +
      "#EXT-X-ENDLIST\n" +
      "\n";
  public static final Uri PLAYLIST_URI = Uri.parse("http://example.com/");

  @Mock
  private HlsExtractorFactory mockExtractorFactory;

  @Mock
  private HlsPlaylistTracker mockPlaylistTracker;

  @Mock
  private HlsDataSourceFactory mockDataSourceFactory;
  private HlsChunkSource testee;

  @Before
  public void setup() throws IOException {
    // sadly, auto mock does not work, you get NoClassDefFoundError: com/android/dx/rop/type/Type
//    MockitoAnnotations.initMocks(this);
    mockExtractorFactory = Mockito.mock(HlsExtractorFactory.class);
    mockPlaylistTracker = Mockito.mock(HlsPlaylistTracker.class);
    mockDataSourceFactory = Mockito.mock(HlsDataSourceFactory.class);
    InputStream inputStream = new ByteArrayInputStream(Util.getUtf8Bytes(TEST_PLAYLIST));
    HlsMediaPlaylist playlist =
        (HlsMediaPlaylist) new HlsPlaylistParser().parse(PLAYLIST_URI, inputStream);

    when(mockPlaylistTracker.getPlaylistSnapshot(eq(PLAYLIST_URI), anyBoolean())).thenReturn(playlist);

    testee = new HlsChunkSource(
        mockExtractorFactory,
        mockPlaylistTracker,
        new Uri[] {PLAYLIST_URI},
        new Format[] { ExoPlayerTestRunner.VIDEO_FORMAT },
        mockDataSourceFactory,
        null,
        new TimestampAdjusterProvider(),
        null);

    assertThat(testee).isNotNull();
  }

  @Test
  public void getAdjustedSeekPositionUs_PreviousSync() {
    when(mockPlaylistTracker.isSnapshotValid(eq(PLAYLIST_URI))).thenReturn(true);

    long adjusted = testee.getAdjustedSeekPositionUs(17_000_000, SeekParameters.PREVIOUS_SYNC);
    assertThat(adjusted).isEqualTo(16_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUs_NextSync() {
    when(mockPlaylistTracker.isSnapshotValid(eq(PLAYLIST_URI))).thenReturn(true);

    long adjusted = testee.getAdjustedSeekPositionUs(17_000_000, SeekParameters.NEXT_SYNC);
    assertThat(adjusted).isEqualTo(20_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUs_NextSyncAtEnd() {
    when(mockPlaylistTracker.isSnapshotValid(eq(PLAYLIST_URI))).thenReturn(true);

    long adjusted = testee.getAdjustedSeekPositionUs(24_000_000, SeekParameters.NEXT_SYNC);
    assertThat(adjusted).isEqualTo(24_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUs_ClosestSync() {
    when(mockPlaylistTracker.isSnapshotValid(eq(PLAYLIST_URI))).thenReturn(true);

    long adjusted = testee.getAdjustedSeekPositionUs(17_000_000, SeekParameters.CLOSEST_SYNC);
    assertThat(adjusted).isEqualTo(16_000_000);

    adjusted = testee.getAdjustedSeekPositionUs(19_000_000, SeekParameters.CLOSEST_SYNC);
    assertThat(adjusted).isEqualTo(20_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUs_Exact() {
    when(mockPlaylistTracker.isSnapshotValid(eq(PLAYLIST_URI))).thenReturn(true);

    long adjusted = testee.getAdjustedSeekPositionUs(17_000_000, SeekParameters.EXACT);
    assertThat(adjusted).isEqualTo(17_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUs_NoIndependedSegments() {
    when(mockPlaylistTracker.isSnapshotValid(eq(PLAYLIST_URI))).thenReturn(true);

    // difficult to mock a final class.
    HlsMediaPlaylist mockPlaylist = new HlsMediaPlaylist(
        HlsMediaPlaylist.PLAYLIST_TYPE_UNKNOWN,
        PLAYLIST_URI.toString(),
        Collections.emptyList(),
        0,
        0,
        false,
        0,
        0,
        8,
        6,
        false,
        true,
        true,
        null,
        Collections.emptyList()
    );
    when(mockPlaylistTracker.getPlaylistSnapshot(eq(PLAYLIST_URI), anyBoolean())).thenReturn(mockPlaylist);
    long adjusted = testee.getAdjustedSeekPositionUs(100_000_000, SeekParameters.EXACT);
    assertThat(adjusted).isEqualTo(100_000_000);
  }

}
