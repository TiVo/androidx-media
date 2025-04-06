package com.tivo.exoplayer.library.multiview;

import static com.google.android.exoplayer2.C.ROLE_FLAG_TRICK_PLAY;
import static com.google.android.exoplayer2.RendererCapabilities.ADAPTIVE_NOT_SEAMLESS;
import static com.google.android.exoplayer2.RendererCapabilities.ADAPTIVE_SEAMLESS;
import static com.google.android.exoplayer2.RendererCapabilities.TUNNELING_NOT_SUPPORTED;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

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
import com.google.android.exoplayer2.trackselection.ExoTrackSelection.Definition;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import com.tivo.exoplayer.library.multiview.MultiViewTrackSelector.TrackCategory;
import org.junit.After;

/**
 * Tests for {@link MultiViewTrackSelector} focusing on policy validation.
 */
@RunWith(AndroidJUnit4.class)
public class MultiViewTrackSelectorTest {

  @Mock
  private Factory mockTrackSelectionFactory;

  private MultiViewTrackSelector multiViewTrackSelector;
  private AutoCloseable mockitoCloseable;

  private final Format baseTestFormat = new Format.Builder()
      .setSampleMimeType("video/mp4")
      .setFrameRate(60)
      .build();
  private Format[] formatsMeetingOptimal;
  private Format[] formatsExceedingOptimal;
  private Format[] formatsMeetingOptimalIsTrickPlay;

  @Before
  public void setUp() {
    // Store the AutoCloseable returned by openMocks
    mockitoCloseable = openMocks(this); // Initialize Mockito annotations

    // Configure the mock to create a Definition when called through selectVideoTrack
    // instead of trying to mock the specific method signatures
    when(mockTrackSelectionFactory.createTrackSelections(any(Definition[].class), any(), any(), any()))
        .thenAnswer(invocation -> {
          Definition[] definitions = invocation.getArgument(0);
          ExoTrackSelection[] selections = new ExoTrackSelection[definitions.length];
          for (int i = 0; i < definitions.length; i++) {
            if (definitions[i] != null) {
              // Create a mocked ExoTrackSelection
              ExoTrackSelection selection = mock(ExoTrackSelection.class);
              selections[i] = selection;
            }
          }
          return selections;
        });

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
        baseTestFormat.buildUpon().setAverageBitrate(4000000).setWidth(1920).setHeight(1080).setFrameRate(29.97f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(4500000).setWidth(1920).setHeight(1080).setFrameRate(29.97f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(3000000).setWidth(1920).setHeight(1080).setFrameRate(29.97f).build()
    };
  }

  @After
  public void tearDown() throws Exception {
    // Close the mockito resources
    if (mockitoCloseable != null) {
      mockitoCloseable.close();
    }
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
  public void test_selectVideoTrack_fallbackToLowestResourceTrack() throws ExoPlaybackException {
    // This test verifies that when no tracks meet the optimal size,
    // the selector falls back to the lowest-resource track
    
    Format[] formats = concatenateArrays(formatsExceedingOptimal);

    OptimalVideoSize mockOptimalSize = new OptimalVideoSize(960, 540);
    multiViewTrackSelector.setOptimalVideoSize(mockOptimalSize);

    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);

    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, multiViewTrackSelector.getParameters(), true);

    assertThat(definition).isNotNull();

    // With minTracksToKeep = 1 and no tracks meeting optimal size,
    // only the lowest-resource track should be kept
    assertThat(definition.tracks).hasLength(1);
    assertThat(definition.tracks).isEqualTo(new int[]{2});
    
    // Verify this is indeed the lowest resource track
    Format selectedFormat = formats[definition.tracks[0]];
    for (int i = 0; i < formats.length; i++) {
      if (i == definition.tracks[0]) continue;
      float selectedLoad = getDecoderLoad(selectedFormat);
      float currentLoad = getDecoderLoad(formats[i]);
      // Using isAtMost instead of isLessThan to handle cases where values might be equal
      // This resolves the floating point equality issue
      assertThat(selectedLoad).isAtMost(currentLoad);
    }
  }

