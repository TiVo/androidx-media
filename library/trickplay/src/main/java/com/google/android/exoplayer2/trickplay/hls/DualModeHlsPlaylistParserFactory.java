package com.google.android.exoplayer2.trickplay.hls;

import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParserFactory;
import com.google.android.exoplayer2.upstream.ParsingLoadable;

public class DualModeHlsPlaylistParserFactory implements HlsPlaylistParserFactory {
    static final int IFRAME_SUBSET_TARGET = 7;      // TODO this may become a list of values

    private final HlsPlaylistParserFactory hlsPlaylistParserFactory;


    private class AugmentedPlaylistParser implements ParsingLoadable.Parser<HlsPlaylist> {
        private final ParsingLoadable.Parser<HlsPlaylist> delegatePlaylistParser;

        public AugmentedPlaylistParser(ParsingLoadable.Parser<HlsPlaylist> playlistParser) {
            delegatePlaylistParser = playlistParser;
        }

        @Override
        public HlsPlaylist parse(Uri uri, InputStream inputStream) throws IOException {
            HlsPlaylist playlist = delegatePlaylistParser.parse(uri, inputStream);
            if (playlist instanceof HlsMasterPlaylist) {
                HlsMasterPlaylist masterPlaylist = (HlsMasterPlaylist) playlist;
                ArrayList<HlsMasterPlaylist.Variant> iFrameVariants = new ArrayList<>(masterPlaylist.iFrameVariants);
                if (!iFrameVariants.isEmpty()) {
                    HlsMasterPlaylist.Variant variant = iFrameVariants.get(0);

                    Uri clonedVariant = variant.url.buildUpon()
                            .fragment(String.valueOf(IFRAME_SUBSET_TARGET))
                            .build();

                    Format clonedFormat = variant.format
                            .copyWithBitrate(variant.format.bitrate / IFRAME_SUBSET_TARGET)
                            .copyWithFrameRate(0.1f)
                            .copyWithLabel("iFrame_" + IFRAME_SUBSET_TARGET);

                    iFrameVariants.add(new HlsMasterPlaylist.Variant(clonedVariant, clonedFormat, null));

                    playlist = new HlsMasterPlaylist(
                            masterPlaylist.baseUri,
                            masterPlaylist.tags,
                            masterPlaylist.variants,
                            iFrameVariants,
                            masterPlaylist.videos,
                            masterPlaylist.audios,
                            masterPlaylist.subtitles,
                            masterPlaylist.closedCaptions,
                            masterPlaylist.muxedAudioFormat,
                            masterPlaylist.muxedCaptionFormats,
                            masterPlaylist.hasIndependentSegments,
                            masterPlaylist.variableDefinitions,
                            masterPlaylist.sessionKeyDrmInitData);
                }
            }
            return playlist;
        }
    }

    public DualModeHlsPlaylistParserFactory(HlsPlaylistParserFactory hlsPlaylistParserFactory) {
        this.hlsPlaylistParserFactory = hlsPlaylistParserFactory;
    }

    @Override
    public ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser() {
        return new AugmentedPlaylistParser(hlsPlaylistParserFactory.createPlaylistParser());
    }

    @Override
    public ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser(HlsMasterPlaylist masterPlaylist) {
        return new FrameCuratorPlaylistParser(hlsPlaylistParserFactory, masterPlaylist);
    }

}
