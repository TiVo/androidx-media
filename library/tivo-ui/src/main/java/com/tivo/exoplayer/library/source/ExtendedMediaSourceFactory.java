package com.tivo.exoplayer.library.source;

import static com.google.android.exoplayer2.C.WIDEVINE_UUID;
import static com.tivo.exoplayer.library.DrmInfo.TIVO_CRYPT_UUID;
import static com.tivo.exoplayer.library.DrmInfo.VCAS_UUID;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManagerProvider;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParserFactory;
import com.google.android.exoplayer2.ui.AdViewProvider;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;
import com.tivo.exoplayer.library.SourceFactoriesCreated;
import com.tivo.exoplayer.library.TivoCryptDrmInfo;
import com.tivo.exoplayer.library.VcasDrmInfo;
import com.tivo.exoplayer.tivocrypt.TivoCryptDataSourceFactory;
import java.io.File;
import java.lang.reflect.Constructor;

/**
 * ExoPlayer moved to making the {@link MediaItem} front and center as the host object for
 * playback start API, e.g. {@link com.google.android.exoplayer2.Player#addMediaItem(MediaItem)}.
 *
 * This was apparently done to support creating a playlist of MediaItems, see the
 * {@see https://developer.android.com/media/media3/exoplayer/playlists} for more info.
 *
 * The result is the actual {@link MediaSource} object is exposed less.
 *
 * This class "extends" (replaces) the {@link com.google.android.exoplayer2.source.DefaultMediaSourceFactory}
 * with this implementation that supports VCAS and TivoCrypt DRM.  This deprecates the MediaSourceLifeCycle API's
 * and implementation
 * ...
 */
public class ExtendedMediaSourceFactory implements MediaSourceFactory {
  private static final String TAG = "ExtendedMediaSourceFactory";
  private final Context context;
  private final HlsPlaylistParserFactory hlsPlaylistParserFactory;
  private SourceFactoriesCreated factoriesCreated = new SourceFactoriesCreated() {};
  @Nullable private DrmSessionManagerProvider drmSessionManagerProvider;
  private LoadErrorHandlingPolicy mainMediaSourceLoadErrorHandlerPolicy;
  private HttpDataSource.Factory drmHttpDataSourceFactory;
  private String userAgentPrefix = "TiVoExoPlayer";
  @Nullable private DefaultMediaSourceFactory.AdsLoaderProvider adsLoaderProvider;
  @Nullable private AdViewProvider adViewProvider;

  /**
   * Provides {@link AdsLoader} instances for media items that have {@link
   * MediaItem.PlaybackProperties#adsConfiguration ad tag URIs}.
   */
  public interface AdsLoaderProvider {

    /**
     * Returns an {@link AdsLoader} for the given {@link
     * MediaItem.PlaybackProperties#adsConfiguration ads configuration}, or {@code null} if no ads
     * loader is available for the given ads configuration.
     *
     * <p>This method is called each time a {@link MediaSource} is created from a {@link MediaItem}
     * that defines an {@link MediaItem.PlaybackProperties#adsConfiguration ads configuration}.
     */
    @Nullable
    AdsLoader getAdsLoader(MediaItem.AdsConfiguration adsConfiguration);
  }

  public ExtendedMediaSourceFactory(Context context, HlsPlaylistParserFactory hlsPlaylistParserFactory) {
    this.context = context;
    this.hlsPlaylistParserFactory = hlsPlaylistParserFactory;
  }

  @NonNull
  @Override
  public ExtendedMediaSourceFactory setDrmSessionManagerProvider(@Nullable DrmSessionManagerProvider drmSessionManagerProvider) {
    this.drmSessionManagerProvider = drmSessionManagerProvider;
    return this;
  }

  /**
   * Allows client to be notified (and possibly modify) when the ExoPlayer factory objects and MediaItem
   * after they are created and filled in, see the {@link SourceFactoriesCreated} for details.
   *
   * @param factoriesCreated callback interface
   * @return this builder for chaining
   */
  public ExtendedMediaSourceFactory setSourceFactoriesCreatedCallback(SourceFactoriesCreated factoriesCreated) {
    this.factoriesCreated = factoriesCreated;
    return this;
  }

  @NonNull
  @Override
  public ExtendedMediaSourceFactory setDrmSessionManager(@Nullable DrmSessionManager drmSessionManager) {
    throw new UnsupportedOperationException("use setDrmSessionManagerProvider()");
  }

  @NonNull
  @Override
  public ExtendedMediaSourceFactory setDrmHttpDataSourceFactory(@Nullable HttpDataSource.Factory drmHttpDataSourceFactory) {
    this.drmHttpDataSourceFactory = drmHttpDataSourceFactory;
    return this;
  }