  // Helper method to calculate decoder load for test verification
  private float getDecoderLoad(Format format) {
    float frameRate = format.frameRate == Format.NO_VALUE ? 30.0f : format.frameRate;
    return format.width * format.height * frameRate;
  }

  @Test
  public void test_selectVideoTrack_preferExactMatchOverHigherResolution() throws ExoPlaybackException {
    // This test verifies that when both exact matches and higher resolution tracks are available,
    // the exact matches are preferred
    
    // Create a mix of formats: exact matches and higher resolution formats
    Format[] formats = concatenateArrays(formatsMeetingOptimal, formatsExceedingOptimal);
    
    // Set optimal size of 960x540
    OptimalVideoSize mockOptimalSize = new OptimalVideoSize(960, 540);
    multiViewTrackSelector.setOptimalVideoSize(mockOptimalSize);
    
    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);
    
    // Call selectVideoTrack
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, 
                                               multiViewTrackSelector.getParameters(), true);
    
    assertThat(definition).isNotNull();
    
    // Should select the exact matches rather than the higher resolution tracks
    assertThat(definition.tracks).hasLength(formatsMeetingOptimal.length);
    
    // Verify the selected tracks are the exact matches
    for (int i = 0; i < formatsMeetingOptimal.length; i++) {
      assertThat(definition.tracks[i]).isEqualTo(i);
    }
  }

  @Test
  public void test_selectVideoTrack_mixedFrameRatesAndResolutions() throws ExoPlaybackException {
    // This test verifies the combined behavior of resolution selection and frame rate limits
    
    // Create a mix of formats with different resolutions and frame rates
    Format[] formats = new Format[] {
        // Format meeting optimal size and limits
        baseTestFormat.buildUpon().setAverageBitrate(2000000).setWidth(960).setHeight(540).setFrameRate(29.97f).build(),
        
        // Format slightly higher than optimal with standard frame rate (should be selected after exact matches)
        baseTestFormat.buildUpon().setAverageBitrate(3000000).setWidth(1280).setHeight(720).setFrameRate(29.97f).build(),
        
        // Format meeting optimal size but with high frame rate (should be filtered by frame rate limit)
        baseTestFormat.buildUpon().setAverageBitrate(2500000).setWidth(960).setHeight(540).setFrameRate(60.0f).build(),
        
        // Format slightly higher than optimal with high frame rate (should be filtered by frame rate limit)
        baseTestFormat.buildUpon().setAverageBitrate(3500000).setWidth(1280).setHeight(720).setFrameRate(60.0f).build(),
        
        // Format much higher than optimal with standard frame rate
        baseTestFormat.buildUpon().setAverageBitrate(4000000).setWidth(1920).setHeight(1080).setFrameRate(29.97f).build(),
    };
    
    // Set optimal size of 960x540
    OptimalVideoSize mockOptimalSize = new OptimalVideoSize(960, 540);
    multiViewTrackSelector.setOptimalVideoSize(mockOptimalSize);
    
    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);
    
    // Call selectVideoTrack
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, 
                                               multiViewTrackSelector.getParameters(), true);
    
    assertThat(definition).isNotNull();
    
    // Should select exactly one track: the one meeting optimal size and hard limits
    assertThat(definition.tracks).hasLength(1);
    assertThat(definition.tracks[0]).isEqualTo(0);
  }

  @Test
  public void test_selectVideoTrack_onlyHighFrameRateAvailable() throws ExoPlaybackException {
    // This test verifies behavior when only high frame rate tracks are available
    
    // Create formats where all have 60fps
    Format[] formats = new Format[] {
        // Format meeting optimal size but with high frame rate
        baseTestFormat.buildUpon().setAverageBitrate(2000000).setWidth(960).setHeight(540).setFrameRate(60.0f).build(),
        
        // Format slightly higher than optimal with high frame rate
        baseTestFormat.buildUpon().setAverageBitrate(3000000).setWidth(1280).setHeight(720).setFrameRate(60.0f).build(),
        
        // Format much higher than optimal with high frame rate
        baseTestFormat.buildUpon().setAverageBitrate(4000000).setWidth(1920).setHeight(1080).setFrameRate(60.0f).build(),
    };
    
    // Set optimal size of 960x540
    OptimalVideoSize mockOptimalSize = new OptimalVideoSize(960, 540);
    multiViewTrackSelector.setOptimalVideoSize(mockOptimalSize);
    
    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);
    
    // Call selectVideoTrack
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, 
                                               multiViewTrackSelector.getParameters(), true);
    
    assertThat(definition).isNotNull();
    
    // Should select the track meeting optimal size, even though it exceeds frame rate limit
    assertThat(definition.tracks).hasLength(1);
    assertThat(definition.tracks[0]).isEqualTo(0);
  }

  @Test
  public void test_selectVideoTrack_withMeetsOptimal() throws ExoPlaybackException {
    Format[] formats = concatenateArrays(formatsMeetingOptimal, formatsExceedingOptimal, formatsMeetingOptimalIsTrickPlay);

    // This size is the UI size for a double pixel density UHD display screen, divided by 2 for 2 rows of 2 columns.
    OptimalVideoSize maxVideoSize = new OptimalVideoSize(960, 540);
    multiViewTrackSelector.setOptimalVideoSize(maxVideoSize);

    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);

    // Call as the DefaultTrackSelector main selectTracks method would for video tracks
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, multiViewTrackSelector.getParameters(), true);

    assertThat(definition).isNotNull();
    // We should select both formatsMeetingOptimal entries that also meet our hard limits
    assertThat(definition.tracks).hasLength(formatsMeetingOptimal.length);
    for (int i = 0; i < formatsMeetingOptimal.length; i++) {
      assertThat(definition.tracks[i]).isEqualTo(i);
    }
  }

  @Test
  public void test_selectVideoTrack_allExceedOptimal() throws ExoPlaybackException {
    Format[] formats = new Format[formatsExceedingOptimal.length];
    System.arraycopy(formatsExceedingOptimal, 0, formats, 0, formatsExceedingOptimal.length);

    OptimalVideoSize maxVideoSize = new OptimalVideoSize(960, 540);
    multiViewTrackSelector.setOptimalVideoSize(maxVideoSize);

    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);

    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, multiViewTrackSelector.getParameters(), true);

    assertThat(definition).isNotNull();

    // With minTracksToKeep = 1, only the lowest-resource track should be kept
    // This should be the last track (index 2)
    assertThat(definition.tracks).hasLength(1);
    assertThat(definition.tracks).isEqualTo(new int[]{2});
  }

  @Test
  public void test_selectVideoTrack_exceedsBandwidth_notIframe() throws ExoPlaybackException {
    // Create formats where some meet our 30fps and 5Mbps limits
    Format[] formats = new Format[] {
        baseTestFormat.buildUpon().setAverageBitrate(2184000).setWidth(720).setHeight(480).setFrameRate(29.97f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(3384000).setWidth(1280).setHeight(720).setFrameRate(29.97f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(4884000).setWidth(1280).setHeight(720).setFrameRate(29.97f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(1800000).setWidth(720).setHeight(480).setFrameRate(29.97f).setRoleFlags(ROLE_FLAG_TRICK_PLAY).build(),
        baseTestFormat.buildUpon().setAverageBitrate(900000).setWidth(720).setHeight(480).setFrameRate(29.97f).setRoleFlags(ROLE_FLAG_TRICK_PLAY).build(),
        baseTestFormat.buildUpon().setAverageBitrate(600000).setWidth(720).setHeight(480).setFrameRate(29.97f).setRoleFlags(ROLE_FLAG_TRICK_PLAY).build(),
        baseTestFormat.buildUpon().setAverageBitrate(450000).setWidth(720).setHeight(480).setFrameRate(29.97f).setRoleFlags(ROLE_FLAG_TRICK_PLAY).build(),
        baseTestFormat.buildUpon().setAverageBitrate(360000).setWidth(720).setHeight(480).setFrameRate(29.97f).setRoleFlags(ROLE_FLAG_TRICK_PLAY).build(),
        baseTestFormat.buildUpon().setAverageBitrate(257142).setWidth(720).setHeight(480).setFrameRate(29.97f).setRoleFlags(ROLE_FLAG_TRICK_PLAY).build()
    };

    // This size is the UI size for a double pixel density UHD display screen, divided by 2 for 2 rows of 2 columns.
    OptimalVideoSize maxVideoSize = new OptimalVideoSize(960, 540);
    multiViewTrackSelector.setOptimalVideoSize(maxVideoSize);

    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);

    // Call as the DefaultTrackSelector main selectTracks method would for video tracks
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, multiViewTrackSelector.getParameters(), true);

    assertThat(definition).isNotNull();
    // Should select the only non-trick-play track that also meets the optimal size and hard limits
    assertThat(definition.tracks).hasLength(1);
    assertThat(definition.tracks[0]).isEqualTo(0);
  }

  @Test
  public void test_selectVideoTrack_nullOptimalVideoSize() throws ExoPlaybackException {
    // Create formats where some meet our hard limits
    Format[] formats = new Format[] {
        baseTestFormat.buildUpon().setAverageBitrate(2_000_000).setWidth(960).setHeight(540).setFrameRate(30.0f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(3_000_000).setWidth(1280).setHeight(720).setFrameRate(30.0f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(4_000_000).setWidth(1920).setHeight(1080).setFrameRate(30.0f).build(),
    };
    
    // Don't set an optimal size (should use all filtered tracks)
    multiViewTrackSelector.setOptimalVideoSize(null);
    
    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);
    
    // Call selectVideoTrack
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, 
                                               multiViewTrackSelector.getParameters(), true);
    
    assertThat(definition).isNotNull();
    
    // Should select all tracks that meet our hard limits
    // They're already sorted by decoder load, so all 3 should be selected in order
    assertThat(definition.tracks).hasLength(3);
    assertThat(definition.tracks[0]).isEqualTo(0);
    assertThat(definition.tracks[1]).isEqualTo(1);
    assertThat(definition.tracks[2]).isEqualTo(2);
  }

  @Test
  public void test_setMinTracksToKeep_modifiesInternalState() {
    // Default value should be 1
    assertThat(multiViewTrackSelector.getMinTracksToKeep()).isEqualTo(1);
    
    // Set and verify new value
    multiViewTrackSelector.setMinTracksToKeep(3);
    assertThat(multiViewTrackSelector.getMinTracksToKeep()).isEqualTo(3);
    
    // Set back to default
    multiViewTrackSelector.setMinTracksToKeep(1);
    assertThat(multiViewTrackSelector.getMinTracksToKeep()).isEqualTo(1);
  }
  
  @Test
  public void test_selectVideoTrack_respectsMinTracksToKeep() throws ExoPlaybackException {
    // Create formats where all exceed our hard limits (60fps and high bitrate)
    Format[] formats = new Format[] {
        baseTestFormat.buildUpon().setAverageBitrate(6_000_000).setWidth(1280).setHeight(720).setFrameRate(60.0f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(8_000_000).setWidth(1920).setHeight(1080).setFrameRate(60.0f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(10_000_000).setWidth(1920).setHeight(1080).setFrameRate(60.0f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(12_000_000).setWidth(3840).setHeight(2160).setFrameRate(60.0f).build(),
    };
    
    OptimalVideoSize mockOptimalSize = new OptimalVideoSize(960, 540);
    multiViewTrackSelector.setOptimalVideoSize(mockOptimalSize);
    
    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);
    
    // Test with minTracksToKeep = 1 (default)
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, 
                                               multiViewTrackSelector.getParameters(), true);
    
    assertThat(definition).isNotNull();
    assertThat(definition.tracks).hasLength(1);
    assertThat(definition.tracks[0]).isEqualTo(0); // Should select lowest decoder load track
    
    // Test with minTracksToKeep = 2
    multiViewTrackSelector.setMinTracksToKeep(2);
    definition = multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, 
                                                      multiViewTrackSelector.getParameters(), true);
    
    assertThat(definition).isNotNull();
    assertThat(definition.tracks).hasLength(2);
    assertThat(definition.tracks[0]).isEqualTo(0); // Should select two lowest decoder load tracks
    assertThat(definition.tracks[1]).isEqualTo(1);
    
    // Test with minTracksToKeep = 3
    multiViewTrackSelector.setMinTracksToKeep(3);
    definition = multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, 
                                                      multiViewTrackSelector.getParameters(), true);
    
    assertThat(definition).isNotNull();
    assertThat(definition.tracks).hasLength(3);
    assertThat(definition.tracks[0]).isEqualTo(0);
    assertThat(definition.tracks[1]).isEqualTo(1);
    assertThat(definition.tracks[2]).isEqualTo(2);
    
    // Test with minTracksToKeep > available tracks
    multiViewTrackSelector.setMinTracksToKeep(10);
    definition = multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, 
                                                      multiViewTrackSelector.getParameters(), true);
    
    assertThat(definition).isNotNull();
    assertThat(definition.tracks).hasLength(4); // Should not exceed available tracks
  }

  @Test
  public void test_categorizeTrack() {
    // Test format that meets all limits
    Format meetsAllFormat = baseTestFormat.buildUpon()
        .setAverageBitrate(3_000_000)
        .setWidth(960)
        .setHeight(540)
        .setFrameRate(29.97f)
        .build();
    assertThat(MultiViewTrackSelector.categorizeTrack(meetsAllFormat))
        .isEqualTo(TrackCategory.MEETS_ALL_LIMITS);
        
    // Test exceeds bitrate only
    Format exceedsBitrateFormat = baseTestFormat.buildUpon()
        .setAverageBitrate(5_000_000)
        .setWidth(960)
        .setHeight(540)
        .setFrameRate(29.97f)
        .build();
    assertThat(MultiViewTrackSelector.categorizeTrack(exceedsBitrateFormat))
        .isEqualTo(TrackCategory.EXCEEDS_BITRATE);
    
    // Test exceeds frame rate only
    Format exceedsFrameRateFormat = baseTestFormat.buildUpon()
        .setAverageBitrate(3_000_000)
        .setWidth(960)
        .setHeight(540)
        .setFrameRate(60.0f)
        .build();
    assertThat(MultiViewTrackSelector.categorizeTrack(exceedsFrameRateFormat))
        .isEqualTo(TrackCategory.EXCEEDS_FRAME_RATE);
    
    // Test exceeds both limits
    Format exceedsAllFormat = baseTestFormat.buildUpon()
        .setAverageBitrate(5_000_000)
        .setWidth(960)
        .setHeight(540)
        .setFrameRate(60.0f)
        .build();
    assertThat(MultiViewTrackSelector.categorizeTrack(exceedsAllFormat))
        .isEqualTo(TrackCategory.EXCEEDS_BITRATE_AND_FRAME_RATE);
  }

  @Test
  public void test_policy_frameRateLimit() throws ExoPlaybackException {
    Format[] formats = {
        // Standard frame rate track (30fps)
        baseTestFormat.buildUpon().setAverageBitrate(3_000_000).setWidth(1280).setHeight(720).setFrameRate(30.0f).build(),
        
        // High frame rate track (60fps) - should be deprioritized
        baseTestFormat.buildUpon().setAverageBitrate(3_000_000).setWidth(1280).setHeight(720).setFrameRate(60.0f).build()
    };
    
    // Configure selector
    OptimalVideoSize mockOptimalSize = new OptimalVideoSize(1280, 720);
    multiViewTrackSelector.setOptimalVideoSize(mockOptimalSize);
    
    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);
    
    // Run selection
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, 
                                               multiViewTrackSelector.getParameters(), true);
    
    // Verify that only the standard frame rate track (30fps) is selected
    assertThat(definition).isNotNull();
    assertThat(definition.tracks).hasLength(1);
    assertThat(definition.tracks[0]).isEqualTo(0);  // First track (30fps)
  }
  
  @Test
  public void test_policy_bitrateLimit() throws ExoPlaybackException {
    Format[] formats = {
        // Standard bitrate track (3Mbps)
        baseTestFormat.buildUpon().setAverageBitrate(3_000_000).setWidth(1280).setHeight(720).setFrameRate(30.0f).build(),
        
        // High bitrate track (5Mbps) - should be deprioritized
        baseTestFormat.buildUpon().setAverageBitrate(5_000_000).setWidth(1280).setHeight(720).setFrameRate(30.0f).build()
    };
    
    // Configure selector
    OptimalVideoSize mockOptimalSize = new OptimalVideoSize(1280, 720);
    multiViewTrackSelector.setOptimalVideoSize(mockOptimalSize);
    
    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);
    
    // Run selection
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, 
                                               multiViewTrackSelector.getParameters(), true);
    
    // Verify that only the standard bitrate track is selected
    assertThat(definition).isNotNull();
    assertThat(definition.tracks).hasLength(1);
    assertThat(definition.tracks[0]).isEqualTo(0);  // First track (standard bitrate)
  }
  
  @Test
  public void test_policy_optimalSizeSelection() throws ExoPlaybackException {
    Format[] formats = {
        // Exact match for optimal size
        baseTestFormat.buildUpon().setAverageBitrate(2_000_000).setWidth(960).setHeight(540).setFrameRate(30.0f).build(),
        
        // Higher resolution
        baseTestFormat.buildUpon().setAverageBitrate(3_000_000).setWidth(1280).setHeight(720).setFrameRate(30.0f).build(),
        
        // Even higher resolution
        baseTestFormat.buildUpon().setAverageBitrate(4_000_000).setWidth(1920).setHeight(1080).setFrameRate(30.0f).build()
    };
    
    // Configure selector - set optimal size of 960x540
    OptimalVideoSize mockOptimalSize = new OptimalVideoSize(960, 540);
    multiViewTrackSelector.setOptimalVideoSize(mockOptimalSize);
    
    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);
    
    // Run selection
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, 
                                               multiViewTrackSelector.getParameters(), true);
    
    // Verify that only the exact match to optimal size is selected
    assertThat(definition).isNotNull();
    assertThat(definition.tracks).hasLength(1);
    assertThat(definition.tracks[0]).isEqualTo(0);  // First track (exact match)
  }
  
  @Test
  public void test_policy_minTracksToKeepWhenAllExceedLimits() throws ExoPlaybackException {
    Format[] formats = {
        // All formats exceed limits (both high frame rate and high bitrate)
        baseTestFormat.buildUpon().setAverageBitrate(5_000_000).setWidth(1280).setHeight(720).setFrameRate(60.0f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(8_000_000).setWidth(1920).setHeight(1080).setFrameRate(60.0f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(12_000_000).setWidth(3840).setHeight(2160).setFrameRate(60.0f).build()
    };
    
    // Configure selector
    OptimalVideoSize mockOptimalSize = new OptimalVideoSize(960, 540);
    multiViewTrackSelector.setOptimalVideoSize(mockOptimalSize);
    
    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);
    
    // Test with default minTracksToKeep = 1
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, 
                                               multiViewTrackSelector.getParameters(), true);
    
    // Verify that only one lowest-resource track is kept
    assertThat(definition).isNotNull();
    assertThat(definition.tracks).hasLength(1);
    assertThat(definition.tracks[0]).isEqualTo(0);  // First track (lowest resource)
    
    // Test with minTracksToKeep = 2
    multiViewTrackSelector.setMinTracksToKeep(2);
    definition = multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, 
                                                       multiViewTrackSelector.getParameters(), true);
    
    // Verify that two lowest-resource tracks are kept
    assertThat(definition).isNotNull();
    assertThat(definition.tracks).hasLength(2);
    assertThat(definition.tracks[0]).isEqualTo(0);  // First track
    assertThat(definition.tracks[1]).isEqualTo(1);  // Second track
    
    // Reset minTracksToKeep to default
    multiViewTrackSelector.setMinTracksToKeep(1);
  }
  
  @Test
  public void test_policy_trickPlayFiltering() throws ExoPlaybackException {
    Format[] formats = {
        // Regular track
        baseTestFormat.buildUpon().setAverageBitrate(3_000_000).setWidth(1280).setHeight(720).setFrameRate(30.0f).build(),
        
        // Trick-play track with better characteristics (lower bitrate)
        baseTestFormat.buildUpon().setAverageBitrate(1_000_000).setWidth(1280).setHeight(720).setFrameRate(30.0f)
            .setRoleFlags(ROLE_FLAG_TRICK_PLAY).build()
    };
    
    // Configure selector
    OptimalVideoSize mockOptimalSize = new OptimalVideoSize(1280, 720);
    multiViewTrackSelector.setOptimalVideoSize(mockOptimalSize);
    
    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);
    
    // Run selection
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, 
                                               multiViewTrackSelector.getParameters(), true);
    
    // Verify that only the non-trick-play track is selected, even though the trick-play track
    // has better characteristics (lower bitrate)
    assertThat(definition).isNotNull();
    assertThat(definition.tracks).hasLength(1);
    assertThat(definition.tracks[0]).isEqualTo(0);  // First track (non-trick-play)
  }
  
  @Test
  public void test_policy_trackPrioritization() throws ExoPlaybackException {
    Format[] formats = {
        // Format that exceeds bitrate limit only (5Mbps, 30fps)
        baseTestFormat.buildUpon().setAverageBitrate(5_000_000).setWidth(1280).setHeight(720).setFrameRate(30.0f).build(),
        
        // Format that exceeds frame rate limit only (3Mbps, 60fps)
        baseTestFormat.buildUpon().setAverageBitrate(3_000_000).setWidth(1280).setHeight(720).setFrameRate(60.0f).build(),
        
        // Format that exceeds both limits (5Mbps, 60fps)
        baseTestFormat.buildUpon().setAverageBitrate(5_000_000).setWidth(1280).setHeight(720).setFrameRate(60.0f).build()
    };
    
    // Configure selector
    OptimalVideoSize mockOptimalSize = new OptimalVideoSize(1280, 720);
    multiViewTrackSelector.setOptimalVideoSize(mockOptimalSize);
    
    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);
    
    // Run selection
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, 
                                               multiViewTrackSelector.getParameters(), true);
    
    // Verify that the track with standard frame rate but high bitrate is selected
    // This confirms the prioritization: EXCEEDS_BITRATE > EXCEEDS_FRAME_RATE > EXCEEDS_BITRATE_AND_FRAME_RATE
    assertThat(definition).isNotNull();
    assertThat(definition.tracks).hasLength(1);
    assertThat(definition.tracks[0]).isEqualTo(0);  // First track (exceeds bitrate only)
  }
  
  @Test
  public void test_policy_resourceBasedSorting() throws ExoPlaybackException {
    Format[] formats = {
        // Order is deliberately mixed to test sorting:
        
        // High resource (1080p)
        baseTestFormat.buildUpon().setAverageBitrate(4_000_000).setWidth(1920).setHeight(1080).setFrameRate(30.0f).build(),
        
        // Low resource (540p)
        baseTestFormat.buildUpon().setAverageBitrate(1_000_000).setWidth(960).setHeight(540).setFrameRate(30.0f).build(),
        
        // Medium resource (720p)
        baseTestFormat.buildUpon().setAverageBitrate(2_000_000).setWidth(1280).setHeight(720).setFrameRate(30.0f).build()
    };
    
    // Configure selector - set optimal size larger than any format to force resource-based sorting
    OptimalVideoSize mockOptimalSize = new OptimalVideoSize(3840, 2160);
    multiViewTrackSelector.setOptimalVideoSize(mockOptimalSize);
    
    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);
    
    // Run selection
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, 
                                               multiViewTrackSelector.getParameters(), true);
    
    // Verify tracks are sorted by resource usage (lowest to highest)
    assertThat(definition).isNotNull();
    assertThat(definition.tracks).hasLength(3);
    
    // Should be sorted by decoder load: 540p -> 720p -> 1080p
    assertThat(definition.tracks[0]).isEqualTo(1);  // 540p (lowest resource)
    assertThat(definition.tracks[1]).isEqualTo(2);  // 720p (medium resource)
    assertThat(definition.tracks[2]).isEqualTo(0);  // 1080p (highest resource)
  }
  
  @Test
  public void test_policy_defaultMinTracksToKeep() {
    assertThat(multiViewTrackSelector.getMinTracksToKeep()).isEqualTo(1);
  }
  
  @Test
  public void test_policy_trackCategories() {
    // Testing that categories are defined in the correct priority order
    TrackCategory[] categories = TrackCategory.values();
    
    // Order should be:
    // 1. MEETS_ALL_LIMITS (highest priority)
    // 2. EXCEEDS_BITRATE
    // 3. EXCEEDS_FRAME_RATE
    // 4. EXCEEDS_BITRATE_AND_FRAME_RATE (lowest priority)
    
    assertThat(categories[0]).isEqualTo(TrackCategory.MEETS_ALL_LIMITS);
    assertThat(categories[1]).isEqualTo(TrackCategory.EXCEEDS_BITRATE);
    assertThat(categories[2]).isEqualTo(TrackCategory.EXCEEDS_FRAME_RATE);
    assertThat(categories[3]).isEqualTo(TrackCategory.EXCEEDS_BITRATE_AND_FRAME_RATE);
  }

  @Test
  public void test_selectVideoTrack_withIdenticalDecoderLoads() throws ExoPlaybackException {
    // Create formats with identical decoder loads but different properties
    Format[] formats = new Format[] {
        // Both formats have 720p @ 30fps = identical decoder loads
        // But different bitrate
        baseTestFormat.buildUpon().setAverageBitrate(2_500_000).setWidth(1280).setHeight(720).setFrameRate(30.0f).build(),
        baseTestFormat.buildUpon().setAverageBitrate(3_500_000).setWidth(1280).setHeight(720).setFrameRate(30.0f).build(),
    };
    
    // Set optimal size larger than formats to ensure both exceed optimal
    OptimalVideoSize mockOptimalSize = new OptimalVideoSize(960, 540);
    multiViewTrackSelector.setOptimalVideoSize(mockOptimalSize);
    
    TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));
    int[][] formatSupport = getRendererSupportsAll(formats);
    
    // Run selection
    ExoTrackSelection.Definition definition =
        multiViewTrackSelector.selectVideoTrack(groups, formatSupport, ADAPTIVE_NOT_SEAMLESS, 
                                               multiViewTrackSelector.getParameters(), true);
    
    assertThat(definition).isNotNull();
    
    // With identical decoder loads, the lower bitrate should be preferred
    assertThat(definition.tracks).hasLength(1);
    // Verify that the track with the lower bitrate is selected as a tie-breaker when decoder loads are identical
    assertThat(definition.tracks[0]).isEqualTo(0);  // First track (lower bitrate)
    assertThat(formats[0].averageBitrate).isLessThan(formats[1].averageBitrate);  // Confirm lower bitrate
    
    // Verify decoder loads are actually identical
    float load1 = getDecoderLoad(formats[0]);
    float load2 = getDecoderLoad(formats[1]);
    assertThat(load1).isEqualTo(load2);
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