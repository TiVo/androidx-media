package com.google.android.exoplayer2.trackselection;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.android.exoplayer2.testutil.FakeMediaChunk;
import com.google.android.exoplayer2.util.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.trickplay.TrickPlayControlInternal;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.MimeTypes;

import static com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection.DEFAULT_BANDWIDTH_FRACTION;
import static com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection.DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE;
import static com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection.DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS;
import static com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection.DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS;
import static com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(AndroidJUnit4.class)
public class IFrameAwareAdaptiveTrackSelectionTest {

    private static final MediaChunkIterator[] THREE_EMPTY_MEDIA_CHUNK_ITERATORS =
            new MediaChunkIterator[] {
                    MediaChunkIterator.EMPTY, MediaChunkIterator.EMPTY, MediaChunkIterator.EMPTY
            };

    @Mock
    private BandwidthMeter mockBandwidthMeter;

    @Mock
    private TrickPlayControlInternal mockTrickPlayControl;


    private ExoTrackSelection.Definition[] definitions;
    private TrackGroup noIFrameFormatsGroup;
    private IFrameAwareAdaptiveTrackSelection.Factory factory;
    private int maxIframeBitrate;
    private int iFrameTrackCount;
    private int maxNonIframeBitrate;
    private Format srcTpFormat;
    private Clock mockClock;

    @Before
    public void setUp() {
        initMocks(this);
        mockClock = new FakeClock(0);
        factory = new IFrameAwareAdaptiveTrackSelection.Factory(
            DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
            DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
            1,
            DEFAULT_BANDWIDTH_FRACTION,
            DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
            mockClock);

        // Normal tracks, in order of bitrate
        maxNonIframeBitrate = 300;
        Format format1 = videoFormat(maxNonIframeBitrate, "a", 1280,720, 0, 30.0f);
        Format format2 = videoFormat(maxNonIframeBitrate / 2, "b", 1280,720,0, 30.0f);
        Format format3 = videoFormat(maxNonIframeBitrate / 3, "c", 1280,720,0, 30.0f);

        // Base iFrame source track, assume iFrames 1 second duration
        maxIframeBitrate = format1.bitrate / 15;
        iFrameTrackCount = 1;
        srcTpFormat = videoFormat(maxIframeBitrate, "e_tp", 1280,720, C.ROLE_FLAG_TRICK_PLAY, 1.0f);

        ArrayList<Format> formats = new ArrayList<>(Arrays.asList(format1, format2, format3, srcTpFormat));

        for (int subset : new int[] {2, 3, 4, 5}) {
            Format curated = srcTpFormat.buildUpon()
                .setPeakBitrate(srcTpFormat.peakBitrate / subset)
                .setFrameRate(srcTpFormat.frameRate / subset)
                .setLabel("e_tp_" + subset)
                .build();
            formats.add(curated);
            iFrameTrackCount++;
        }

        int[] selectedTracks = new int[formats.size()];
        for (int i=0; i < formats.size(); i++) {
            selectedTracks[i] = i;
        }

        TrackGroup trackGroup = new TrackGroup(formats.toArray(new Format[] {}));

        definitions = new ExoTrackSelection.Definition[]{
                new ExoTrackSelection.Definition(trackGroup, selectedTracks)
        };

        noIFrameFormatsGroup = new TrackGroup(format1, format2, format3);

        when(mockTrickPlayControl.isPlaybackSpeedForwardTrickPlayEnabled()).thenReturn(true);

        when(mockTrickPlayControl.getSpeedFor(isA(TrickPlayControl.TrickMode.class))).then((InvocationOnMock invocation) -> {
            switch ((TrickPlayControl.TrickMode) invocation.getArgument(0)) {
                case FF1:
                    return 15.0f;
                case FF2:
                    return 30.0f;
                case FF3:
                    return 60.0f;
                case NORMAL:
                    return 1.0f;
                default:
                    throw new UnsupportedOperationException("only forward modes required");
            }
        });

        factory.setTrickPlayControl(mockTrickPlayControl);
    }

