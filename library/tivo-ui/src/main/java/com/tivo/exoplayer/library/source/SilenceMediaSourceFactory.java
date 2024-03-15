package com.tivo.exoplayer.library.source;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.SilenceMediaSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;

/**
 * Class to produce {@link com.google.android.exoplayer2.source.SilenceMediaSource}.  Typical use
 * case would be the empty URI (@link {@link android.net.Uri#EMPTY} as a sentinel that this MediaSource
 * should be created.
 */
public class SilenceMediaSourceFactory implements MediaSourceFactory {

  private final long durationUs;

  /**
   * Create this factory to produce silence of the indicated duration.
   * @param durationUs the duration of silence to create.
   */
  public SilenceMediaSourceFactory(long durationUs) {
    this.durationUs = durationUs;
  }

  // Implement MediaSourceFactory, note there are no network fetches or DRM so ignore most of the API
  @NonNull
  @Override
  public MediaSourceFactory setDrmSessionManagerProvider(@Nullable DrmSessionManagerProvider drmSessionManagerProvider) {
    return this;
  }

  @NonNull
  @Override
  public MediaSourceFactory setDrmSessionManager(@Nullable DrmSessionManager drmSessionManager) {
    return this;
  }

  @NonNull
  @Override
  public MediaSourceFactory setDrmHttpDataSourceFactory(@Nullable HttpDataSource.Factory drmHttpDataSourceFactory) {
    return this;
  }

  @Override
  public MediaSourceFactory setDrmUserAgent(@Nullable String userAgent) {
    return this;
  }

  @Override
  public MediaSourceFactory setLoadErrorHandlingPolicy(@Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
    return this;
  }

  /**
   * Content is audio only, like progressive media
   * @return int[] with TYPE_OTHER
   */
  @Override
  public int[] getSupportedTypes() {
    return new int[] {C.TYPE_OTHER};
  }

  @Override
  public MediaSource createMediaSource(MediaItem mediaItem) {
    return new SilenceMediaSource(durationUs);
  }
}