  @NonNull
  @Override
  public ExtendedMediaSourceFactory setDrmUserAgent(@Nullable String userAgent) {
    throw new UnsupportedOperationException("use SourceFactoriesCreated callback for this");

  }

  @NonNull
  @Override
  public ExtendedMediaSourceFactory setLoadErrorHandlingPolicy(@Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
    mainMediaSourceLoadErrorHandlerPolicy = loadErrorHandlingPolicy;
    return this;
  }

  /**
   * Sets the {@link ExtendedMediaSourceFactory.AdsLoaderProvider} that provides {@link AdsLoader} instances for media items
   * that have {@link MediaItem.PlaybackProperties#adsConfiguration ads configurations}.
   *
   * @param adsLoaderProvider A provider for {@link AdsLoader} instances.
   * @return This factory, for convenience.
   */
  public ExtendedMediaSourceFactory setAdsLoaderProvider(
      @Nullable DefaultMediaSourceFactory.AdsLoaderProvider adsLoaderProvider) {
    this.adsLoaderProvider = adsLoaderProvider;
    return this;
  }

  /**
   * Sets the {@link AdViewProvider} that provides information about views for the ad playback UI.
   *
   * @param adViewProvider A provider for {@link AdsLoader} instances.
   * @return This factory, for convenience.
   */
  public ExtendedMediaSourceFactory setAdViewProvider(@Nullable AdViewProvider adViewProvider) {
    this.adViewProvider = adViewProvider;
    return this;
  }

  @NonNull
  @Override
  public int[] getSupportedTypes() {
    return new int[] {C.TYPE_DASH, C.TYPE_HLS, C.TYPE_OTHER};
  }

  /**
   * Call this when the Android {@link android.app.Activity} is stopped to release
   * anything held by this object.
   */
  public void releaseResources() {

  }

  private MediaSource maybeWrapWithAdsMediaSource(MediaItem mediaItem, MediaSource mediaSource) {
    Assertions.checkNotNull(mediaItem.playbackProperties);
    @Nullable
    MediaItem.AdsConfiguration adsConfiguration = mediaItem.playbackProperties.adsConfiguration;
    if (adsConfiguration == null) {
      return mediaSource;
    }
    DefaultMediaSourceFactory.AdsLoaderProvider adsLoaderProvider = this.adsLoaderProvider;
    AdViewProvider adViewProvider = this.adViewProvider;
    if (adsLoaderProvider == null || adViewProvider == null) {
      Log.w(
          TAG,
          "Playing media without ads. Configure ad support by calling setAdsLoaderProvider and"
              + " setAdViewProvider.");
      return mediaSource;
    }
    @Nullable AdsLoader adsLoader = adsLoaderProvider.getAdsLoader(adsConfiguration);
    if (adsLoader == null) {
      Log.w(TAG, "Playing media without ads, as no AdsLoader was provided.");
      return mediaSource;
    }
    return new AdsMediaSource(
        mediaSource,
        new DataSpec(adsConfiguration.adTagUri),
        /* adsId= */ adsConfiguration.adsId != null
        ? adsConfiguration.adsId
        : ImmutableList.of(
            mediaItem.mediaId, mediaItem.playbackProperties.uri, adsConfiguration.adTagUri),
        /* adMediaSourceFactory= */ this,
        adsLoader,
        adViewProvider);
  }

  @NonNull
  @SuppressLint("SwitchIntDef")
  @Override
  public MediaSource createMediaSource(MediaItem mediaItem) {
    Assertions.checkNotNull(mediaItem.playbackProperties);
    @C.ContentType
    int type =
        Util.inferContentTypeForUriAndMimeType(
            mediaItem.playbackProperties.uri, mediaItem.playbackProperties.mimeType);

    DataSource.Factory dataSourceFactory = buildDataSourceFactory(mediaItem);
    @Nullable MediaSourceFactory factory = null;
    switch (type) {
      case C.TYPE_HLS:
        factory = new HlsMediaSource.Factory(dataSourceFactory)
            .setPlaylistParserFactory(hlsPlaylistParserFactory);
        break;
      case C.TYPE_OTHER:
        factory =  new ProgressiveMediaSource.Factory(dataSourceFactory);
        break;
      case C.TYPE_DASH:
        factory = new DashMediaSource.Factory(dataSourceFactory);
        break;
    }
    Assertions.checkNotNull(factory, "getSupportedType() should have prevented this!");
    assert factory != null;

    mediaItem = factoriesCreated.factoriesCreated(type, mediaItem, factory);
    if (drmSessionManagerProvider == null) {
      DefaultDrmSessionManagerProvider defaultDrmSessionManagerProvider = new DefaultDrmSessionManagerProvider();
      drmSessionManagerProvider = defaultDrmSessionManagerProvider;
      defaultDrmSessionManagerProvider.setDrmHttpDataSourceFactory(drmHttpDataSourceFactory);
//      defaultDrmSessionManagerProvider.setLoadErrorHandlingPolicy(new DrmLoadErrorHandlerPolicy());
    }
    factory.setDrmSessionManagerProvider(drmSessionManagerProvider);
    MediaSource mediaSource = factory.createMediaSource(mediaItem);
    return maybeWrapWithAdsMediaSource(mediaItem, mediaSource);
  }

