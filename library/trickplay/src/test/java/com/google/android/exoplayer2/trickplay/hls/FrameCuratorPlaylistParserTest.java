package com.google.android.exoplayer2.trickplay.hls;

import android.net.Uri;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistParserFactory;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParserFactory;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.common.base.Charsets;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

@RunWith(AndroidJUnit4.class)
public class FrameCuratorPlaylistParserTest {

    private HlsMediaPlaylist mediaPlaylist;

    private static class SegmentKey {
        final String segmentKey;

        SegmentKey(HlsMediaPlaylist.Segment segment) {
            String value = segment.url;
            if (segment.byteRangeLength != C.LENGTH_UNSET) {
                value = value + "@" + segment.byteRangeOffset + "/" + segment.byteRangeLength;
            }
            segmentKey = value;
        }

        @Override
        public int hashCode() {
            return segmentKey.hashCode();
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            return obj instanceof SegmentKey && segmentKey.equals(((SegmentKey) obj).segmentKey);
        }
    }

    private static class FakePlaylistTracker {

        private final HlsPlaylistParserFactory parserFactory;
        private final HlsMasterPlaylist master;
        private Uri dualModeVariantUri;
        @Nullable private HlsMediaPlaylist dualModePlaylistSnapshot;
        @Nullable private HlsMediaPlaylist curatedSourcePlaylist;

        FakePlaylistTracker(String masterPlaylist) throws IOException {
            final DefaultHlsPlaylistParserFactory defaultHlsPlaylistParserFactory = new DefaultHlsPlaylistParserFactory();
            parserFactory = new DualModeHlsPlaylistParserFactory(defaultHlsPlaylistParserFactory);
            ParsingLoadable.Parser<HlsPlaylist> masterParser = parserFactory.createPlaylistParser();
            ByteArrayInputStream inputStream =
                new ByteArrayInputStream(masterPlaylist.getBytes(Charsets.UTF_8));
            master = (HlsMasterPlaylist) masterParser.parse(Uri.EMPTY, inputStream);
            for (HlsMasterPlaylist.Variant variant : master.variants) {
                if (variant.url.getFragment() != null) {
                    dualModeVariantUri = variant.url;
                }
            }
        }

        void clearSnapshots() {
            dualModePlaylistSnapshot = null;
        }

        HlsMediaPlaylist parsePlaylistUpdate(String testPlaylistFile) throws IOException {
            InputStream testPlaylistStream = TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), testPlaylistFile);
            ParsingLoadable.Parser<HlsPlaylist> playlistParser = parserFactory.createPlaylistParser(master, dualModePlaylistSnapshot);
            assertThat(playlistParser).isInstanceOf(FrameCuratorPlaylistParser.class);
            FrameCuratorPlaylistParser curatorPlaylistParser = (FrameCuratorPlaylistParser) playlistParser;
            dualModePlaylistSnapshot = (HlsMediaPlaylist) playlistParser.parse(dualModeVariantUri, testPlaylistStream);
            curatedSourcePlaylist = curatorPlaylistParser.getSourceMediaPlaylist(dualModeVariantUri);

            return dualModePlaylistSnapshot;
        }

        HlsMediaPlaylist getCuratedSourcePlaylist() {
            return curatedSourcePlaylist;
        }
    }


    @Before
    public void setupTest() throws IOException {
        InputStream testPlaylistStream = TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), "testplaylist.m3u8");
        ParsingLoadable.Parser<HlsPlaylist> playlistParser = new DefaultHlsPlaylistParserFactory().createPlaylistParser();
        mediaPlaylist = (HlsMediaPlaylist) playlistParser.parse(Uri.EMPTY, testPlaylistStream);
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
    public void testCuratedPlaylistUpdate() throws IOException {
        String testMaster = "#EXTM3U\n" +
            "#EXT-X-VERSION:5\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=2160000,CODECS=\"mp4a.40.2,avc1.64001f\",RESOLUTION=720x480\n" +
            "dummy.m3u8\n" +
            "#EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=1373400,URI=\"test_playlist.m3u8\",CODECS=\"avc1.640020\",RESOLUTION=1280x720\n";

        FakePlaylistTracker playlistTracker = new FakePlaylistTracker(testMaster);

        String previousSources[] = {
            "testplaylist_update_previous.m3u8",
            "testplaylist_update_previous_1.m3u8",
            "testplaylist_update_previous_2.m3u8"
        };
        String currentSources[] = {
            "testplaylist_update_current.m3u8",
            "testplaylist_update_current_1.m3u8",
            "testplaylist_update_current_2.m3u8"
        };

        for (int i=0; i< previousSources.length; i++) {
            String previousSource = previousSources[i];
            String currentSource = currentSources[i];

            playlistTracker.clearSnapshots();

            // Parse and validate previous as an update, basically this is the initial playlist load, there is no previous
            // snapshot
            //
            HlsMediaPlaylist previous = playlistTracker.parsePlaylistUpdate(previousSource);
            assertThat(playlistTracker.getCuratedSourcePlaylist()).isNotNull();
            validatePlaylistCuration(playlistTracker.getCuratedSourcePlaylist(), previous);

            // load and parse update.
            HlsMediaPlaylist previousSourcePlaylist = playlistTracker.getCuratedSourcePlaylist();
            assertThat(previousSourcePlaylist).isNotNull();
            HlsMediaPlaylist current = playlistTracker.parsePlaylistUpdate(currentSource);
            assertThat(playlistTracker.getCuratedSourcePlaylist()).isNotNull();
            assertThat(current.isUpdateValid(previous)).isTrue();
            validatePlaylistCuration(previousSourcePlaylist, current);
        }

    }

    private void validatePlaylistCuration(HlsMediaPlaylist sourcePlaylist, HlsMediaPlaylist playlist) {
        Map<SegmentKey, Long> timesBySegment = new HashMap<>();
        for (HlsMediaPlaylist.Segment segment : sourcePlaylist.segments) {
            timesBySegment.put(new SegmentKey(segment), segment.relativeStartTimeUs + sourcePlaylist.startTimeUs);
        }

        for (HlsMediaPlaylist.Segment segment : playlist.segments) {
            Long sourceStartTimeUs = timesBySegment.get(new SegmentKey(segment));
            if (sourceStartTimeUs != null) {
                assertThat(segment.relativeStartTimeUs + playlist.startTimeUs).isEqualTo(sourceStartTimeUs);
            }
        }

    }


}
