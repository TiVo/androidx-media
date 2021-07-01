package com.google.android.exoplayer2.trickplay.hls;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistParserFactory;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParserFactory;
import com.google.android.exoplayer2.upstream.ParsingLoadable;

public class FrameCuratorPlaylistParser implements ParsingLoadable.Parser<HlsPlaylist> {
    @Nullable private final ParsingLoadable.Parser<HlsPlaylist> parserDelegate;

    public FrameCuratorPlaylistParser(HlsPlaylistParserFactory hlsPlaylistParserFactory, HlsMasterPlaylist masterPlaylist) {
        parserDelegate = hlsPlaylistParserFactory.createPlaylistParser(masterPlaylist);
    }

    @VisibleForTesting
    FrameCuratorPlaylistParser() {
        parserDelegate = new DefaultHlsPlaylistParserFactory().createPlaylistParser();
    }

    @Override
    public HlsPlaylist parse(Uri uri, InputStream inputStream) throws IOException {
        HlsPlaylist playlist = parserDelegate.parse(uri, inputStream);
        if (playlist instanceof HlsMediaPlaylist && uri.getFragment() != null) {
            int subsetTarget = Integer.parseInt(uri.getFragment());
            HlsMediaPlaylist mediaPlaylist = (HlsMediaPlaylist) playlist;
            List<HlsMediaPlaylist.Segment> updateSegments = curateSmallestIFrames(mediaPlaylist, subsetTarget);


            playlist = mediaPlaylist.copyWithNewSegments(updateSegments);
        }
        return playlist;
    }

    @VisibleForTesting
    List<HlsMediaPlaylist.Segment> curateSmallestIFrames(HlsMediaPlaylist mediaPlaylist, int subset) {
        List<HlsMediaPlaylist.Segment> baseSegments = mediaPlaylist.segments;
        List<HlsMediaPlaylist.Segment> curatedSegments = new ArrayList<>();

        if (baseSegments.size() > 0) {
            curatedSegments.add(baseSegments.get(0));
        }

        int tolerance = (int) Math.floor(subset * .25);

        for (int segNum = subset - tolerance; segNum < baseSegments.size(); segNum += subset) {
            int lastIndex =
                    Math.min(baseSegments.size() - 1, segNum + 2 * tolerance);
            curatedSegments.add(smallestSegmentInRange(baseSegments, segNum, lastIndex));
        }
        List<HlsMediaPlaylist.Segment> clonedSegments = new ArrayList<>();
        for (int idx = 0; idx < curatedSegments.size(); idx++) {
            HlsMediaPlaylist.Segment current = curatedSegments.get(idx);
            long duration;
            if (idx == curatedSegments.size() - 1) {
                duration = mediaPlaylist.durationUs - current.relativeStartTimeUs;
            } else {
                HlsMediaPlaylist.Segment nextSegment = curatedSegments.get(idx + 1);
                duration = nextSegment.relativeStartTimeUs - current.relativeStartTimeUs;
            }
            clonedSegments.add(current.copyWithDuration(duration));
        }

        return clonedSegments;
    }

    @VisibleForTesting
    @NonNull
    HlsMediaPlaylist.Segment smallestSegmentInRange(List<HlsMediaPlaylist.Segment> segments, int startIndex, int endIndex) {
        long minLength = Long.MAX_VALUE;
        HlsMediaPlaylist.Segment minSizeSegment = null;
        for (HlsMediaPlaylist.Segment segment : segments.subList(startIndex, endIndex)) {
            if (segment.byteRangeLength < minLength) {
                minSizeSegment = segment;
                minLength = segment.byteRangeLength;
            }
        }
        return minSizeSegment == null ? segments.get(endIndex) : minSizeSegment;
    }
}
