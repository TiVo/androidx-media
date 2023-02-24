package com.tivo.exoplayer.library.metrics;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.trackselection.BaseTrackSelection;
import java.util.List;

/**
 * Use for PlaybackStatsListener test cases so the player mock can
 * return a track selection with a fixed track selected.
 */
class MockTrackSelection extends BaseTrackSelection {

  private int selectedIndex;

  public static MockTrackSelection buildFrom(Format... formats) {
    TrackGroup group = new TrackGroup(formats);
    int[] tracks = new int[formats.length];
    for (int i = 0; i < tracks.length; i++) {
      tracks[i] = i;
    }
    return new MockTrackSelection(group, tracks);
  }

  public MockTrackSelection(TrackGroup group, int... tracks) {
    super(group, tracks);
    selectedIndex = 0;
  }

  public void setSelectedIndex(int index) {
    selectedIndex = index;
  }

  @Override
  public int getSelectedIndex() {
    return selectedIndex;
  }

  @Override
  public int getSelectionReason() {
    return 0;
  }

  @Nullable
  @Override
  public Object getSelectionData() {
    return null;
  }

  @Override
  public void updateSelectedTrack(long playbackPositionUs, long bufferedDurationUs,
      long availableDurationUs, List<? extends MediaChunk> queue,
      MediaChunkIterator[] mediaChunkIterators) {

  }
}
