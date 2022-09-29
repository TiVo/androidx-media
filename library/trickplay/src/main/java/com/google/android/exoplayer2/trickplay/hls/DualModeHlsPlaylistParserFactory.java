package com.google.android.exoplayer2.trickplay.hls;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParserFactory;
import com.google.android.exoplayer2.upstream.ParsingLoadable;

public class DualModeHlsPlaylistParserFactory implements HlsPlaylistParserFactory {
    private final int[] subsetTargets;
    private final HlsPlaylistParserFactory delegatePlaylistParserFactory;

    /**
     * Create a dual-mode {@link HlsPlaylistParserFactory} implementation.
     *
     * The Dual-Mode playlist parser factory creates HlsPlaylistParsers that augment the delegate
     * factory by:
     * <ul>
     *   <li>Adding subset variants, the dual-mode variants, from a source iFrame variant in the master playlist to produce a new
     *   master playlist.</li>
     *   <li>Cleaning up master playlist issues including, removing audio only variants</li>
     *   <li>creates a parser to </li>
     * </ul>
     *
     * @param delegateFactory called to do the actual playlist parsing
     * @param subsets list of segment multiple divider factors to use in creating the dual-mode variants
     */
    public DualModeHlsPlaylistParserFactory(HlsPlaylistParserFactory delegateFactory, int[] subsets) {
        delegatePlaylistParserFactory = delegateFactory;
        subsetTargets = subsets;
    }

    /**
     * Like {@link #DualModeHlsPlaylistParserFactory(HlsPlaylistParserFactory, int[])} only the subsets
     * are the default (2, 3, 4 and 5)
     *
     * @param delegateFactory called to do the actual playlist parsing
     */
    public DualModeHlsPlaylistParserFactory(HlsPlaylistParserFactory delegateFactory) {
        this(delegateFactory, new int[] {2, 3, 4, 5});
    }

    @Override
    public ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser() {
        return new AugmentedPlaylistParser(delegatePlaylistParserFactory.createPlaylistParser(), subsetTargets);
    }

    @Override
    public ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser(
        HlsMasterPlaylist masterPlaylist,
        @Nullable HlsMediaPlaylist previousMediaPlaylist) {
        return new FrameCuratorPlaylistParser(delegatePlaylistParserFactory, masterPlaylist, previousMediaPlaylist);
    }

}
