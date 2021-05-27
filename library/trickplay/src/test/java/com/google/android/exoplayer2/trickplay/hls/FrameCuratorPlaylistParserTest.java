package com.google.android.exoplayer2.trickplay.hls;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistParserFactory;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

@RunWith(AndroidJUnit4.class)
public class FrameCuratorPlaylistParserTest {

    private HlsMediaPlaylist mediaPlaylist;

    @Before
    public void setupTest() throws IOException {
        InputStream testPlaylistStream = TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), "testplaylist.m3u8");
        ParsingLoadable.Parser<HlsPlaylist> playlistParser = new DefaultHlsPlaylistParserFactory().createPlaylistParser();
        mediaPlaylist = (HlsMediaPlaylist) playlistParser.parse(Uri.EMPTY, testPlaylistStream);
    }

    @Test
    public void testMinSizeSegment() {
        FrameCuratorPlaylistParser testee = new FrameCuratorPlaylistParser();
        HlsMediaPlaylist.Segment result = testee.smallestSegmentInRange(mediaPlaylist.segments, 3, 9);
        // #EXT-X-BYTERANGE:97760@623408
        assertThat(result.byteRangeLength).isEqualTo(97760);
        assertThat(result.byteRangeOffset).isEqualTo(623408);
    }


    @Test
    public void testIncludesLastSegment() {
        FrameCuratorPlaylistParser testee = new FrameCuratorPlaylistParser();
        int lastIndex = mediaPlaylist.segments.size() - 1;
        HlsMediaPlaylist.Segment last = mediaPlaylist.segments.get(lastIndex);
        HlsMediaPlaylist.Segment result = testee.smallestSegmentInRange(mediaPlaylist.segments, lastIndex, lastIndex);
        assertThat(result).isSameInstanceAs(last);
    }

    @Test
    public void testCuratorParse() throws IOException {
        InputStream testPlaylistStream = TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), "testplaylist.m3u8");
        int subset = 10;
        FrameCuratorPlaylistParser testee = new FrameCuratorPlaylistParser();
        Uri testUrl = Uri.EMPTY.buildUpon()
                .encodedFragment(String.valueOf(subset))
                .build();
        HlsPlaylist playlist = testee.parse(testUrl, testPlaylistStream);
        assertThat(playlist).isInstanceOf(HlsMediaPlaylist.class);

        HlsMediaPlaylist curated = (HlsMediaPlaylist) playlist;

        // Equal, in every way but the segment count
        assertThat(curated.durationUs).isEqualTo(mediaPlaylist.durationUs);
        assertThat(curated.targetDurationUs).isEqualTo(mediaPlaylist.targetDurationUs);
        assertThat(curated.hasEndTag).isEqualTo(mediaPlaylist.hasEndTag);
        assertThat(curated.hasDiscontinuitySequence).isEqualTo(mediaPlaylist.hasDiscontinuitySequence);     // TODO curate these properly?

        int expectedSize = (mediaPlaylist.segments.size() + subset) / subset;
        assertWithMessage("curated segment count is near divisor of subset")
                .that(curated.segments.size()).isAtMost(expectedSize);
    }

    @Test
    public void testCurateSmallest() {
        FrameCuratorPlaylistParser testee = new FrameCuratorPlaylistParser();
        List<HlsMediaPlaylist.Segment> result = testee.curateSmallestIFrames(mediaPlaylist, 10);
        int expected = (int) (Math.ceil(mediaPlaylist.segments.size() / 10) + 1);
        assertThat(result.size()).isEqualTo(expected);

        long totalDuration = 0;
        for (HlsMediaPlaylist.Segment segment : result) {
            totalDuration += segment.durationUs;
//            System.out.println("duration\t"+segment.durationUs);
        }
        assertThat(totalDuration).isEqualTo(mediaPlaylist.durationUs);

    }


}