    private AdaptiveTrackSelection createTrackSelection() {
        ExoTrackSelection selections[] = factory.createTrackSelections(definitions, mockBandwidthMeter,
            new MediaSource.MediaPeriodId(new Object()), Timeline.EMPTY);
        assertThat(selections.length).isEqualTo(1);
        assertThat(selections[0]).isInstanceOf(AdaptiveTrackSelection.class);
        AdaptiveTrackSelection adaptiveTrackSelection = (AdaptiveTrackSelection) selections[0];
        adaptiveTrackSelection.updateSelectedTrack(
                /* playbackPositionUs= */ 0,
                /* bufferedDurationUs= */ 9_999_000,
                /* availableDurationUs= */ C.TIME_UNSET,
                /* queue= */ Collections.emptyList(),
                /* mediaChunkIterators= */ THREE_EMPTY_MEDIA_CHUNK_ITERATORS);
        return adaptiveTrackSelection;
    }

    @Test
    public void testFilterTracks_filtersIframe() {

        // in trickplay mode, filters to iFrame only tracks
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.FORWARD);
        when(mockTrickPlayControl.getCurrentTrickMode()).thenReturn(TrickPlayControl.TrickMode.FF1);
        TrackGroup group = definitions[0].group;
        int [] tracks = definitions[0].tracks;

        int[] selectedTracks = factory.filterTracks(group, tracks);
        assertThat(selectedTracks.length).isEqualTo(iFrameTrackCount);
        for (int index : selectedTracks) {
            assertThat(group.getFormat(index).roleFlags).isEqualTo(C.ROLE_FLAG_TRICK_PLAY);
        }

