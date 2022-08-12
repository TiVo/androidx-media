package com.tivo.exoplayer.library.tracks;

import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectorResult;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class StressBasedTrackSelectorTest {

  private static final Format VIDEO_FORMAT =
          new Format.Builder()
                  .setSampleMimeType(MimeTypes.VIDEO_H264)
                  .setWidth(1024)
                  .setHeight(768)
                  .build();

  private static final RendererCapabilities VIDEO_CAPABILITIES = new FakeRendererCapabilities(C.TRACK_TYPE_VIDEO);

  private static final Timeline TIMELINE = new FakeTimeline(/* windowCount= */ 1);

  private static MediaSource.MediaPeriodId periodId;

  private StressBasedTrackSelector trackSelector;

  @Mock private TrackSelector.InvalidationListener invalidationListener;
  @Mock private BandwidthMeter bandwidthMeter;

  @BeforeClass
  public static void setUpBeforeClass() {
    periodId = new MediaSource.MediaPeriodId(TIMELINE.getUidOfPeriod(/* periodIndex= */ 0));
  }

  @Before
  public void setUp() {
    initMocks(this);
    when(bandwidthMeter.getBitrateEstimate()).thenReturn(1000000L);
    Context context = ApplicationProvider.getApplicationContext();
    trackSelector = new StressBasedTrackSelector(context);
    trackSelector.init(invalidationListener, bandwidthMeter);

    trackSelector.setParameters(
            trackSelector
                    .buildUponParameters()
                    .setViewportSize(3840, 2160, false));
  }

  @Test
  public void stressLevelNone() throws Exception {
    trackSelector.setCurrentStressLevel(StressBasedTrackSelector.PLAYER_STRESS_NONE);

    Format.Builder formatBuilder = VIDEO_FORMAT.buildUpon();
    TrackGroupArray trackGroups = new TrackGroupArray(new TrackGroup(
            formatBuilder.setId("0").setWidth(1920).setHeight(1080).setFrameRate(30).build(),
            formatBuilder.setId("1").setWidth(1920).setHeight(1080).setFrameRate(60).build(),
            formatBuilder.setId("2").setWidth(2160).setHeight(1440).setFrameRate(24).build(),
            formatBuilder.setId("3").setWidth(2160).setHeight(1440).setFrameRate(30).build(),
            formatBuilder.setId("4").setWidth(2160).setHeight(1440).setFrameRate(60).build(),
            formatBuilder.setId("5").setWidth(3840).setHeight(2160).setFrameRate(24).build(),
            formatBuilder.setId("6").setWidth(3840).setHeight(2160).setFrameRate(30).build(),
            formatBuilder.setId("7").setWidth(3840).setHeight(2160).setFrameRate(60).build()));

    TrackSelectorResult result = trackSelector.selectTracks(
            new RendererCapabilities[]{VIDEO_CAPABILITIES}, trackGroups, periodId, TIMELINE);

    assertThat(result.length).isEqualTo(1);
    assertAdaptiveSelection(result.selections.get(0), trackGroups.get(0), 0, 1, 2, 3, 4, 5, 6, 7);
  }

  @Test
  public void stressLevelModerate() throws Exception {
    trackSelector.setCurrentStressLevel(StressBasedTrackSelector.PLAYER_STRESS_MODERATE);

    Format.Builder formatBuilder = VIDEO_FORMAT.buildUpon();
    TrackGroupArray trackGroups = new TrackGroupArray(new TrackGroup(
            formatBuilder.setId("0").setWidth(1920).setHeight(1080).setFrameRate(30).build(),
            formatBuilder.setId("1").setWidth(1920).setHeight(1080).setFrameRate(60).build(),
            formatBuilder.setId("2").setWidth(2160).setHeight(1440).setFrameRate(24).build(),
            formatBuilder.setId("3").setWidth(2160).setHeight(1440).setFrameRate(30).build(),
            formatBuilder.setId("4").setWidth(2160).setHeight(1440).setFrameRate(60).build(),
            formatBuilder.setId("5").setWidth(3840).setHeight(2160).setFrameRate(24).build(),
            formatBuilder.setId("6").setWidth(3840).setHeight(2160).setFrameRate(30).build(),
            formatBuilder.setId("7").setWidth(3840).setHeight(2160).setFrameRate(60).build()));

    TrackSelectorResult result = trackSelector.selectTracks(
            new RendererCapabilities[]{VIDEO_CAPABILITIES}, trackGroups, periodId, TIMELINE);

    assertThat(result.length).isEqualTo(1);
    assertAdaptiveSelection(result.selections.get(0), trackGroups.get(0), 0, 1, 2, 3, 4, 5, 6);
  }

  @Test
  public void stressLevelSevere() throws Exception {
    trackSelector.setCurrentStressLevel(StressBasedTrackSelector.PLAYER_STRESS_SEVERE);

    Format.Builder formatBuilder = VIDEO_FORMAT.buildUpon();
    TrackGroupArray trackGroups = new TrackGroupArray(new TrackGroup(
            formatBuilder.setId("0").setWidth(1920).setHeight(1080).setFrameRate(30).build(),
            formatBuilder.setId("1").setWidth(1920).setHeight(1080).setFrameRate(60).build(),
            formatBuilder.setId("2").setWidth(2160).setHeight(1440).setFrameRate(24).build(),
            formatBuilder.setId("3").setWidth(2160).setHeight(1440).setFrameRate(30).build(),
            formatBuilder.setId("4").setWidth(2160).setHeight(1440).setFrameRate(60).build(),
            formatBuilder.setId("5").setWidth(3840).setHeight(2160).setFrameRate(24).build(),
            formatBuilder.setId("6").setWidth(3840).setHeight(2160).setFrameRate(30).build(),
            formatBuilder.setId("7").setWidth(3840).setHeight(2160).setFrameRate(60).build()));

    TrackSelectorResult result = trackSelector.selectTracks(
            new RendererCapabilities[]{VIDEO_CAPABILITIES}, trackGroups, periodId, TIMELINE);

    assertThat(result.length).isEqualTo(1);
    assertAdaptiveSelection(result.selections.get(0), trackGroups.get(0), 0, 1, 2, 3, 5, 6);
  }

  @Test
  public void stressLevelCritical() throws Exception {
    trackSelector.setCurrentStressLevel(StressBasedTrackSelector.PLAYER_STRESS_CRITICAL);

    Format.Builder formatBuilder = VIDEO_FORMAT.buildUpon();
    TrackGroupArray trackGroups = new TrackGroupArray(new TrackGroup(
            formatBuilder.setId("0").setWidth(1920).setHeight(1080).setFrameRate(30).build(),
            formatBuilder.setId("1").setWidth(1920).setHeight(1080).setFrameRate(60).build(),
            formatBuilder.setId("2").setWidth(2160).setHeight(1440).setFrameRate(24).build(),
            formatBuilder.setId("3").setWidth(2160).setHeight(1440).setFrameRate(30).build(),
            formatBuilder.setId("4").setWidth(2160).setHeight(1440).setFrameRate(60).build(),
            formatBuilder.setId("5").setWidth(3840).setHeight(2160).setFrameRate(24).build(),
            formatBuilder.setId("6").setWidth(3840).setHeight(2160).setFrameRate(30).build(),
            formatBuilder.setId("7").setWidth(3840).setHeight(2160).setFrameRate(60).build()));

    TrackSelectorResult result = trackSelector.selectTracks(
            new RendererCapabilities[]{VIDEO_CAPABILITIES}, trackGroups, periodId, TIMELINE);

    assertThat(result.length).isEqualTo(1);
    assertAdaptiveSelection(result.selections.get(0), trackGroups.get(0), 0, 2, 3, 5, 6);
  }

  @Test
  public void stressLevelCriticalNoLowFrameRate() throws Exception {
    trackSelector.setCurrentStressLevel(StressBasedTrackSelector.PLAYER_STRESS_CRITICAL);

    Format.Builder formatBuilder = VIDEO_FORMAT.buildUpon();
    TrackGroupArray trackGroups = new TrackGroupArray(new TrackGroup(
            formatBuilder.setId("0").setWidth(1920).setHeight(1080).setFrameRate(60).build(),
            formatBuilder.setId("1").setWidth(2160).setHeight(1440).setFrameRate(60).build(),
            formatBuilder.setId("2").setWidth(3840).setHeight(2160).setFrameRate(60).build()));

    TrackSelectorResult result = trackSelector.selectTracks(
            new RendererCapabilities[]{VIDEO_CAPABILITIES}, trackGroups, periodId, TIMELINE);

    assertThat(result.length).isEqualTo(1);
    assertAdaptiveSelection(result.selections.get(0), trackGroups.get(0), 0, 1, 2);
  }

  private static void assertAdaptiveSelection(
          TrackSelection selection, TrackGroup expectedTrackGroup, int... expectedTracks) {
    assertThat(selection).isInstanceOf(AdaptiveTrackSelection.class);
    assertThat(selection.getTrackGroup()).isEqualTo(expectedTrackGroup);
    assertThat(selection.length()).isEqualTo(expectedTracks.length);
    for (int i = 0; i < expectedTracks.length; i++) {
      assertThat(selection.getIndexInTrackGroup(i)).isEqualTo(expectedTracks[i]);
      assertThat(selection.getFormat(i))
              .isSameInstanceAs(expectedTrackGroup.getFormat(selection.getIndexInTrackGroup(i)));
    }
  }

  private static final class FakeRendererCapabilities implements RendererCapabilities {

    private final int trackType;
    @Capabilities private final int supportValue;

    /**
     * Returns {@link FakeRendererCapabilities} that advertises adaptive support for all
     * tracks of the given type.
     *
     * @param trackType the track type of all formats that this renderer capabilities advertises
     * support for.
     */
    FakeRendererCapabilities(int trackType) {
      this(
              trackType,
              RendererCapabilities.create(FORMAT_HANDLED, ADAPTIVE_SEAMLESS, TUNNELING_NOT_SUPPORTED));
    }

    /**
     * Returns {@link FakeRendererCapabilities} that advertises support level using given value for
     * all tracks of the given type.
     *
     * @param trackType the track type of all formats that this renderer capabilities advertises
     *     support for.
     * @param supportValue the {@link Capabilities} that will be returned for formats with the given
     *     type.
     */
    FakeRendererCapabilities(int trackType, @Capabilities int supportValue) {
      this.trackType = trackType;
      this.supportValue = supportValue;
    }

    @Override
    public String getName() {
      return "FakeRenderer(" + Util.getTrackTypeString(trackType) + ")";
    }

    @Override
    public int getTrackType() {
      return trackType;
    }

    @Override
    @Capabilities
    public int supportsFormat(Format format) {
      return MimeTypes.getTrackType(format.sampleMimeType) == trackType
              ? supportValue
              : RendererCapabilities.create(FORMAT_UNSUPPORTED_TYPE);
    }

    @Override
    @AdaptiveSupport
    public int supportsMixedMimeTypeAdaptation() {
      return ADAPTIVE_SEAMLESS;
    }

  }
}
