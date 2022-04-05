package com.google.android.exoplayer2.trickplay.hls;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistParserFactory;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import static com.google.common.truth.Truth.assertThat;

@RunWith(AndroidJUnit4.class)
public class SmallestIFrameCuratorTest {

  private HlsMediaPlaylist mediaPlaylist;
  private ArrayList<HlsMediaPlaylist.Segment> mockSegments;
  private HlsMediaPlaylist mockPreviousPlaylist;
  private HlsMediaPlaylist currentPlaylist1;
  private HlsMediaPlaylist previousPlaylist1;
  private HlsMediaPlaylist currentPlaylist2;
  private HlsMediaPlaylist previousPlaylist2;

  @Before
  public void setupTest() throws IOException {
    InputStream testPlaylistStream = TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), "testplaylist.m3u8");
    ParsingLoadable.Parser<HlsPlaylist> playlistParser = new DefaultHlsPlaylistParserFactory().createPlaylistParser();
    mediaPlaylist = (HlsMediaPlaylist) playlistParser.parse(Uri.EMPTY, testPlaylistStream);
    mockSegments = new ArrayList<>(Arrays.asList(
        new HlsMediaPlaylist.Segment("1.ts", 1, 10, null, null),
        new HlsMediaPlaylist.Segment("2.ts", 2, 10, null, null),
        new HlsMediaPlaylist.Segment("3.ts", 3, 10, null, null),
        new HlsMediaPlaylist.Segment("4.ts", 4, 10, null, null)
    ));
    mockPreviousPlaylist = new HlsMediaPlaylist(
        HlsMediaPlaylist.PLAYLIST_TYPE_UNKNOWN,
        "",
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

    testPlaylistStream = TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), "testplaylist_update_current_1.m3u8");
    currentPlaylist1 = (HlsMediaPlaylist) playlistParser.parse(Uri.EMPTY, testPlaylistStream);
    testPlaylistStream = TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), "testplaylist_update_previous_1.m3u8");
    previousPlaylist1 = (HlsMediaPlaylist) playlistParser.parse(Uri.EMPTY, testPlaylistStream);

    assertThat(currentPlaylist1.isUpdateValid(previousPlaylist1)).isTrue();

    testPlaylistStream = TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), "testplaylist_update_current_2.m3u8");
    currentPlaylist2 = (HlsMediaPlaylist) playlistParser.parse(Uri.EMPTY, testPlaylistStream);
    testPlaylistStream = TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), "testplaylist_update_previous_2.m3u8");
    previousPlaylist2 = (HlsMediaPlaylist) playlistParser.parse(Uri.EMPTY, testPlaylistStream);

    assertThat(currentPlaylist2.isUpdateValid(previousPlaylist2)).isTrue();
  }


  @Test
  public void testMinSizeSegment() {
    SmallestIFramesCurator testee = new SmallestIFramesCurator();
    HlsMediaPlaylist.Segment result = testee.smallestSegmentInRange(mediaPlaylist.segments, 3, 9);
    // #EXT-X-BYTERANGE:97760@623408
    assertThat(result.byteRangeLength).isEqualTo(97760);
    assertThat(result.byteRangeOffset).isEqualTo(623408);
  }

  @Test
  public void testIncludesLastSegment() {
    SmallestIFramesCurator testee = new SmallestIFramesCurator();
    int lastIndex = mediaPlaylist.segments.size() - 1;
    HlsMediaPlaylist.Segment last = mediaPlaylist.segments.get(lastIndex);
    HlsMediaPlaylist.Segment result = testee.smallestSegmentInRange(mediaPlaylist.segments, lastIndex, lastIndex);
    assertThat(result).isSameInstanceAs(last);
  }

  @Test
  public void testCurateSmallest() {
    SmallestIFramesCurator testee = new SmallestIFramesCurator();
    List<HlsMediaPlaylist.Segment> result = testee.curateSmallestIFrames(mediaPlaylist, 10);
    int expected = (int) (Math.ceil(mediaPlaylist.segments.size() / 10) + 1);
    assertThat(result.size()).isEqualTo(expected);

    // The second segment should have the discontinuity now
    assertThat(result.get(1).relativeDiscontinuitySequence).isEqualTo(1);

    long totalDuration = 0;
    for (HlsMediaPlaylist.Segment segment : result) {
      totalDuration += segment.durationUs;

      // The nature of how Google playlist parser assigns relativeDiscontinuitySequence makes
      // this so, and curation depends on it
      HlsMediaPlaylist.Segment original = SmallestIFramesCurator.matchingSegmentFromList(segment, mediaPlaylist.segments);
      assertThat(original).isNotNull();
      assertThat(original.relativeDiscontinuitySequence).isEqualTo(segment.relativeDiscontinuitySequence);
//            System.out.println("duration\t"+segment.durationUs);
    }
    assertThat(totalDuration).isEqualTo(mediaPlaylist.durationUs);

  }


  @Test
  public void testComputePlaylistUpdates() {
    SmallestIFramesCurator testee = new SmallestIFramesCurator(previousPlaylist1);

    SmallestIFramesCurator.PlaylistUpdates updates = testee.computePlaylistUpdates(currentPlaylist1, previousPlaylist1);
    assertThat(updates.removeCount).isEqualTo((int) currentPlaylist1.mediaSequence - previousPlaylist1.mediaSequence);
    assertThat(updates.discontinuityDelta).isEqualTo(2);
    assertThat(updates.timeDeltaUs).isEqualTo(42042000);
    assertThat(updates.addedSegments.size()).isEqualTo(21);
    assertThat(updates.addedSegments.get(0).url).isEqualTo("seg_add_1");

    // and with curated version of source
    HlsMediaPlaylist curated = new SmallestIFramesCurator().generateCuratedPlaylist(previousPlaylist1, 5, Uri.EMPTY);

    // double check, also covered by FrameCuratorPlaylistParserTest.testCuratorParse
    assertThat(curated.durationUs).isEqualTo(previousPlaylist1.durationUs);
    assertThat(curated.mediaSequence).isEqualTo(1);
    assertThat(curated.hasDiscontinuitySequence).isEqualTo(false);

    // Now, using the curated playlist as the base
    testee = new SmallestIFramesCurator(curated);
    SmallestIFramesCurator.PlaylistUpdates curatedUpdates = testee.computePlaylistUpdates(currentPlaylist1, previousPlaylist1);;

    // Because of the subset each segment is played for a longer duration, so a longer duration must be removed unless
    // the multiple is even
    assertThat(curatedUpdates.timeDeltaUs).isAtLeast(updates.timeDeltaUs);
    assertThat(curatedUpdates.discontinuityDelta).isEqualTo(updates.discontinuityDelta);

  }


  @Test
  public void testCloneAdjustedSegments() {

    // Initial curation of first playlist
    HlsMediaPlaylist curated = new SmallestIFramesCurator().generateCuratedPlaylist(previousPlaylist2, 5, Uri.EMPTY);

    // Compute the updates
    SmallestIFramesCurator testee = new SmallestIFramesCurator(curated);
    SmallestIFramesCurator.PlaylistUpdates updates = testee.computePlaylistUpdates(currentPlaylist2, previousPlaylist2);;
    final int expectedDiscontinuitySequenceNumber = 1;
    assertThat(updates.discontinuityDelta).isEqualTo(expectedDiscontinuitySequenceNumber);
    assertThat(updates.removeCount).isEqualTo(2);

    // and with curated version of source

    // do the adds (just add all the segments, don't curate) and removes then update and clone
    List<HlsMediaPlaylist.Segment> rawUpdatedSegments =
        new ArrayList<>(curated.segments.subList(updates.removeCount, curated.segments.size()));
    rawUpdatedSegments.addAll(updates.addedSegments);


    List<HlsMediaPlaylist.Segment> cloned = testee.cloneAdjustedSegments(updates.discontinuityDelta, rawUpdatedSegments);

    assertThat(cloned.size()).isEqualTo(rawUpdatedSegments.size());
    assertThat(computeDuration(cloned)).isEqualTo(computeDuration(rawUpdatedSegments));
    assertThat(cloned.get(0).relativeStartTimeUs).isEqualTo(0);

    for (int i=0; i < cloned.size(); i++) {
      HlsMediaPlaylist.Segment clone = cloned.get(i);
      HlsMediaPlaylist.Segment raw = rawUpdatedSegments.get(i);
      assertThat(clone.relativeDiscontinuitySequence).isEqualTo(raw.relativeDiscontinuitySequence - expectedDiscontinuitySequenceNumber);
    }
  }

  public static void dumpSegments(List<HlsMediaPlaylist.Segment> segments) {
    int i=1;
    for (HlsMediaPlaylist.Segment segment: segments) {
      System.out.println("segment " + (i++) + " DSN: " + segment.relativeDiscontinuitySequence + " startUs: " + segment.relativeStartTimeUs + " durationUs: " + segment.durationUs + " uri:" + segment.url);
    }
  }

  public static long computeDuration(List<HlsMediaPlaylist.Segment> segments) {
    long totalDuration = 0;
    for (HlsMediaPlaylist.Segment segment : segments) {
      totalDuration += segment.durationUs;
    }
    return totalDuration;
  }
}
