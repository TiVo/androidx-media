package com.google.android.exoplayer2.trickplay.hls;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.google.android.exoplayer2.C;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistParserFactory;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.ParsingLoadable;

import static com.google.common.truth.Truth.assertThat;

@RunWith(AndroidJUnit4.class)
public class DualModeHlsPlaylistParserFactoryTest {


    private HlsMasterPlaylist masterPlaylist;
    private DualModeHlsPlaylistParserFactory parserFactory;

    @Before
    public void setupTest() throws IOException {
        InputStream masterPlaylistSource = TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), "testmaster.m3u8");
        parserFactory = new DualModeHlsPlaylistParserFactory(new DefaultHlsPlaylistParserFactory());
        ParsingLoadable.Parser<HlsPlaylist> masterParser = parserFactory.createPlaylistParser();
        HlsPlaylist playlist = masterParser.parse(Uri.EMPTY, masterPlaylistSource);
        assertThat(playlist).isInstanceOf(HlsMasterPlaylist.class);
        masterPlaylist = (HlsMasterPlaylist) playlist;
    }

    @Test
    public void testNoIframePlaylistParsesCorrectly() throws IOException {
        InputStream masterPlaylistSourceNoIframe = TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), "testmasternoIFrame.m3u8");
        parserFactory = new DualModeHlsPlaylistParserFactory(new DefaultHlsPlaylistParserFactory());
        ParsingLoadable.Parser<HlsPlaylist> masterParser = parserFactory.createPlaylistParser();
        HlsPlaylist playlistnoIframe = masterParser.parse(Uri.EMPTY, masterPlaylistSourceNoIframe);
        assertThat(playlistnoIframe).isInstanceOf(HlsMasterPlaylist.class);
        assertThat(iFrameOnlyVariants(((HlsMasterPlaylist) playlistnoIframe).variants).size()).isEqualTo(0);
    }

    @Test
    public void testIFrameClonesCreated() throws IOException {
        assertThat(iFrameOnlyVariants(masterPlaylist.variants).size()).isEqualTo(2);
    }

    @Test
    public void testCreateMediaParser() throws IOException {
        InputStream mediaPlaylistSource = TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), "testplaylist.m3u8");
        ParsingLoadable.Parser<HlsPlaylist> mediaParser = parserFactory.createPlaylistParser(masterPlaylist);
        Uri variantCloneUri = Uri.EMPTY.buildUpon()
                .fragment("1")
                .build();
        HlsPlaylist playlist = mediaParser.parse(variantCloneUri, mediaPlaylistSource);
        assertThat(playlist).isInstanceOf(HlsMediaPlaylist.class);
    }


    private static List<HlsMasterPlaylist.Variant> iFrameOnlyVariants(Iterable<? extends HlsMasterPlaylist.Variant> variants) {
        ArrayList<HlsMasterPlaylist.Variant> matched = new ArrayList<>();
        for (HlsMasterPlaylist.Variant variant : variants) {
            if ((variant.format.roleFlags & C.ROLE_FLAG_TRICK_PLAY) != 0) {
                matched.add(variant);
            }
        }
        return matched;
    }
}
