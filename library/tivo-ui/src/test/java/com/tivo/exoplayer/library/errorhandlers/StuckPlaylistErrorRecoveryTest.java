package com.tivo.exoplayer.library.errorhandlers;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistTracker;
import static com.google.android.exoplayer2.Player.PLAYBACK_SUPPRESSION_REASON_NONE;
import static com.google.android.exoplayer2.Player.STATE_READY;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class StuckPlaylistErrorRecoveryTest {

    @Mock
    private PlayerErrorRecoverable mockPlayerErrorRecoverable;

    @Mock
    private SimpleExoPlayer mockPlayer;

    @Mock
    private Timeline mockTimeline;

    @Before
    public void setupMocks() {
        MockitoAnnotations.initMocks(this);
        when(mockPlayerErrorRecoverable.getCurrentPlayer()).thenReturn(mockPlayer);
        when(mockPlayer.getPlaybackState()).thenReturn(STATE_READY);
        when(mockPlayer.getPlaybackSuppressionReason()).thenReturn(PLAYBACK_SUPPRESSION_REASON_NONE);
        when(mockPlayer.isPlaying()).thenReturn(true);

        // Default to empty timeline
        when(mockTimeline.getWindowCount()).thenReturn(0);
        when(mockPlayer.getCurrentTimeline()).thenReturn(mockTimeline);

    }

    @Test
    public void testHandleOneErrorOccurrence() {
        StuckPlaylistErrorRecovery testee = new StuckPlaylistErrorRecovery(mockPlayerErrorRecoverable);

        Uri url = Uri.parse("http://example.com/");
        ExoPlaybackException error =
               ExoPlaybackException.createForSource(new HlsPlaylistTracker.PlaylistStuckException(url));

        boolean value = testee.recoverFrom(error);

        // Mock setup
        when(mockPlayer.getCurrentTimeline()).thenReturn(Timeline.EMPTY);

        verify(mockPlayerErrorRecoverable).retryPlayback();
        assertThat(value).isTrue();
        assertThat(testee.currentErrorBeingHandled()).isEqualTo(error);
        assertThat(testee.checkRecoveryCompleted()).isFalse();
        assertThat(testee.isRecoveryInProgress()).isTrue();
        assertThat(testee.isRecoveryFailed()).isFalse();
    }

    @Test
    public void testRecoverAfterTimelineUpdated() {
        StuckPlaylistErrorRecovery testee = new StuckPlaylistErrorRecovery(mockPlayerErrorRecoverable);
        List<Player.EventListener> listeners = getCurrentEventListener();
        Player.EventListener listener = listeners.get(0);
        Uri url = Uri.parse("http://example.com/");
        ExoPlaybackException error =
                ExoPlaybackException.createForSource(new HlsPlaylistTracker.PlaylistStuckException(url));


        // Setup the initial timeline for starting playback
        Timeline.Window currentWidow = new Timeline.Window();
        currentWidow.durationUs = 10;
        currentWidow.windowStartTimeMs = 1000;
        when(mockTimeline.getWindowCount()).thenReturn(1);
        when(mockTimeline.getWindow(anyInt(), any(Timeline.Window.class), anyLong())).thenReturn(currentWidow);
        listener.onTimelineChanged(mockTimeline, 0);

        boolean value = testee.recoverFrom(error);

        verify(mockPlayerErrorRecoverable).retryPlayback();
        assertThat(value).isTrue();
        assertThat(testee.currentErrorBeingHandled()).isEqualTo(error);
        assertThat(testee.checkRecoveryCompleted()).isFalse();
        assertThat(testee.isRecoveryInProgress()).isTrue();
        assertThat(testee.isRecoveryFailed()).isFalse();


        // Update the timeline, so we are un-stuck
        currentWidow.durationUs = 1;
        currentWidow.windowStartTimeMs = 2000;
        listener.onTimelineChanged(mockTimeline, 0);

        // Verify we recovered
        when(mockPlayer.getCurrentTimeline()).thenReturn(mockTimeline);

        assertThat(testee.currentErrorBeingHandled()).isEqualTo(error);
        assertThat(testee.checkRecoveryCompleted()).isTrue();
        assertThat(testee.currentErrorBeingHandled()).isNull();
        assertThat(testee.isRecoveryInProgress()).isFalse();
        assertThat(testee.isRecoveryFailed()).isFalse();

    }


    private List<Player.EventListener> getCurrentEventListener() {
        ArgumentCaptor<Player.EventListener> listenerArgumentCaptor;
        listenerArgumentCaptor = ArgumentCaptor.forClass(Player.EventListener.class);
        verify(mockPlayer, atLeastOnce()).addListener(listenerArgumentCaptor.capture());
        return listenerArgumentCaptor.getAllValues();
    }
}