  /**
   * Construct and return the Factory class for {@link DataSource} objects.
   *
   * The DataSource is used to build a {@link MediaSource} for loading and playing a
   * URL.
   *
   * @param mediaItem - has DRM specific metadata to create DRM aware data source factory
   *
   * @return factory to produce DataSource objects.
   */
  private DataSource.Factory buildDataSourceFactory(MediaItem mediaItem) {
    HttpDataSource.Factory upstreamFactory =
        new DefaultHttpDataSource.Factory()
            .setUserAgent(userAgentPrefix + " - [" + SimpleExoPlayerFactory.VERSION_INFO + "]");
    factoriesCreated.upstreamDataSourceFactoryCreated(upstreamFactory);
    DataSource.Factory factory = null;

    @Nullable MediaItem.PlaybackProperties properties = mediaItem.playbackProperties;
    @Nullable MediaItem.DrmConfiguration drmConfiguration = properties == null
        ? null
        : properties.drmConfiguration;

    if (drmConfiguration == null || WIDEVINE_UUID.equals(drmConfiguration.uuid)) {
      factory = new DefaultDataSourceFactory(context, upstreamFactory);
    } else if (VCAS_UUID.equals(drmConfiguration.uuid)) {

      VcasDrmInfo info = (VcasDrmInfo) mediaItem.playbackProperties.tag;
      assert info != null;
      if (Build.VERSION.SDK_INT >= 22) {
        // Create the singleton "Verimatrix DRM" data source factory
        Class<?> clazz =
                null;
        try {
          clazz = Class.forName("com.tivo.exoplayer.vcas.VerimatrixDataSourceFactory");
          Constructor<?> constructor =
                  clazz.getConstructor(
                          DataSource.Factory.class,
                          String.class, String.class,
                          String.class, String.class,
                          String.class, boolean.class);

          String filesDir = info.getStoreDir();
          File folder = new File(filesDir);
          if (!folder.exists()) {
            folder.mkdirs();
          }

          String sourceDir = context.getApplicationInfo().sourceDir;
          // AAB install will place the libs in split directory. Look for
          // matching directory with pattern armeabi. We are exporting
          // armeabi_v7a only
          String[] splitSourceDirs = context.getApplicationInfo().splitSourceDirs;
          if (splitSourceDirs != null) {
              for (String splitSourceDir : splitSourceDirs) {
                  if (splitSourceDir.contains(".armeabi")) {
                      sourceDir = splitSourceDir;
                  }
              }
          }

          factory = (DataSource.Factory) constructor
                  .newInstance(upstreamFactory,
                          filesDir,
                          context.getApplicationInfo().nativeLibraryDir,
                          sourceDir,
                          info.getCaId(),
                          info.getBootAddr(),
                          info.isDebugOn());
        } catch (ClassNotFoundException e) {
          Log.e(TAG, "Couldn't instantiate VCAS factory");
          throw new RuntimeException("error creating VerimatrixDataSourceFactory object", e);
        } catch (NoSuchMethodException e) {
          Log.e(TAG, "No matching VCAS constructor");
          throw new RuntimeException("error creating VerimatrixDataSourceFactory object", e);
        } catch (Exception e) {
          Log.e(TAG, "VCAS instantiation failed: ", e);
          throw new RuntimeException("error creating VerimatrixDataSourceFactory object", e);
        }
      }
    } else if (TIVO_CRYPT_UUID.equals(drmConfiguration.uuid)) {
      TivoCryptDrmInfo info = (TivoCryptDrmInfo) mediaItem.playbackProperties.tag;
      assert info != null;

      factory = new TivoCryptDataSourceFactory(upstreamFactory, info.getWbKey(), info.getContext(), info.getDeviceKey());
    } else {
      throw new RuntimeException("no support for UUID " + drmConfiguration.uuid);
    }
    return factory;
  }

}
