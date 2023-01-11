package com.google.android.exoplayer2.trickplay.hls;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParserFactory;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import java.util.HashMap;
import java.util.Map;

public class DualModeHlsPlaylistParserFactory implements HlsPlaylistParserFactory {
    private final int[] subsetTargets;
    private final HlsPlaylistParserFactory delegatePlaylistParserFactory;
    private final FrameRateAnalyzer frameRateAnalyzer;
    private final Map<Uri, HlsMediaPlaylist> previousSourcePlaylists;

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
        frameRateAnalyzer = new FrameRateAnalyzer();
        previousSourcePlaylists = new HashMap<>();
    }

    /**
     * Like {@link #DualModeHlsPlaylistParserFactory(HlsPlaylistParserFactory, int[])} only the subsets
     * are the default (2, 3, 4, 5, 7)
     *
     * @param delegateFactory called to do the actual playlist parsing
     */
    public DualModeHlsPlaylistParserFactory(HlsPlaylistParserFactory delegateFactory) {
        this(delegateFactory, new int[] {2, 3, 4, 5, 7});
    }

    /**
     * Accessor for the FrameRateAnalyzer is used by the TrickPlayController for adaptive rate
     * shifting trickplay tracks.   This is unfortunately required for two reasons:
     *
     * <ol>
     *   <li>HLS master playlists for iFrame variants do not report frame-rate attribute</li>
     *   <li>The Format object created by the master playlist parse is immuteable, so not possible
     *   to change the frame rate after playlist is loaded</li>
     * </ol>
     *
     * The FrameRateAnalzyer returned by this method lives for the life of this factory.
     *
     * @return FrameRateAnalzyer object
     */
    public FrameRateAnalyzer getFrameRateAnalyzer() {
        return frameRateAnalyzer;
    }

    @NonNull
    @Override
    public ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser() {
        frameRateAnalyzer.resetOnNewMasterPlaylist();
        previousSourcePlaylists.clear();
        return new AugmentedPlaylistParser(delegatePlaylistParserFactory.createPlaylistParser(), subsetTargets);
    }

    @NonNull
    @Override
    public ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser(
        @NonNull HlsMasterPlaylist masterPlaylist,
        @Nullable HlsMediaPlaylist previousMediaPlaylist) {
        return new FrameCuratorPlaylistParser(delegatePlaylistParserFactory, frameRateAnalyzer, masterPlaylist,
            previousSourcePlaylists, previousMediaPlaylist);
    }

}
