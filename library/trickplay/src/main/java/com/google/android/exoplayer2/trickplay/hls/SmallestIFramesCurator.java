package com.google.android.exoplayer2.trickplay.hls;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.util.Log;

public class SmallestIFramesCurator {
  private static final String TAG = "SmallestIFramesCurator";

  private @MonotonicNonNull HlsMediaPlaylist previousCuratedPlaylist;

  @VisibleForTesting
  static class PlaylistUpdates {
    int removeCount = 0;
    int discontinuityDelta = 0;
    long timeDeltaUs = 0;
    List<HlsMediaPlaylist.Segment> addedSegments = new ArrayList<>();

    PlaylistUpdates() {}
  }

  public SmallestIFramesCurator(HlsMediaPlaylist previousPlaylist) {
    previousCuratedPlaylist = previousPlaylist;
  }

  public SmallestIFramesCurator() {
  }


  /**
   * Generates a new curated {@link HlsMediaPlaylist} from the sourcePlaylist.  This is called for the
   * first load request for a curated playlist.
   *
   * @param sourcePlaylist original source for segments to curate
   * @param subset subseting factor
   * @param curatedUri URI for the curated version of the playlist
   * @return The new {@link HlsMediaPlaylist} with subset of segments from source
   */
  HlsMediaPlaylist generateCuratedPlaylist(HlsMediaPlaylist sourcePlaylist, int subset, Uri curatedUri) {
    List<HlsMediaPlaylist.Segment> curated = curateSmallestIFrames(sourcePlaylist, subset);
    int newDiscontinuitySequence = sourcePlaylist.hasDiscontinuitySequence ? sourcePlaylist.discontinuitySequence : C.INDEX_UNSET;
    return sourcePlaylist.copyWithUpdates(curated, curatedUri.toString(), sourcePlaylist.startTimeUs, 1, newDiscontinuitySequence);
  }

  /**
   * Handles an update to the source {@link HlsMediaPlaylist}, latest, returning an updated curated
   * playlist. This is done by integrating the changes between 'latest' and 'previous' and producing a new
   * curated {@link HlsMediaPlaylist} from the current currated playlist.
   *
   * Algorithm simply removes any segments that were removed from the source, then curates and
   * adds any new segments.  Playlist mediaSequenceNumber and discontinuitySequenceNumber are
   * updated to reflect the changes
   *
   * @param latest most recent loaded and parsed source {@link HlsMediaPlaylist}
   * @param previous the {@link HlsMediaPlaylist} previous to latest
   * @param subset subseting factor
   * @return the updated playlist, if changed, otherwise return currentCuratedPlaylist
   */
  @RequiresNonNull("previousCuratedPlaylist")
  HlsMediaPlaylist updateCurrentCurated(HlsMediaPlaylist latest,
                                        HlsMediaPlaylist previous,
                                        int subset) {
    @NonNull HlsMediaPlaylist updatedPlaylist = previousCuratedPlaylist;

    PlaylistUpdates updates = computePlaylistUpdates(latest, previous);
    if (updates.removeCount > 0 || updates.addedSegments.size() > 0) {
      List<HlsMediaPlaylist.Segment> rawUpdatedSegments =
          new ArrayList<>(previousCuratedPlaylist.segments.subList(updates.removeCount, previousCuratedPlaylist.segments.size()));


      List<HlsMediaPlaylist.Segment> addedCurated = curateSmallestIFrames(latest, subset, updates.addedSegments);
      rawUpdatedSegments.addAll(addedCurated);

      // clone and fixup the start times
      List<HlsMediaPlaylist.Segment> updatedSegments = cloneAdjustedSegments(updates.discontinuityDelta, rawUpdatedSegments);

      int updatedDiscontinuitySequence = C.INDEX_UNSET;
      if (previousCuratedPlaylist.hasDiscontinuitySequence || updates.discontinuityDelta > 0) {
        updatedDiscontinuitySequence = previousCuratedPlaylist.discontinuitySequence + updates.discontinuityDelta;
      }

      final long updatedMediaSequence = previousCuratedPlaylist.mediaSequence + updates.removeCount;
      final long updatedStartTimeUs = previousCuratedPlaylist.startTimeUs + updates.timeDeltaUs;
      updatedPlaylist = previousCuratedPlaylist.copyWithUpdates(
          updatedSegments,
          previousCuratedPlaylist.baseUri,
          updatedStartTimeUs,
          updatedMediaSequence,
          updatedDiscontinuitySequence
      );
    }

    return updatedPlaylist;
  }


  /**
   * Scans the {@link HlsMediaPlaylist.Segment}'s in the source {@link HlsMediaPlaylist} tp produce a
   * subset of the segments reduced with the subset factor to use for higherspeed iFrame only playback.
   *
   * @param sourcePlaylist - source playlist to be curated
   * @param subset - subset factor to curate playlist with
   * @return list of cloned {@link HlsMediaPlaylist.Segment}'s reduced from the source.
   */
  @VisibleForTesting
  List<HlsMediaPlaylist.Segment> curateSmallestIFrames(HlsMediaPlaylist sourcePlaylist, int subset) {
    List<HlsMediaPlaylist.Segment> baseSegments = sourcePlaylist.segments;
    return curateSmallestIFrames(sourcePlaylist, subset, baseSegments);
  }

