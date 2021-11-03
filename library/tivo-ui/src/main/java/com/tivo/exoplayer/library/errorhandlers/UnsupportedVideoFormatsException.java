package com.tivo.exoplayer.library.errorhandlers;

import java.util.List;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RendererCapabilities;

/**
 * Reported via the {@link PlayerErrorHandlerListener} interface as as the cause for an
 * {@link ExoPlaybackException} of type {@link ExoPlaybackException#TYPE_RENDERER} if the
 * track selection results in a combination of video tracks that are likely un-playable.
 *
 * That is, for example, all of the video tracks report
 * {@link RendererCapabilities.FormatSupport#FORMAT_UNSUPPORTED_DRM} ExoPlayer may attempt to
 * start playback before this error is reported.  The handler can call {@link Player#stop()}
 * to abort playback.
 */
public class UnsupportedVideoFormatsException extends Exception {

  public static class UnsupportedTrack {
    public final int trackIndex;
    public final Format format;
    public final @RendererCapabilities.FormatSupport int formatSupport;

    public UnsupportedTrack(int trackIndex, Format format, int formatSupport) {
      this.trackIndex = trackIndex;
      this.format = format;
      this.formatSupport = formatSupport;
    }
  }

  public final List<UnsupportedTrack> unsupportedTrackList;


  public UnsupportedVideoFormatsException(List<UnsupportedTrack> unsupportedTrackList) {
    this.unsupportedTrackList = unsupportedTrackList;
  }

}
