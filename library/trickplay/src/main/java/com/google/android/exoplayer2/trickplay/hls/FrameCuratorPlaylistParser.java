package com.google.android.exoplayer2.trickplay.hls;

import android.net.Uri;
import android.util.Pair;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistParserFactory;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParserFactory;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.util.Log;

public class FrameCuratorPlaylistParser implements ParsingLoadable.Parser<HlsPlaylist> {
    private static final String TAG = "FrameCuratedPlaylistParser";

    private final ParsingLoadable.Parser<HlsPlaylist> parserDelegate;
    private final Map<Uri, HlsMediaPlaylist> previousPlaylists;
    private final Map<Uri, HlsMediaPlaylist> previousSourcePlaylist;

    public FrameCuratorPlaylistParser(HlsPlaylistParserFactory hlsPlaylistParserFactory, HlsMasterPlaylist masterPlaylist) {
        parserDelegate = hlsPlaylistParserFactory.createPlaylistParser(masterPlaylist);
        previousPlaylists = new HashMap<>();
        previousSourcePlaylist = new HashMap<>();
    }

    @VisibleForTesting
    FrameCuratorPlaylistParser() {
        parserDelegate = new DefaultHlsPlaylistParserFactory().createPlaylistParser();
        previousPlaylists = new HashMap<>();
        previousSourcePlaylist = new HashMap<>();
    }

    @Override
    public HlsPlaylist parse(Uri uri, InputStream inputStream) throws IOException {
        HlsPlaylist playlist = parserDelegate.parse(uri, inputStream);
        if (playlist instanceof HlsMediaPlaylist && uri.getFragment() != null) {
            int subsetTarget = Integer.parseInt(uri.getFragment());
            HlsMediaPlaylist sourcePlaylist = (HlsMediaPlaylist) playlist;

            SmallestIFramesCurator smallestIFramesCurator = new SmallestIFramesCurator(previousPlaylists.get(uri));
            HlsMediaPlaylist previousSource = previousSourcePlaylist.get(uri);
            HlsMediaPlaylist curatedPlaylist;
            if (previousSource == null) {
                curatedPlaylist = smallestIFramesCurator.generateCuratedPlaylist(sourcePlaylist, subsetTarget, uri);
            } else {
                curatedPlaylist = smallestIFramesCurator.updateCurrentCurated(sourcePlaylist, previousSource, subsetTarget);
            }

            previousPlaylists.put(uri, curatedPlaylist);
            previousSourcePlaylist.put(uri, sourcePlaylist);
            playlist = curatedPlaylist;
        }
        return playlist;
    }

    static Pair<List<HlsMediaPlaylist.Segment>, List<HlsMediaPlaylist.Segment>> computeSegmentsDelta(
        HlsMediaPlaylist updated, HlsMediaPlaylist previous) {
        List<HlsMediaPlaylist.Segment> removed = new ArrayList<>();
        List<HlsMediaPlaylist.Segment> added = new ArrayList<>();
        if (updated.isUpdateValid(previous)) {
            int mediaSequenceDelta = (int) (updated.mediaSequence - previous.mediaSequence);
            removed.addAll(previous.segments.subList(0, mediaSequenceDelta));

            int firstAdded = updated.segments.size() - mediaSequenceDelta;
            added.addAll(updated.segments.subList(firstAdded, updated.segments.size()));
        } else {
            Log.w(TAG, "Warning, update is not valid so ignoring");
        }
        return new Pair<>(removed, added);
    }
}