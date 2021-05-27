package com.google.android.exoplayer2.trackselection;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.MimeTypes;
import static com.google.common.truth.Truth.assertThat;
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
    private TrickPlayControl mockTrickPlayControl;


    private TrackSelection.Definition[] definitions;
    private IFrameAwareAdaptiveTrackSelection.Factory factory;

    @Before
    public void setUp() {
        initMocks(this);
        factory = new IFrameAwareAdaptiveTrackSelection.Factory();
        Format format1 = videoFormat(20, "a", 1280, 720, 0, 30.0f);
        Format format2 = videoFormat(15, "b", 1280, 720,0, 30.0f);
        Format format3 = videoFormat(10, "c", 1280, 720,0, 30.0f);
        Format format4 = videoFormat(5, "e_tp", 1280, 720,C.ROLE_FLAG_TRICK_PLAY, 1.0f);
        Format format5 = videoFormat(1, "f_tp", 1280, 720,C.ROLE_FLAG_TRICK_PLAY, 0.2f);
        TrackGroup trackGroup = new TrackGroup(format1, format2, format3, format4, format5);

        definitions = new TrackSelection.Definition[]{
                new TrackSelection.Definition(trackGroup, /* tracks= */ 0, 1, 2, 3, 4)
        };

        factory.setTrickPlayControl(mockTrickPlayControl);
    }

    private AdaptiveTrackSelection createTrackSelection() {
        TrackSelection selections[] = factory.createTrackSelections(definitions, mockBandwidthMeter);
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
    public void testFilterTracks() {
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.FORWARD);
        TrackGroup group = definitions[0].group;
        int [] tracks = definitions[0].tracks;

        int[] selectedTracks = factory.filterTracks(group, tracks);
        assertThat(selectedTracks.length).isEqualTo(2);
        for (int index : selectedTracks) {
            assertThat(group.getFormat(index).roleFlags).isEqualTo(C.ROLE_FLAG_TRICK_PLAY);
        }

        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.NONE);

        selectedTracks = factory.filterTracks(group, tracks);
        assertThat(selectedTracks.length).isEqualTo(3);
        for (int index : selectedTracks) {
            assertThat(group.getFormat(index).roleFlags).isEqualTo(0);
        }
    }

    @Test
    public void testDeferSwitching() {
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.REVERSE);
        AdaptiveTrackSelection adaptiveTrackSelection = createTrackSelection();
        assertThat(adaptiveTrackSelection).isInstanceOf(IFrameAwareAdaptiveTrackSelection.class);
        IFrameAwareAdaptiveTrackSelection testee = (IFrameAwareAdaptiveTrackSelection) adaptiveTrackSelection;
        TrackGroup group = definitions[0].group;
        Format iframeFormat = group.getFormat(4);

        boolean shouldDefer = testee.testShouldDeferSwitching(5_000_000, C.TIME_UNSET, 4, iframeFormat);
        assertThat(shouldDefer).isTrue();


        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.FORWARD);
        shouldDefer = testee.testShouldDeferSwitching(5_000_000, C.TIME_UNSET, 4, iframeFormat);
        assertThat(shouldDefer).isFalse();
    }

    @Test
    public void testSelectIframeOnly() {

        when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(100000L);       // all will be good
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.FORWARD);

        AdaptiveTrackSelection adaptiveTrackSelection = createTrackSelection();
        Format selected = adaptiveTrackSelection.getFormat(adaptiveTrackSelection.getSelectedIndex());
        assertThat(selected.roleFlags).isEqualTo(C.ROLE_FLAG_TRICK_PLAY);
    }

    @Test
    public void testSelectHighestIframeOnlyForReverse() {
        when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(100000L);       // all will be good
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.REVERSE);

        AdaptiveTrackSelection adaptiveTrackSelection = createTrackSelection();
        Format selected = adaptiveTrackSelection.getFormat(adaptiveTrackSelection.getSelectedIndex());
        assertThat(selected.roleFlags).isEqualTo(C.ROLE_FLAG_TRICK_PLAY);
        assertThat(selected.bitrate).isEqualTo(5);
    }

    @Test
    public void testSelectHighestIframeOnlyForScrub() {
        when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(100000L);       // all will be good
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.SCRUB);

        AdaptiveTrackSelection adaptiveTrackSelection = createTrackSelection();
        Format selected = adaptiveTrackSelection.getFormat(adaptiveTrackSelection.getSelectedIndex());
        assertThat(selected.roleFlags).isEqualTo(C.ROLE_FLAG_TRICK_PLAY);
        assertThat(selected.bitrate).isEqualTo(5);
    }

    @Test
    public void testNotIFrameOnly() {
        when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(0L);       // no bitrate is < than this
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.NONE);

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
        AdaptiveTrackSelection adaptiveTrackSelection = createTrackSelection();
        Format selected = adaptiveTrackSelection.getFormat(adaptiveTrackSelection.getSelectedIndex());
        assertThat(selected.roleFlags).isEqualTo(0);
    }

    @Test
    public void testSelectHighestNonIframe() {
        long testBandwidth = 40L;
        when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(testBandwidth);       // all will be good
        when(mockTrickPlayControl.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.NONE);
        AdaptiveTrackSelection adaptiveTrackSelection = createTrackSelection();
        Format selected = adaptiveTrackSelection.getFormat(adaptiveTrackSelection.getSelectedIndex());
        assertThat(selected.roleFlags).isEqualTo(0);
        assertThat(selected.bitrate).isEqualTo(20);
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
}
