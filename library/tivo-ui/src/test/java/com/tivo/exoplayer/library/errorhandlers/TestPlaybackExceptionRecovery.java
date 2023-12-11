package com.tivo.exoplayer.library.errorhandlers;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistTracker;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import java.net.ConnectException;
import java.net.SocketException;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_UNSPECIFIED;
import static com.google.common.truth.Truth.assertThat;

@RunWith(AndroidJUnit4.class)
public class TestPlaybackExceptionRecovery {

  @Test
  public void isSourceErrorOfType_testCauseExtends() {
    DataSpec dataSpec = new DataSpec(Uri.EMPTY);
    HttpDataSource.HttpDataSourceException error =
        HttpDataSource.HttpDataSourceException.createForIOException(
            new ConnectException("failed"),
            dataSpec,
            HttpDataSource.HttpDataSourceException.TYPE_OPEN);
    PlaybackException exception = ExoPlaybackException.createForSource(error,  error.reason);
    assertThat(PlaybackExceptionRecovery.isSourceErrorOfType(exception , SocketException.class)).isTrue();
  }

  @Test
  public void isPlaylistStuckException() {
    PlaybackException error =
        ExoPlaybackException.createForSource(
            new HlsPlaylistTracker.PlaylistStuckException(Uri.EMPTY), ERROR_CODE_IO_UNSPECIFIED);
    assertThat(PlaybackExceptionRecovery.isPlaylistStuck(error)).isTrue();
  }
}
