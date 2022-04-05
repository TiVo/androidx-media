package com.google.android.exoplayer2.trickplay.hls;

import android.net.Uri;
import android.util.Pair;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

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
    public void testComputeSegmentsDelta() throws IOException {
        HlsPlaylistParserFactory parserFactory = new DefaultHlsPlaylistParserFactory();
        ParsingLoadable.Parser<HlsPlaylist> playlistParser = parserFactory.createPlaylistParser();

        InputStream testPlaylistStream = TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), "testplaylist_update_previous_1.m3u8");
        HlsMediaPlaylist previous = (HlsMediaPlaylist) playlistParser.parse(Uri.EMPTY, testPlaylistStream);

        testPlaylistStream = TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), "testplaylist_update_current_1.m3u8");
        HlsMediaPlaylist updated = (HlsMediaPlaylist) playlistParser.parse(Uri.EMPTY, testPlaylistStream);

        Pair<List<HlsMediaPlaylist.Segment>, List<HlsMediaPlaylist.Segment>> pair
            = FrameCuratorPlaylistParser.computeSegmentsDelta(updated, previous);

        assertThat(pair.first.size()).isEqualTo(updated.mediaSequence - previous.mediaSequence);

    }

    @Test
    public void testCuratedPlaylistUpdate() throws IOException {
        String testMaster = "#EXTM3U\n" +
            "#EXT-X-VERSION:5\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=2160000,CODECS=\"mp4a.40.2,avc1.64001f\",RESOLUTION=720x480\n" +
            "dummy.m3u8\n" +
            "#EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=1373400,URI=\"test_playlist.m3u8\",CODECS=\"avc1.640020\",RESOLUTION=1280x720\n";


        // Dual Mode parser will parse the master above and create a curated iFrame playlist from test_playlist
        // State for updates is initialized and saved in the FrameCuratorPlaylistParser created each time a master playlist is
        // parsed (every channel change)
        HlsPlaylistParserFactory parserFactory = new DualModeHlsPlaylistParserFactory(new DefaultHlsPlaylistParserFactory());
        ParsingLoadable.Parser<HlsPlaylist> masterParser = parserFactory.createPlaylistParser();
        ByteArrayInputStream inputStream =
            new ByteArrayInputStream(testMaster.getBytes(Charsets.UTF_8));
        HlsMasterPlaylist master = (HlsMasterPlaylist) masterParser.parse(Uri.EMPTY, inputStream);
        Uri dualModeVariantUri = null;
        for (HlsMasterPlaylist.Variant variant : master.variants) {
            if (variant.url.getFragment() != null) {
                dualModeVariantUri = variant.url;
            }
        }

        // The DefaultHlsPlaylistTracker creates the playlist parser once on load/parse complete for the master
        // playlist, so this is used to save playlist state across loads.
        //
        ParsingLoadable.Parser<HlsPlaylist> playlistParser = parserFactory.createPlaylistParser(master);

        // Load and parse first playlist.
        InputStream testPlaylistStream = TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), "testplaylist_update_previous.m3u8");
        HlsPlaylist playlist = playlistParser.parse(dualModeVariantUri, testPlaylistStream);
        assertThat(playlist).isInstanceOf(HlsMediaPlaylist.class);

        // load and parse update.
        testPlaylistStream = TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), "testplaylist_update_current.m3u8");
        HlsPlaylist playlistUpdate = playlistParser.parse(dualModeVariantUri, testPlaylistStream);
        assertThat(playlistUpdate).isInstanceOf(HlsMediaPlaylist.class);

        assertThat(((HlsMediaPlaylist) playlistUpdate).isUpdateValid((HlsMediaPlaylist) playlist)).isTrue();
    }

}
