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
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.util.Util;

/**
 * Parses any top level ("stand-alone playlist") using the delegate then augments any returned
 * {@link }HlsMasterPlaylist}
 *
 * If the playlist is an {@link HlsMasterPlaylist}, this parser augments the playlist by
 * adding additional iFrame only playlists that are subsets of one of the source iFrame only
 * playlists in the source master.
 *
 */
class AugmentedPlaylistParser implements ParsingLoadable.Parser<HlsPlaylist> {
  private final ParsingLoadable.Parser<HlsPlaylist> delegatePlaylistParser;
  private final int[] subsetTargets;

  /**
   * Create the AugmentedPlaylistParser with the indicated delegate.
   *
   * @param playlistParser - delegate playlist parser, performs the actual parsing
   * @param subsets - subsets to create from the source iFrame only playlist
   */
  public AugmentedPlaylistParser(ParsingLoadable.Parser<HlsPlaylist> playlistParser, int[] subsets) {
    delegatePlaylistParser = playlistParser;
    subsetTargets = subsets;
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
                .setFrameRate(1.0f)         // Base is 1.0x frame rate, actual determined on playlist load
                .build();
            augmentedVariants.set(variantIndx, cloneVariantWithFormat(sourceVariant, updatedFormat));
          }
        }
        variantIndx++;
      }
      removeAudioOnlyVaraints(augmentedVariants);

      if (highestIframe != null) {
        for (int subset : subsetTargets) {
          augmentedVariants.add(createVariant(highestIframe, subset, variantIndx++));
        }

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
   * Remove any audio only varaints that polute the master playlist, unless that is all there is.
   *
   * @param augmentedVariants list of {@link HlsMasterPlaylist.Variant} to modify
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

  private static HlsMasterPlaylist.Variant createVariant(HlsMasterPlaylist.Variant highestIframe, int subset, int id) {
    Uri clonedVariantUri = highestIframe.url.buildUpon()
        .fragment(String.valueOf(subset))
        .build();

    // Set the clone up so we can use it for track selection.  Bandwidth values are adjusted using the
    // subset factor (this assumes the source playlist is correct.
    // NOTE the frame rate value is used set assuming the source frame rate is 1 FPS, master playlists never
    // specify this for iFrame only, but we measure and adjust later.
    //
    Format clonedFormat = highestIframe.format.buildUpon()
        .setAverageBitrate(highestIframe.format.bitrate / subset)
        .setPeakBitrate(highestIframe.format.bitrate / subset)
        .setFrameRate(1.0f / subset)
        .setLabel("iFrame_" + subset)
        .setId(id)
        .build();

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
}
