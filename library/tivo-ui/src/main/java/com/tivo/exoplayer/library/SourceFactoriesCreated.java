package com.tivo.exoplayer.library;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;

/**
 * Callback interface can optionally be passed to the {@link SimpleExoPlayerFactory.Builder} in order
 * to be notified when a {@link MediaSourceFactory} is created and mutate it before it is used
 * to build a {@link MediaSource} and with the {@link MediaItem.Builder} before it is used to build
 * the {@link MediaItem}
 *
 * Note, ExoPlayer is moving away from exposing the {@link MediaSource} itself directly to clients in
 * favor of API's that use {@link MediaItem}.  The {@link MediaSourceFactory} will very likely remain
 * exposed for a while as it can be used to control features specific to the media content type.  Much of
 * this is moving to the {@link MediaItem} itself.  Exposing both of these factories future proofs the
 * interface, at some point the MediaSource parts may go away
 */
public interface SourceFactoriesCreated {

  /**
   * Called with the "factory" and "itemBuilder" just before they are used.  Either of both of these
   * can be inspected and altered, with the obvious limitation that the content type is fixed to
   * what is reported as "type"
   *
   * @param type contentType of the item/factory.  This is immutable and may be used to determine the
   *             class of the "factory" and "item"
   * @param itemBuilder builder for item with mimeType matching the "type" parameter
   * @param factory MediaSourceFactory subclass matching the type, e.g. {@link C#TYPE_HLS} will
   *                be an {@link HlsMediaSource.Factory}
   */
  default void factoriesCreated(@C.ContentType int type, MediaItem.Builder itemBuilder, MediaSourceFactory factory) {}
}