  /**
   * Similar to {@link #curateSmallestIFrames(HlsMediaPlaylist, int)} only this method works just on
   * the baseSegments list which can be a subset of the segments in the playlist.   This is used to
   * for a playlist update that adds segments to the source playlist, to curate just these added segments
   *
   * @param sourcePlaylist - source playlist to be curated
   * @param subset - subset factor to curate playlist with
   * @param baseSegments - segments to curate, all segments in the
   * @return list of cloned {@link HlsMediaPlaylist.Segment}'s reduced from the source.
   */
  private List<HlsMediaPlaylist.Segment> curateSmallestIFrames(HlsMediaPlaylist sourcePlaylist, int subset,
                                                               List<HlsMediaPlaylist.Segment> baseSegments) {
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
        duration = sourcePlaylist.durationUs - current.relativeStartTimeUs;
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

  /**
   * Clones a set of {@link HlsMediaPlaylist.Segment} source from a previously curated playlist and
   * potentially added segments from an update adjusting them to:
   *
   * <ol>
   *   <li>Adjust relativeStartTimeUs to start with 0 and follow durations</li>
   *   <li>Adjust for changes to the playlist discontinuity sequence number change</li>
   * </ol>
   *
   * @param discontinuityDelta - change in the discontinuity sequence in the playlist
   * @param originalSegments - segments to clone and adjust
   * @return adjusted segment list
   */
  @VisibleForTesting
  List<HlsMediaPlaylist.Segment> cloneAdjustedSegments(int discontinuityDelta, List<HlsMediaPlaylist.Segment> originalSegments) {
    List<HlsMediaPlaylist.Segment> updatedSegments = new ArrayList<>(originalSegments.size());
    for (int indx = 0; indx < originalSegments.size(); indx++) {
      HlsMediaPlaylist.Segment src = originalSegments.get(indx);
      if (indx == 0) {
        updatedSegments.add(src.copyWithUpdates(0, discontinuityDelta));
      } else {
        HlsMediaPlaylist.Segment prev = updatedSegments.get(indx - 1);
        updatedSegments.add(src.copyWithUpdates(prev.relativeStartTimeUs + prev.durationUs, discontinuityDelta));
      }
    }
    return updatedSegments;
  }


  /**
   * Compute the updates required to the current {@link #previousCuratedPlaylist} given the
   * updated source {@link HlsMediaPlaylist} and the prior {@link HlsMediaPlaylist}
   *
   * When the source playlist updates any segments removed from the source playlist that are in
   * the curated playlist must be removed from the updated curated.  Basic method is to count the
   * segments in the current playlist that match any of the removed segments, this loop can stop
   * at the first segment that matches none.  Removal affects the new curated playlists
   * mediaSequenceNumber, discontinuitySequenceNumber and relativeDiscontinuitySequence of
   * all the updated segments.
   *
   * The end goal is the curated playlists matches what the origin would have generated.
   *
   * @param latest most recent loaded and parsed source {@link HlsMediaPlaylist}
   * @param previous the {@link HlsMediaPlaylist} previous to latest
   * @return updates required to the playlist, if any
   */
  @RequiresNonNull("previousCuratedPlaylist")
  @VisibleForTesting
  PlaylistUpdates computePlaylistUpdates(HlsMediaPlaylist latest, HlsMediaPlaylist previous) {
    PlaylistUpdates updates = new PlaylistUpdates();
    assert previousCuratedPlaylist != null;

    if (latest.isNewerThan(previous)) {
      if (! latest.isUpdateValid(previous)) {
        Log.w(TAG, "invalid update of " + latest.baseUri + " detected.");
      }
      final int mediaSequenceDelta = (int) (latest.mediaSequence - previous.mediaSequence);
      List<HlsMediaPlaylist.Segment> removed = previous.segments.subList(0, mediaSequenceDelta);

      for (HlsMediaPlaylist.Segment oldest : previousCuratedPlaylist.segments) {
        if (isSegmentInList(oldest, removed)) {
          updates.discontinuityDelta = Math.max(updates.discontinuityDelta, oldest.relativeDiscontinuitySequence);
          updates.removeCount++;
          updates.timeDeltaUs += oldest.durationUs;
        } else {
          break;    // once oldest matches nothing, can stop
        }
      }
      boolean foundLastPrevSegment = false;
      HlsMediaPlaylist.Segment prevLast = previous.segments.get(previous.segments.size() - 1);
      for (HlsMediaPlaylist.Segment addCandidate : latest.segments) {
        if (foundLastPrevSegment) {
          updates.addedSegments.add(addCandidate);
        }
        foundLastPrevSegment = foundLastPrevSegment || areSegmentsEqual(addCandidate, prevLast);
      }
    }

    return updates;
  }


  // Utility methods

  private static boolean areSegmentsEqual(HlsMediaPlaylist.Segment first, HlsMediaPlaylist.Segment segment) {
    return segment.url.equals(first.url)
        && segment.byteRangeLength == first.byteRangeLength
        && segment.byteRangeOffset == first.byteRangeOffset;
  }

  private static boolean isSegmentInList(HlsMediaPlaylist.Segment target, List<HlsMediaPlaylist.Segment> segments) {
    return matchingSegmentFromList(target, segments) != null;
  }

  @VisibleForTesting
  static HlsMediaPlaylist.Segment matchingSegmentFromList(HlsMediaPlaylist.Segment target, List<HlsMediaPlaylist.Segment> segments) {
    for (HlsMediaPlaylist.Segment segment : segments) {
      if (areSegmentsEqual(segment, target)) {
        return segment;
      }
    }
    return null;
  }
}