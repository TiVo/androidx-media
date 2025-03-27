package com.tivo.exoplayer.library.multiview;

import static com.google.android.exoplayer2.C.ROLE_FLAG_TRICK_PLAY;
import static com.google.android.exoplayer2.RendererCapabilities.ADAPTIVE_NOT_SEAMLESS;
import static com.google.android.exoplayer2.RendererCapabilities.ADAPTIVE_SEAMLESS;
import static com.google.android.exoplayer2.RendererCapabilities.TUNNELING_NOT_SUPPORTED;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.MockitoAnnotations.initMocks;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection.Factory;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class MultiViewTrackSelectorTest {

  @Mock
  private Factory mockTrackSelectionFactory;

  private MultiViewTrackSelector multiViewTrackSelector;

  private Format baseTestFormat = new Format.Builder()
      .setSampleMimeType("video/mp4")
      .setFrameRate(60)
      .build();
  private Format[] formatsMeetingOptimal;
  private Format[] formatsExceedingOptimal;
  private Format[] formatsMeetingOptimalIsTrickPlay;
  private Format[] formatsSample4KLadder;

  @Before
  public void setUp() {
    initMocks(this);

    Context context = ApplicationProvider.getApplicationContext();
    multiViewTrackSelector = new MultiViewTrackSelector(context, mockTrackSelectionFactory);

    DefaultTrackSelector.Parameters parameters = DefaultTrackSelector.Parameters.getDefaults(context)
        .buildUpon()
        .setViewportSize(3840, 2160, false) // 4k, despite what mocked Context says
        .build();

    multiViewTrackSelector.setParameters(parameters);
    formatsMeetingOptimal = new Format[] {
        baseTestFormat.buildUpon().setAverageBitrate(2184000).setWidth(720).setHeight(480).setFrameRate(29.97f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(1200000).setWidth(960).setHeight(540).setFrameRate(29.97f).build(),
    };
    formatsMeetingOptimalIsTrickPlay = new Format[] {
        baseTestFormat.buildUpon().setAverageBitrate(1000000).setWidth(720).setHeight(480).setRoleFlags(ROLE_FLAG_TRICK_PLAY).build(),
    };
    formatsExceedingOptimal = new Format[] {
        baseTestFormat.buildUpon().setAverageBitrate(2000000).setWidth(1920).setHeight(1080).setFrameRate(29.97f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(4000000).setWidth(1920).setHeight(1080).setFrameRate(29.97f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(3000000).setWidth(1920).setHeight(1080).setFrameRate(29.97f).build()
    };

    formatsSample4KLadder = new Format[] {
        baseTestFormat.buildUpon().setAverageBitrate(3884000).setWidth(1280).setHeight(720).setFrameRate(59.94f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(1000000).setWidth(1280).setHeight(720).setFrameRate(0.5f).setRoleFlags(ROLE_FLAG_TRICK_PLAY).build(),
        baseTestFormat.buildUpon().setAverageBitrate(2384000).setWidth(1280).setHeight(720).setFrameRate(29.97f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(400000).setWidth(1280).setHeight(720).setFrameRate(0.2f).setRoleFlags(ROLE_FLAG_TRICK_PLAY).build(),
        baseTestFormat.buildUpon().setAverageBitrate(15384000).setWidth(3840).setHeight(2160).setFrameRate(59.94f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(7384000).setWidth(1920).setHeight(1080).setFrameRate(59.94f).build()
    };
  }

  @Test
  public void test_meetsOptimalSize() {

    OptimalVideoSize mockOptimalSize = new OptimalVideoSize(960, 540);

    // No format meets the optimal size
    for (Format format : formatsExceedingOptimal) {
      assertWithMessage("Format " + format + " should not meetsOptimalSize")
          .that(mockOptimalSize.meetsOptimalSize(format)).isFalse();
    }

    // All formats meet the optimal size
    for (Format format : formatsMeetingOptimal) {
      assertWithMessage("Format " + format + " should meetsOptimalSize")
          .that(mockOptimalSize.meetsOptimalSize(format)).isTrue();
    }
  }

  @Test
  public void test_filterAndSortTracks_4kLadder() {
    Format[] formats = new Format[formatsSample4KLadder.length];
    System.arraycopy(formatsSample4KLadder, 0, formats, 0, formatsSample4KLadder.length);

    TrackGroup group = new TrackGroup(formats);
    int tracks[] = new int[formats.length];
    for (int i = 0; i < formats.length; i++) {
      tracks[i] = i;
    }
    ExoTrackSelection.Definition definition = new ExoTrackSelection.Definition(group, tracks, C.TRACK_TYPE_VIDEO);

    // Call as the DefaultTrackSelector main selectTracks method would for video tracks
    List<Integer> indexes = MultiViewTrackSelector.filterAndSortTracks(definition);

    assertThat(indexes).isNotNull();
    assertThat(indexes).hasSize(4);
    assertThat(indexes).containsExactly(2, 0, 5, 4);
  }

  @Test
  public void test_selectVideoTrack_withMeetsOptimal() throws ExoPlaybackException {
    Format[] formats = concatenateArrays(formatsMeetingOptimal, formatsExceedingOptimal, formatsMeetingOptimalIsTrickPlay);

    // This size is the UI size for a double pixel density UHD display screen, divided by 2 for 2 rows of 2 columns.
    OptimalVideoSize mockOptimalSize = new OptimalVideoSize(960, 540);
    multiViewTrackSelector.setOptimalVideoSize(mockOptimalSize);

    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);

    // Call as the DefaultTrackSelector main selectTracks method would for video tracks
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, multiViewTrackSelector.getParameters(), true);

    assertThat(definition).isNotNull();
    assertThat(definition.tracks).hasLength(formatsMeetingOptimal.length);
    for (int i = 0; i < formatsMeetingOptimal.length; i++) {
      assertThat(definition.tracks[i]).isEqualTo(i);
    }
  }

  @Test
  public void test_selectVideoTrack_allExceedOptimal() throws ExoPlaybackException {
    Format[] formats = new Format[formatsExceedingOptimal.length];
    System.arraycopy(formatsExceedingOptimal, 0, formats, 0, formatsExceedingOptimal.length);

    // This size is the UI size for a double pixel density UHD display screen, divided by 2 for 2 rows of 2 columns.
    OptimalVideoSize mockOptimalSize = new OptimalVideoSize(960, 540);
    multiViewTrackSelector.setOptimalVideoSize(mockOptimalSize);

    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);

    // Call as the DefaultTrackSelector main selectTracks method would for video tracks
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, multiViewTrackSelector.getParameters(), true);

    assertThat(definition).isNotNull();

    // No format meets the optimal size, so the two lowest score formats are kept
    assertThat(definition.tracks).hasLength(2);
    assertThat(definition.tracks).isEqualTo(new int[]{0, 2});
  }

  @Test
  public void test_selectVideoTrack_exceedsBandwidth_notIframe() throws ExoPlaybackException {
    Format[] formats = new Format[] {
        baseTestFormat.buildUpon().setAverageBitrate(2184000).setWidth(720).setHeight(480).build(),
        baseTestFormat.buildUpon().setAverageBitrate(3384000).setWidth(1280).setHeight(720).build(),
        baseTestFormat.buildUpon().setAverageBitrate(4884000).setWidth(1280).setHeight(720).build(),
        baseTestFormat.buildUpon().setAverageBitrate(1800000).setWidth(720).setHeight(480).setRoleFlags(ROLE_FLAG_TRICK_PLAY).build(),
        baseTestFormat.buildUpon().setAverageBitrate(900000).setWidth(720).setHeight(480).setRoleFlags(ROLE_FLAG_TRICK_PLAY).build(),
        baseTestFormat.buildUpon().setAverageBitrate(600000).setWidth(720).setHeight(480).setRoleFlags(ROLE_FLAG_TRICK_PLAY).build(),
        baseTestFormat.buildUpon().setAverageBitrate(450000).setWidth(720).setHeight(480).setRoleFlags(ROLE_FLAG_TRICK_PLAY).build(),
        baseTestFormat.buildUpon().setAverageBitrate(360000).setWidth(720).setHeight(480).setRoleFlags(ROLE_FLAG_TRICK_PLAY).build(),
        baseTestFormat.buildUpon().setAverageBitrate(257142).setWidth(720).setHeight(480).setRoleFlags(ROLE_FLAG_TRICK_PLAY).build()
    };

    // This size is the UI size for a double pixel density UHD display screen, divided by 2 for 2 rows of 2 columns.
    OptimalVideoSize mockOptimalSize = new OptimalVideoSize(960, 540);
    multiViewTrackSelector.setOptimalVideoSize(mockOptimalSize);

    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);

    // Call as the DefaultTrackSelector main selectTracks method would for video tracks
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, multiViewTrackSelector.getParameters(), true);

    assertThat(definition).isNotNull();
    assertThat(definition.tracks).hasLength(1);
    assertThat(definition.tracks[0]).isEqualTo(0);
  }

  @Test
  public void test_selectVideoTrack_sample4KLadder() throws ExoPlaybackException {
    Format[] formats = {
        baseTestFormat.buildUpon().setAverageBitrate(2384000).setWidth(1280).setHeight(720).setFrameRate(29.97f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(3884000).setWidth(1280).setHeight(720).setFrameRate(59.94f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(7384000).setWidth(1920).setHeight(1080).setFrameRate(59.94f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(15384000).setWidth(3840).setHeight(2160).setFrameRate(59.94f).build()
    };

    OptimalVideoSize mockOptimalSize = new OptimalVideoSize(960, 540);
    multiViewTrackSelector.setOptimalVideoSize(mockOptimalSize);

    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);

    // Call as the DefaultTrackSelector main selectTracks method would for video tracks
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, multiViewTrackSelector.getParameters(), true);

    assertThat(definition).isNotNull();
    assertThat(definition.tracks).hasLength(2);
    assertThat(definition.tracks).isEqualTo(new int[]{0, 1});
  }

  @Test
  public void test_selectVideoTrack_sample4KLadder2() throws ExoPlaybackException {
    Format[] formats = {
        baseTestFormat.buildUpon().setAverageBitrate(10320000).setWidth(2560).setHeight(1440).setFrameRate(29.97f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(16320000).setWidth(3840).setHeight(2160).setFrameRate(59.94f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(4820000).setWidth(1920).setHeight(1080).setFrameRate(29.97f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(2320000).setWidth(1280).setHeight(720).setFrameRate(29.97f).build()
    };

    OptimalVideoSize mockOptimalSize = new OptimalVideoSize(960, 540);
    multiViewTrackSelector.setOptimalVideoSize(mockOptimalSize);

    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);

    // Call as the DefaultTrackSelector main selectTracks method would for video tracks
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, multiViewTrackSelector.getParameters(), true);

    assertThat(definition).isNotNull();
    assertThat(definition.tracks).hasLength(2);
    assertThat(definition.tracks).isEqualTo(new int[]{3, 2});
  }


  @Test
  public void test_selectVideoTrack_exceedsOptimal_meetsMaxToKeep() throws ExoPlaybackException {
    Format[] formats = {
        baseTestFormat.buildUpon().setAverageBitrate(2384000).setWidth(1280).setHeight(720).setFrameRate(29.97f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(3884000).setWidth(1280).setHeight(720).setFrameRate(59.94f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(7384000).setWidth(1920).setHeight(1080).setFrameRate(59.94f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(15384000).setWidth(3840).setHeight(2160).setFrameRate(59.94f).build()
    };

    OptimalVideoSize mockOptimalSize = new OptimalVideoSize(960, 540);
    multiViewTrackSelector.setOptimalVideoSize(mockOptimalSize);

    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);

    // Call as the DefaultTrackSelector main selectTracks method would for video tracks
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, multiViewTrackSelector.getParameters(), true);

    assertThat(definition).isNotNull();
    assertThat(definition.tracks).hasLength(2);
    assertThat(definition.tracks).isEqualTo(new int[]{0, 1});
  }

  @NonNull
  private static int[][] getRendererSupportsAll(Format[] formats) {
    int[][] formatSupport = new int[1][formats.length];
    for (int i = 0; i < formats.length; i++) {
      formatSupport[0][i] = RendererCapabilities.create(C.FORMAT_HANDLED, ADAPTIVE_SEAMLESS, TUNNELING_NOT_SUPPORTED);
    }
    return formatSupport;
  }

  @SafeVarargs
  private static <T> T[] concatenateArrays(T[]... arrays) {
    int totalLength = 0;
    for (T[] array : arrays) {
      totalLength += array.length;
    }

    T[] result = Arrays.copyOf(arrays[0], totalLength);
    int currentPos = 0;
    for (T[] array : arrays) {
      System.arraycopy(array, 0, result, currentPos, array.length);
      currentPos += array.length;
    }

    return result;
  }
}