        // in normal mode, filters to only normal tracks
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.NONE);
        when(mockTrickPlayControl.getCurrentTrickMode()).thenReturn(TrickPlayControl.TrickMode.NORMAL);

        selectedTracks = factory.filterTracks(group, tracks);
        assertThat(selectedTracks.length).isEqualTo(3);
        for (int index : selectedTracks) {
            assertThat(group.getFormat(index).roleFlags).isEqualTo(0);
        }
    }

    @Test
    public void testFilterTracks_honorsOverrides() {
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.NONE);
        when(mockTrickPlayControl.getCurrentTrickMode()).thenReturn(TrickPlayControl.TrickMode.NORMAL);
        TrackGroup group = definitions[0].group;

        // Filtered set from DefaultTrackSelector will always include the iFrame only tracks, for this
        // test just disable one non-iFrame track
        ArrayList<Integer> subset = new ArrayList<>();
        for (int i=0; i < group.length; i++) {
            if (i > 0) {
                subset.add(i);
            }
        }

        // In normal mode with filtered tracks iFrame only are removed and filtered set remains
        int[] selectedTracks = factory.filterTracks(group, fromList(subset));
        assertThat(selectedTracks.length).isEqualTo(subset.size() - iFrameTrackCount);
        for (int index : selectedTracks) {
            assertThat(group.getFormat(index).roleFlags).isEqualTo(0);
        }

        // in iFrame only mode, only iFrame tracks only are returned
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.FORWARD);
        when(mockTrickPlayControl.getCurrentTrickMode()).thenReturn(TrickPlayControl.TrickMode.FF1);
        selectedTracks = factory.filterTracks(group, fromList(subset));
        for (int index : selectedTracks) {
            assertThat(group.getFormat(index).roleFlags).isEqualTo(C.ROLE_FLAG_TRICK_PLAY);
        }
    }

    @Test
    public void testFilterTracks_iFrameOverridePreserved() {

        // in normal mode or iFrame only mode, an override that selects only iFrame tracks is preserved in both modes
        // this use case allows testing iFrame only tracks individually or in subsets
        //
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.NONE);
        when(mockTrickPlayControl.getCurrentTrickMode()).thenReturn(TrickPlayControl.TrickMode.NORMAL);
        TrackGroup group = definitions[0].group;
        int[] subset = new int[] { 3, 4};      // iFrame only
        int[] selectedTracks = factory.filterTracks(group, subset);
        assertThat(selectedTracks).isEqualTo(subset);

        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.FORWARD);
        when(mockTrickPlayControl.getCurrentTrickMode()).thenReturn(TrickPlayControl.TrickMode.FF1);
        group = definitions[0].group;
        subset = new int[] { 3, 4};      // iFrame only
        selectedTracks = factory.filterTracks(group, subset);
        assertThat(selectedTracks).isEqualTo(subset);
    }

    @Test
    public void testShouldFilterTracks() {
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.FORWARD);
        when(mockTrickPlayControl.getCurrentTrickMode()).thenReturn(TrickPlayControl.TrickMode.FF1);

        // Absence of any i-Frame should block filtering
        boolean noFilter = factory.shouldFilterTracks(noIFrameFormatsGroup, new int[] {0,1,2});
        assertThat(noFilter).isFalse();

    }

    @Test
    public void testDeferSwitching() {
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.REVERSE);
        when(mockTrickPlayControl.getCurrentTrickMode()).thenReturn(TrickPlayControl.TrickMode.FR1);
        AdaptiveTrackSelection adaptiveTrackSelection = createTrackSelection();
        assertThat(adaptiveTrackSelection).isInstanceOf(IFrameAwareAdaptiveTrackSelection.class);
        IFrameAwareAdaptiveTrackSelection testee = (IFrameAwareAdaptiveTrackSelection) adaptiveTrackSelection;
        TrackGroup group = definitions[0].group;
        Format iframeFormat = group.getFormat(4);

        boolean shouldDefer = testee.testShouldDeferSwitching(5_000_000, C.TIME_UNSET, 4, iframeFormat);
        assertThat(shouldDefer).isTrue();


        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.FORWARD);
        when(mockTrickPlayControl.getCurrentTrickMode()).thenReturn(TrickPlayControl.TrickMode.FF1);
        shouldDefer = testee.testShouldDeferSwitching(5_000_000, C.TIME_UNSET, 4, iframeFormat);
        assertThat(shouldDefer).isFalse();
    }

    @Test
    public void testSelectIframeOnly() {

        when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(100000L);       // all will be good
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.FORWARD);
        when(mockTrickPlayControl.getCurrentTrickMode()).thenReturn(TrickPlayControl.TrickMode.FF1);

        AdaptiveTrackSelection adaptiveTrackSelection = createTrackSelection();
        Format selected = adaptiveTrackSelection.getFormat(adaptiveTrackSelection.getSelectedIndex());
        assertThat(selected.roleFlags).isEqualTo(C.ROLE_FLAG_TRICK_PLAY);
    }

    @Test
    public void testSelectHighestIframeOnlyForReverse() {
        when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(100000L);       // all will be good
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.REVERSE);
        when(mockTrickPlayControl.getCurrentTrickMode()).thenReturn(TrickPlayControl.TrickMode.FR1);

        AdaptiveTrackSelection adaptiveTrackSelection = createTrackSelection();
        Format selected = adaptiveTrackSelection.getFormat(adaptiveTrackSelection.getSelectedIndex());
        assertThat(selected.roleFlags).isEqualTo(C.ROLE_FLAG_TRICK_PLAY);
        assertThat(selected.bitrate).isEqualTo(maxIframeBitrate);
    }

    @Test
    public void testSelectHighestIframeOnlyForScrub() {
        when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(100000L);       // all will be good
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.SCRUB);
        when(mockTrickPlayControl.getCurrentTrickMode()).thenReturn(TrickPlayControl.TrickMode.SCRUB);

        AdaptiveTrackSelection adaptiveTrackSelection = createTrackSelection();
        Format selected = adaptiveTrackSelection.getFormat(adaptiveTrackSelection.getSelectedIndex());
        assertThat(selected.roleFlags).isEqualTo(C.ROLE_FLAG_TRICK_PLAY);
        assertThat(selected.bitrate).isEqualTo(maxIframeBitrate);
    }

    @Test
    public void test_closestTargetFrameRateMatch() {
        ExoTrackSelection selections[] = factory.createTrackSelections(definitions, mockBandwidthMeter,
            new MediaSource.MediaPeriodId(new Object()), Timeline.EMPTY);
        assertThat(selections.length).isEqualTo(1);
        assertThat(selections[0]).isInstanceOf(AdaptiveTrackSelection.class);
        IFrameAwareAdaptiveTrackSelection adaptiveTrackSelection = (IFrameAwareAdaptiveTrackSelection) selections[0];

        // numbers assume 1 FPS source format
        assertThat(srcTpFormat.frameRate).isEqualTo(1.0f);

        // Set for FF1 forward
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.FORWARD);
        when(mockTrickPlayControl.getCurrentTrickMode()).thenReturn(TrickPlayControl.TrickMode.FF1);

        // For the purpose of this test the fixed frame rate values from the Formats are enough
        when(mockTrickPlayControl.getFrameRateForFormat(isA(Format.class))).then((InvocationOnMock invocation) -> ((Format) invocation.getArgument(0)).frameRate);

        when(mockTrickPlayControl.getTargetFrameRateForPlaybackSpeed(15.0f)).thenReturn(3.0f);
        Format result = adaptiveTrackSelection.closestTargetFrameRateMatch(15.0f);
        assertThat(result.frameRate).isEqualTo(0.2f);       // the 5 subset is an exact match

        when(mockTrickPlayControl.getTargetFrameRateForPlaybackSpeed(15.0f)).thenReturn(5.0f);
        result = adaptiveTrackSelection.closestTargetFrameRateMatch(15.0f);
        assertThat(result.frameRate).isWithin(0.001f).of(0.333f);

        assertThat(result).isNotNull();
    }

    @Test
    public void test_evaluateQueueSize_normalMode() {
        // Normal mode should basically call the super class (which prunes < 1080 segments)
        when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(0L);       // no bitrate is < than this
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.NONE);
        when(mockTrickPlayControl.getCurrentTrickMode()).thenReturn(TrickPlayControl.TrickMode.NORMAL);

        ExoTrackSelection selections[] = factory.createTrackSelections(definitions, mockBandwidthMeter,
            new MediaSource.MediaPeriodId(new Object()), Timeline.EMPTY);
        ExoTrackSelection trackSelection = selections[0];

        MediaChunk[] chunks = new MediaChunk[] {
            new FakeMediaChunk(videoFormat(0, "1", 1080, 1920, 0, 60.0f), 0, 6_000_000),
            new FakeMediaChunk(videoFormat(0, "1", 1080, 1920, 0, 60.0f), 6, 12_000_000),
            new FakeMediaChunk(videoFormat(0, "1", 640, 480, 0, 60.0f), 12_000_000, 18_000_000)
        };
        int expectedSize = trackSelection.evaluateQueueSize(0, Arrays.asList(chunks));

        // 640 chunk is discarded
        assertThat(expectedSize).isEqualTo(2);
    }

    @Test
    public void test_evaluateQueueSize_fastForwardDownshift() {
        // Start out VTP in FF3 mode
        when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(0L);       // no bitrate is < than this
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.FORWARD);
        when(mockTrickPlayControl.getCurrentTrickMode()).thenReturn(TrickPlayControl.TrickMode.FF3);

        // numbers assume 1 FPS source format
        assertThat(srcTpFormat.frameRate).isEqualTo(1.0f);
        // For the purpose of this test the fixed frame rate values from the Formats are enough
        when(mockTrickPlayControl.getFrameRateForFormat(isA(Format.class))).then((InvocationOnMock invocation) -> ((Format) invocation.getArgument(0)).frameRate);

        // make FF1 is exact match for subset 2 (tp_2) with .5fps
        when(mockTrickPlayControl.getTargetFrameRateForPlaybackSpeed(15.0f)).thenReturn(7.5f);

        // For FF3, 8 FPS target will make the 0.2fps format closest to a match (12FPS at 60x)
        when(mockTrickPlayControl.getTargetFrameRateForPlaybackSpeed(60.0f)).thenReturn(8.0f);

        when(mockTrickPlayControl.getFrameRateForFormat(isA(Format.class))).then((InvocationOnMock invocation) -> ((Format) invocation.getArgument(0)).frameRate);


        ExoTrackSelection selections[] = factory.createTrackSelections(definitions, mockBandwidthMeter,
            new MediaSource.MediaPeriodId(new Object()), Timeline.EMPTY);
        ExoTrackSelection trackSelection = selections[0];

        // Make the initial selection
        trackSelection.updateSelectedTrack(0, 50_000_000, 100_000_000,
            Collections.emptyList(), THREE_EMPTY_MEDIA_CHUNK_ITERATORS);

        Format selectedFormat = trackSelection.getFormat(trackSelection.getSelectedIndex());
        assertThat(selectedFormat.frameRate).isEqualTo(0.2f);

        // Fake loading a couple of segments at this format, they should not be pruned
        ArrayList<MediaChunk> loadedChunks = new ArrayList<>();
        long positionUs = 0;
        int loadedSegmentsCount = 3;
        for (int i=0; i < loadedSegmentsCount; i++) {
            loadedChunks.add(new FakeMediaChunk(selectedFormat, positionUs, positionUs + 3_000_000));
            positionUs += 3_000_000;
        }
        int expectedSize = trackSelection.evaluateQueueSize(0, loadedChunks);
        assertThat(expectedSize).isEqualTo(loadedSegmentsCount);

        // Switch to FF1 and, then the .5fps should be best
        when(mockTrickPlayControl.getCurrentTrickMode()).thenReturn(TrickPlayControl.TrickMode.FF1);
        trackSelection.updateSelectedTrack(0, 50_000_000, 100_000_000,
            Collections.emptyList(), THREE_EMPTY_MEDIA_CHUNK_ITERATORS);

        assertThat(trackSelection.getFormat(trackSelection.getSelectedIndex()).frameRate).isEqualTo(0.5f);

        // On a downshift in speed we must quickly buffer higher frame rate, so only should keep 1 chunk
        expectedSize = trackSelection.evaluateQueueSize(0, loadedChunks);
        assertThat(expectedSize).isEqualTo(1);

    }

    @Test
    public void testNotIFrameOnly() {
        when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(0L);       // no bitrate is < than this
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.NONE);
        when(mockTrickPlayControl.getCurrentTrickMode()).thenReturn(TrickPlayControl.TrickMode.NORMAL);

        AdaptiveTrackSelection adaptiveTrackSelection = createTrackSelection();

        Format selected = adaptiveTrackSelection.getFormat(adaptiveTrackSelection.getSelectedIndex());
        assertThat(selected.roleFlags).isEqualTo(0);

        // Default (for better or worse) is hightest index, if no format is suitable for bandwidth
        assertThat(adaptiveTrackSelection.getSelectedIndex()).isEqualTo(2);
    }

    @Test
    public void testDefaultNotIFrameOnly() {
        long testBandwidth = 0L;
        when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(testBandwidth);       // all will be good
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.NONE);
        when(mockTrickPlayControl.getCurrentTrickMode()).thenReturn(TrickPlayControl.TrickMode.NORMAL);
        AdaptiveTrackSelection adaptiveTrackSelection = createTrackSelection();
        Format selected = adaptiveTrackSelection.getFormat(adaptiveTrackSelection.getSelectedIndex());
        assertThat(selected.roleFlags).isEqualTo(0);
    }

    @Test
    public void testSelectHighestNonIframe() {
        long testBandwidth = maxNonIframeBitrate * 2L;
        when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(testBandwidth);       // all will be good
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.NONE);
        when(mockTrickPlayControl.getCurrentTrickMode()).thenReturn(TrickPlayControl.TrickMode.NORMAL);
        AdaptiveTrackSelection adaptiveTrackSelection = createTrackSelection();
        Format selected = adaptiveTrackSelection.getFormat(adaptiveTrackSelection.getSelectedIndex());
        assertThat(selected.roleFlags).isEqualTo(0);
        assertThat(selected.bitrate).isEqualTo(maxNonIframeBitrate);
    }
    private static Format videoFormat(int bitrate, String id, int width, int height, int role, float frameRate) {

        return new Format.Builder()
            .setId(id)
            .setLabel(id)
            .setSelectionFlags(0)
            .setRoleFlags(role)
            .setAverageBitrate(bitrate)
            .setPeakBitrate(bitrate)
            .setCodecs(null)
            .setMetadata(null)
            .setContainerMimeType(MimeTypes.VIDEO_H264)
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setInitializationData(null)
            .setWidth(width)
            .setHeight(height)
            .setFrameRate(frameRate)
            .build();
    }

    private static int[] fromList(ArrayList<Integer> source) {
        int[] retValue = new int[source.size()];
        int i = 0;
        for (Integer value : source) {
            retValue[i++] = value;
        }
        return retValue;
    }
}
