package com.google.android.exoplayer2.trickplay.hls;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistParserFactory;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParserFactory;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.common.base.Charsets;
import static com.google.android.exoplayer2.trickplay.hls.HlsPlaylistUtils.createMockPlaylist;
import static com.google.android.exoplayer2.trickplay.hls.HlsPlaylistUtils.createMockSegment;
import static com.google.common.truth.Truth.assertThat;

@RunWith(AndroidJUnit4.class)
public class FrameRateAnalyzerTest {

  private static final String SRC_IFRAME_URI = "test_playlist.m3u8";
  public static final int TEST_SUBSET = 2;
  private HlsMasterPlaylist testMasterPlaylist;
  private ArrayList<HlsMediaPlaylist.Segment> test2SecondSegments;
  private ArrayList<HlsMediaPlaylist.Segment> test3SecondSegments;
  private Format baseFormat;
  private Format secondFormat;

  @Before
  public void setUp() throws Exception {
    baseFormat = new Format.Builder()
        .setRoleFlags(C.ROLE_FLAG_TRICK_PLAY)
        .setHeight(720)
        .setWidth(1280)
        .setPeakBitrate(1373400)
        .setFrameRate(1.0f)
        .build();

    secondFormat = baseFormat.buildUpon()
        .setPeakBitrate(baseFormat.peakBitrate / TEST_SUBSET)
        .setFrameRate(baseFormat.frameRate / TEST_SUBSET)
        .build();

    String testMaster = "#EXTM3U\n" +
        "#EXT-X-VERSION:5\n" +
        "#EXT-X-STREAM-INF:BANDWIDTH=2160000,CODECS=\"mp4a.40.2,avc1.64001f\",RESOLUTION=720x480\n" +
        "dummy.m3u8\n" +
        "#EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=" + baseFormat.peakBitrate + ",URI=\"" + SRC_IFRAME_URI
        + "\",CODECS=\"avc1.640020\",RESOLUTION=" + baseFormat.width + "x" + baseFormat.height + "\n";


    HlsPlaylistParserFactory parserFactory =
        new DualModeHlsPlaylistParserFactory(new DefaultHlsPlaylistParserFactory(), new int[] {TEST_SUBSET});
    ParsingLoadable.Parser<HlsPlaylist> masterParser = parserFactory.createPlaylistParser();
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(testMaster.getBytes(Charsets.UTF_8));
    testMasterPlaylist = (HlsMasterPlaylist) masterParser.parse(Uri.EMPTY, inputStream);

    test2SecondSegments = new ArrayList<>();
    test3SecondSegments = new ArrayList<>();
    for (int i=0; i<10; i++) {
      test2SecondSegments.add(createMockSegment("", 0, 0, 2_000_000));
      test3SecondSegments.add(createMockSegment("", 0, 0, ((i % 2) == 0) ? 2_000_000 : 4_000_000));
    }
  }

  @Test
  public void test_formatMatching() {
    FrameRateAnalyzer.FormatKey key = new FrameRateAnalyzer.FormatKey(baseFormat);

    assertThat(key).isNotEqualTo(new FrameRateAnalyzer.FormatKey(secondFormat));

    // FormatKey only considers the propegated Format.bitrate value, if it comes from avg or peak is not relevant
    Format avg = baseFormat.buildUpon()
        .setAverageBitrate(baseFormat.bitrate)
        .setPeakBitrate(Format.NO_VALUE)
        .build();

    assertThat(key).isEqualTo(new FrameRateAnalyzer.FormatKey(avg));


  }
  @Test
  public void test_validatePlaylistAssumptions() {
    // Setup should create a master playlist with one normal variant, and 2 iFrame variants
    // one of which is a clone created by DualModePlaylistParser
    //
    assertThat(testMasterPlaylist.variants.size()).isEqualTo(3);

    assertThat(testMasterPlaylist.variants.get(1).url.toString()).isEqualTo(SRC_IFRAME_URI);

  }


  @Test
  public void test_analyzeFrameRate() {
    FrameRateAnalyzer analyzer = new FrameRateAnalyzer();

    assertThat(analyzer.analyzeFrameRate(createMockPlaylist(test2SecondSegments, Uri.EMPTY))).isEqualTo(0.5f);

    final HlsMediaPlaylist playlist = createMockPlaylist(test3SecondSegments, Uri.EMPTY);
    assertThat(analyzer.analyzeFrameRate(playlist)).isWithin(0.001f).of(0.333f);
  }

  @Test
  public void test_getFrameRateFor_noLoadedPlaylists() {

    // No playlists loaded and no value for format frameRate should give the NO_VALUE return
    FrameRateAnalyzer analyzer = new FrameRateAnalyzer();
    assertThat(analyzer.getFrameRateFor(new Format.Builder().build())).isEqualTo(Format.NO_VALUE);

    // If frameRate specified in the format it should be returned if no playlists update

    assertThat(analyzer.getFrameRateFor(baseFormat)).isEqualTo(baseFormat.frameRate);

  }

  @Test
  public void test_getFrameRateFor() {
    FrameRateAnalyzer analyzer = new FrameRateAnalyzer();
    assertThat(analyzer.getFrameRateFor(new Format.Builder().build())).isEqualTo(Format.NO_VALUE);

    Uri uri = Uri.parse(SRC_IFRAME_URI);
    analyzer.playlistUpdated(testMasterPlaylist, createMockPlaylist(test2SecondSegments, uri));


    Uri curatedUri = uri.buildUpon()
        .fragment(Integer.toString(TEST_SUBSET))
        .build();
    analyzer.playlistUpdated(testMasterPlaylist, createMockPlaylist(test3SecondSegments, curatedUri));


    assertThat(analyzer.getFrameRateFor(baseFormat)).isEqualTo(0.5f);
    assertThat(analyzer.getFrameRateFor(secondFormat)).isWithin(0.001f).of(0.333f);
  }

  @Test
  public void test_getFrameRateFor_noUpdate() {
    FrameRateAnalyzer analyzer = new FrameRateAnalyzer();

    Uri uri = Uri.parse(SRC_IFRAME_URI);
    analyzer.playlistUpdated(testMasterPlaylist, createMockPlaylist(test2SecondSegments, uri));


    assertThat(analyzer.getFrameRateFor(baseFormat)).isEqualTo(0.5f);

    // since playlist was never loaded for the second format, it should be derived based on the multiple
    // second format's frame rate is 1/2 the base so should be 0.25
    //
    assertThat(analyzer.getFrameRateFor(secondFormat)).isEqualTo(0.25f);
  }


}
