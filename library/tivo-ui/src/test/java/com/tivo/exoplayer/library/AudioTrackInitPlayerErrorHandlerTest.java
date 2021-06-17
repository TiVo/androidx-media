package com.tivo.exoplayer.library;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioSink;
import static com.google.android.exoplayer2.Player.PLAYBACK_SUPPRESSION_REASON_NONE;
import static com.google.android.exoplayer2.Player.STATE_ENDED;
import static com.google.android.exoplayer2.Player.STATE_READY;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class AudioTrackInitPlayerErrorHandlerTest {


    @Mock
    private PlayerErrorRecoverable mockPlayerErrorRecoverable;

    @Mock
    private SimpleExoPlayer mockPlayer;

    @Before
    public void setupMocks() {
        MockitoAnnotations.initMocks(this);
        when(mockPlayerErrorRecoverable.getCurrentPlayer()).thenReturn(mockPlayer);
        when(mockPlayer.getPlaybackState()).thenReturn(STATE_READY);
        when(mockPlayer.getPlaybackSuppressionReason()).thenReturn(PLAYBACK_SUPPRESSION_REASON_NONE);
        when(mockPlayer.isPlaying()).thenReturn(true);
    }

    @Test
    public void testHandleOneErrorOccurrence() {
        AudioTrackInitPlayerErrorHandler testee = new AudioTrackInitPlayerErrorHandler(mockPlayerErrorRecoverable);

        ExoPlaybackException error = createAudioTrackError();

        when(mockPlayerErrorRecoverable.isTunnelingMode()).thenReturn(false);

        setPlayingStopped();
        boolean value = testee.recoverFrom(error);
        verify(mockPlayerErrorRecoverable, atMost(1)).retryPlayback();

        assertThat(value).isTrue();
        assertThat(testee.checkRecoveryCompleted()).isFalse();
        assertThat(testee.isRecoveryInProgress()).isTrue();
        assertThat(testee.isRecoveryFailed()).isFalse();
    }

    @Test
    public void testRecoverAfterOneErrorOccurrence() {
        AudioTrackInitPlayerErrorHandler testee = new AudioTrackInitPlayerErrorHandler(mockPlayerErrorRecoverable);
        ExoPlaybackException error = createAudioTrackError();

        when(mockPlayerErrorRecoverable.isTunnelingMode()).thenReturn(false);

        boolean value = testee.recoverFrom(error);
        assertThat(value).isTrue();
        assertThat(testee.checkRecoveryCompleted()).isTrue();
        assertThat(testee.isRecoveryInProgress()).isFalse();
        assertThat(testee.isRecoveryFailed()).isFalse();
    }

    private void setPlayingStopped() {
        when(mockPlayerErrorRecoverable.getCurrentPlayer()).thenReturn(mockPlayer);
        when(mockPlayer.getPlaybackSuppressionReason()).thenReturn(PLAYBACK_SUPPRESSION_REASON_NONE);
        when(mockPlayer.getPlaybackState()).thenReturn(STATE_ENDED);
    }

    private ExoPlaybackException createAudioTrackError() {
        AudioSink.InitializationException cause = new AudioSink.InitializationException(0, 32, 6, 0);
        Format format = Format.createAudioSampleFormat(
                null,
                "audio/ac3",
                null,
                0,
                0,
                0,
                0,
                null,
                null,
                0,
                null
        );
        ExoPlaybackException error = ExoPlaybackException.createForRenderer(cause, 0, format, RendererCapabilities.FORMAT_HANDLED);
        return error;
    }
}
