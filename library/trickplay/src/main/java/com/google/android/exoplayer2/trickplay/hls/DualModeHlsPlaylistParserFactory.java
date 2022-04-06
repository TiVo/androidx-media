package com.google.android.exoplayer2.trickplay.hls;

import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParserFactory;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.util.Util;

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
                ArrayList<HlsMasterPlaylist.Variant> augmentedVariants = new ArrayList<>(masterPlaylist.variants);

                // Find highest bitrate (should be only one really, as Vecima / Velocix only support one) i-Frame only playlist
                HlsMasterPlaylist.Variant highestIframe = null;
                int variantIndx = 0;
                while (variantIndx < masterPlaylist.variants.size()) {
                    HlsMasterPlaylist.Variant sourceVariant = masterPlaylist.variants.get(variantIndx);
                    if ((sourceVariant.format.roleFlags & C.ROLE_FLAG_TRICK_PLAY) != 0) {
                        if (highestIframe == null) {
                            highestIframe = sourceVariant;
                        } else if (sourceVariant.format.bitrate > highestIframe.format.bitrate) {
                            highestIframe = sourceVariant;
                        }

                        // TODO - perhaps these get removed and all replaced with curated?
                        if (sourceVariant.format.label == null) {
                            Format updatedFormat = sourceVariant.format.buildUpon()
                                    .setLabel("iFrame_org")
                                    .build();
                            augmentedVariants.set(variantIndx, cloneVariantWithFormat(sourceVariant, updatedFormat));
                        }
                    }
                    variantIndx++;
                }
                removeAudioOnlyVaraints(augmentedVariants);

                if (highestIframe != null) {
                    Uri clonedVariantUri = highestIframe.url.buildUpon()
                            .fragment(String.valueOf(IFRAME_SUBSET_TARGET))
                            .build();

                    Format clonedFormat = highestIframe.format.buildUpon()
                            .setAverageBitrate(highestIframe.format.bitrate / IFRAME_SUBSET_TARGET)
                            .setPeakBitrate(highestIframe.format.bitrate / IFRAME_SUBSET_TARGET)
                            .setFrameRate(0.1f)
                            .setLabel("iFrame_" + IFRAME_SUBSET_TARGET)
                            .build();

                    augmentedVariants.add(createVariant(clonedVariantUri, clonedFormat));

                    playlist = new HlsMasterPlaylist(
                            masterPlaylist.baseUri,
                            masterPlaylist.tags,
                            augmentedVariants,
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

        /**
         * Remove any audio only varaints that polute the master playlist, unless that is all there
         * is.
         *
         * @param augmentedVariants
         */
        private void removeAudioOnlyVaraints(ArrayList<HlsMasterPlaylist.Variant> augmentedVariants) {
            boolean hasVideo = false;
            for (HlsMasterPlaylist.Variant variant : augmentedVariants) {
                hasVideo |= Util.getCodecCountOfType(variant.format.codecs, C.TRACK_TYPE_VIDEO) != 0;
            }
            if (hasVideo) {
                Iterator<HlsMasterPlaylist.Variant> it = augmentedVariants.iterator();
                while (it.hasNext()) {
                    HlsMasterPlaylist.Variant variant = it.next();
                    if (Util.getCodecCountOfType(variant.format.codecs, C.TRACK_TYPE_VIDEO) == 0) {
                        it.remove();
                    }
                }
            }

        }
    }

    private static HlsMasterPlaylist.Variant createVariant(Uri clonedVariantUri, Format clonedFormat) {
        return new HlsMasterPlaylist.Variant(clonedVariantUri, clonedFormat, null, null, null, null);
    }

    private static HlsMasterPlaylist.Variant cloneVariantWithFormat(HlsMasterPlaylist.Variant variant, Format updatedFormat) {
        return new HlsMasterPlaylist.Variant(
                variant.url,
                updatedFormat,
                variant.videoGroupId,
                variant.audioGroupId,
                variant.subtitleGroupId,
                variant.captionGroupId
        );
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
