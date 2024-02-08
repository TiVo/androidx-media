package com.tivo.exoplayer.library;


import android.content.Context;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.drm.MediaDrmCallback;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.UnrecognizedInputFormatException;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParserFactory;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Log;

import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.tivo.exoplayer.tivocrypt.TivoCryptDataSourceFactory;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.Map;

/**
 * Manages creation and the lifecycle of playback of an ExoPlayer {@link MediaSource}
 *
 * The ExoPlayer MediaSource connects an ExoPlayer playback session to the data source (for
 * HLS this is an HTTP URL).
 *
 * The {@link SimpleExoPlayer#prepare(MediaSource)} starts the mediasource loading, or reloads it in
 * the event of a recoverable playback error (eg {@link com.google.android.exoplayer2.source.BehindLiveWindowException}
 *
 * This object manages the life-cycle of playback for a given URL
 */
@Deprecated
public class DefaultMediaSourceLifeCycle implements MediaSourceLifeCycle, Player.EventListener {

  private static final String TAG = "DefaultMediaSourceLifeCycle";

  @Nullable private SimpleExoPlayer player;
  private final Context context;
  @Nullable private MediaSource currentMediaSource;
  @NonNull private final HlsPlaylistParserFactory hlsPlaylistParserFactory;

  @Nullable private MediaSourceEventCallback callback;

  SourceFactoriesCreated factoriesCreated = new SourceFactoriesCreated() {};

  private boolean deliveredInitialTimelineChange;
  String userAgentPrefix;

  /**
   * Construct the default implementation of {@link MediaSourceLifeCycle}
   *
   * @param context - android context
   * @param player - SimpleExoPlayer to build mediasources for
   * @param hlsPlaylistParserFactory - used for HLS data source only
   */
  public DefaultMediaSourceLifeCycle(Context context, SimpleExoPlayer player,
      HlsPlaylistParserFactory hlsPlaylistParserFactory) {
    this.player = player;
    this.context = context;
    this.hlsPlaylistParserFactory = hlsPlaylistParserFactory;
    player.addListener(this);
  }

  @Override
  public void playUrl(Uri uri, long startPositionUs, DrmInfo drmInfo, boolean enableChunkless) throws UnrecognizedInputFormatException {
    Log.d(TAG, "play URL " + uri);
    assert player != null;

    playUrlInternal(uri, startPositionUs, drmInfo);
    player.setPlayWhenReady(true);
    player.prepare();
  }

  @Override
  public void playUrl(Uri uri, long startPositionMs, boolean startPlaying, DrmInfo drmInfo) throws UnrecognizedInputFormatException {
    Log.d(TAG, "play URL, startAt: " + startPositionMs + ", startPlaying: " + startPlaying + ", uri: " + uri);
    assert player != null;
    playUrlInternal(uri, startPositionMs, drmInfo);
    player.setPlayWhenReady(startPlaying);
    player.prepare();
  }

  @Override
  public void playUrl(Uri uri, long startPositionMs, DrmInfo drmInfo) throws UnrecognizedInputFormatException {
    Log.d(TAG, "play URL, startAt: " + startPositionMs + ", uri: " + uri);
    assert player != null;
    playUrlInternal(uri, startPositionMs, drmInfo);
    player.prepare();
  }


  private void playUrlInternal(Uri uri, long startPositionUs, DrmInfo drmInfo) throws UnrecognizedInputFormatException {
    MediaSource mediaSource = buildMediaSource(uri, drmInfo, buildDataSourceFactory(drmInfo), false);
    assert player != null;
    int currentState = player.getPlaybackState();
    if (currentState == Player.STATE_BUFFERING || currentState == Player.STATE_READY) {
      Log.d(TAG, "Player not idle, stopping playback with player in state: " + currentState);
      player.stop(true);   // stop and reset position and state to idle
    }
    currentMediaSource = mediaSource;
    deliveredInitialTimelineChange = false;
    if (startPositionUs == C.POSITION_UNSET) {
      player.setMediaSource(currentMediaSource);
    } else {
      // TODO - work around bug, https://github.com/google/ExoPlayer/issues/7975 min is non-zero
      startPositionUs = Math.max(1, startPositionUs);
      player.setMediaSource(currentMediaSource, startPositionUs);
    }
  }

  @Override
  public void setMediaSourceEventCallback(@Nullable MediaSourceEventCallback callback) {
    this.callback = callback;
  }

  /**
   * Construct and return the Factory class for {@link DataSource} objects.
   *
   * The DataSource is used to build a {@link MediaSource} for loading and playing a
   * URL.
   *
   * @param drmInfo DRM specific metadata to create DRM aware data source factory
   *
   * @return factory to produce DataSource objects.
   */
  protected DataSource.Factory buildDataSourceFactory(DrmInfo drmInfo) {
    HttpDataSource.Factory upstreamFactory = createUpstreamDataSourceFactory();

    // Currently Verimatrix ViewRight CAS is plugged in at the DataSource level,
    // If the library can provide the decrypting key then decryption can be done at
    // MediaSource level using DrmSessionManager with Clearkey DRM scheme.
    if (drmInfo instanceof VcasDrmInfo) {
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

          String filesDir = ((VcasDrmInfo)drmInfo).getStoreDir();
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

          DataSource.Factory vfactory = (DataSource.Factory) constructor
                  .newInstance(upstreamFactory,
                          filesDir,
                          context.getApplicationInfo().nativeLibraryDir,
                          sourceDir,
                          ((VcasDrmInfo)drmInfo).getCaId(),
                          ((VcasDrmInfo)drmInfo).getBootAddr(),
                          ((VcasDrmInfo)drmInfo).isDebugOn());
          return vfactory;
        } catch (ClassNotFoundException e) {
          Log.e(TAG, "Couldn't instantiate VCAS factory");
        } catch (NoSuchMethodException e) {
          Log.e(TAG, "No matching VCAS constructor");
        } catch (Exception e) {
          Log.e(TAG, "VCAS instantiation failed: ", e);
        }
      }
      return null;
    } else if (drmInfo instanceof TivoCryptDrmInfo) {

      return new TivoCryptDataSourceFactory(upstreamFactory, ((TivoCryptDrmInfo) drmInfo).getWbKey(), ((TivoCryptDrmInfo) drmInfo).getContext(), ((TivoCryptDrmInfo) drmInfo).getDeviceKey());
    }

    return new DefaultDataSourceFactory(context, upstreamFactory);
  }

  /**
   * The {@link DataSource} objects are linked in a delegate chain, allowing the downstream DataSource
   * objects to modify data from or URL's passed to the ultimate upstream DataSource (which, in our
   * use case is always some HttpDataSource fetching contents for a URL).
   *
   * This method creates that furthest upstream {@link HttpDataSource.Factory} and calls the
   * {@link SourceFactoriesCreated#upstreamDataSourceFactoryCreated(DataSource.Factory)} method
   * to allow clients to call methods on it.
   *
   * @return an HttpDataSource.Factory that will be used at the base of the chain of DataSource's
   */
  private HttpDataSource.Factory createUpstreamDataSourceFactory() {
    HttpDataSource.Factory upstreamFactory =
        new DefaultHttpDataSource.Factory()
            .setUserAgent(userAgentPrefix + " - [" + SimpleExoPlayerFactory.VERSION_INFO + "]");
    factoriesCreated.upstreamDataSourceFactoryCreated(upstreamFactory);
    return upstreamFactory;
  }

  public static DataSource.Factory buildTivoCryptDataSourceFactory(TivoCryptDrmInfo drmInfo, HttpDataSource.Factory upstreamFactory) {

    return new TivoCryptDataSourceFactory(upstreamFactory, drmInfo.getWbKey(), drmInfo.getContext(), drmInfo.getDeviceKey());
  }

  public static DataSource.Factory buildVcasDataSourceFactory(Context context, VcasDrmInfo vDrmInfo,
      HttpDataSource.Factory upstreamFactory) {
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

        String filesDir = vDrmInfo.getStoreDir();
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

        DataSource.Factory vfactory = (DataSource.Factory) constructor
                .newInstance(upstreamFactory,
                        filesDir,
                        context.getApplicationInfo().nativeLibraryDir,
                        sourceDir,
                        vDrmInfo.getCaId(),
                        vDrmInfo.getBootAddr(),
                        vDrmInfo.isDebugOn());
        return vfactory;
      } catch (ClassNotFoundException e) {
        Log.e(TAG, "Couldn't instantiate VCAS factory");
      } catch (NoSuchMethodException e) {
        Log.e(TAG, "No matching VCAS constructor");
      } catch (Exception e) {
        Log.e(TAG, "VCAS instantiation failed: ", e);
      }
    }
    return null;
  }


  /**
   * Built the MediaSource for the Uri (assumed for now to be HLS)
   *
   * The player glue layer should save this for error recovery (if re-prepare is required)
   *
   * @param uri - URL for the HLS master playlist
   * @param drmInfo DRM specific metadata, {@link DrmInfo}, to create DRM aware data source factory and/or DrmSessionManager
   * @param dataSourceFactory - factory to create a {@link DataSource} to load data from the HLS resouces
   * @param enableChunkless - if true HLS will prepare 'chunkless'
   *          See <a href="https://medium.com/google-exoplayer/faster-hls-preparation-f6611aa15ea6">Faster HLS Prepare</a>
   * @return an {@link MediaSource} ready to pass to {@link com.google.android.exoplayer2.SimpleExoPlayer#prepare(MediaSource)}
   * @throws UnrecognizedInputFormatException - if the URI is not in a supported container format.
   */
  protected MediaSource buildMediaSource(Uri uri, DrmInfo drmInfo, DataSource.Factory dataSourceFactory,
      boolean enableChunkless) throws UnrecognizedInputFormatException {

    MediaSourceFactory factory = null;
    MediaItem.Builder itemBuilder = new MediaItem.Builder();
    itemBuilder.setUri(uri);
    @C.ContentType int type = Util.inferContentType(uri);
    switch (type) {
      case C.TYPE_HLS:
        itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8);
        factory = new HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(enableChunkless)
            .setPlaylistParserFactory(hlsPlaylistParserFactory);
        break;
      case C.TYPE_OTHER:
        factory =  new ProgressiveMediaSource.Factory(dataSourceFactory);
        break;
      case C.TYPE_DASH:
        itemBuilder.setMimeType(MimeTypes.APPLICATION_MPD);
        factory = new DashMediaSource.Factory(dataSourceFactory);
        break;

      case C.TYPE_SS: // TODO - If we want to support SmoothStreaming, add SSDataSource dependency
        itemBuilder.setMimeType(MimeTypes.APPLICATION_SS);
        throw new UnrecognizedInputFormatException("Source is not a supported container format (type: " + type + ")", uri);
    }

    if (Build.VERSION.SDK_INT >= 18) {
      if (drmInfo instanceof WidevineDrmInfo) {
        WidevineDrmInfo wDrmInfo = (WidevineDrmInfo) drmInfo;
        // setup DrmSessionManager

        MediaDrmCallback mediaDrmCallback =
                createMediaDrmCallback(wDrmInfo.getProxyUrl(), wDrmInfo.getKeyRequestProps());
        DrmSessionManager drmSessionManager =
                new DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .setMultiSession(wDrmInfo.isMultiSessionEnabled())
                        .build(mediaDrmCallback);

        // TODO - the path ExoPlayer is going (that deprecates setDrmSessionManager()) is the MediaItem tells
        // TODO - what DRM to use, that ultimately renders this whole class obsolete.  For now, we build the MediaSourceFactory
        // TODO - each time so every mediaItem needs Widevine DRM
        factory.setDrmSessionManagerProvider(mediaItem -> drmSessionManager);
        itemBuilder.setDrmUuid(C.WIDEVINE_UUID);
        itemBuilder.setDrmLicenseUri(wDrmInfo.getProxyUrl());

        Map<String, String> headers = wDrmInfo.getKeyRequestProps();
        itemBuilder.setDrmLicenseRequestHeaders(headers);
      }
    }
    factoriesCreated.factoriesCreated(type, itemBuilder, factory);

    return factory.createMediaSource(itemBuilder.build());
  }

  private HttpMediaDrmCallback createMediaDrmCallback(
          String licenseUrl, Map<String, String> keyRequestProperties) {
    HttpDataSource.Factory licenseDataSourceFactory = createUpstreamDataSourceFactory();
    HttpMediaDrmCallback drmCallback =
            new HttpMediaDrmCallback(licenseUrl, licenseDataSourceFactory);
    for( Map.Entry<String,String> entry : keyRequestProperties.entrySet() ) {
      drmCallback.setKeyRequestProperty(entry.getKey(), entry.getValue());
    }
    return drmCallback;
  }

  @Override
  public void releaseResources() {
    currentMediaSource = null;
    if (player != null) {
      player.removeListener(this);
    }
    player = null;
  }


  /**
   * After the player reads the initial M3u8 and parses it, the timeline is created.
   * Report the first such of these events, following a {@link MediaSource} change
   * (via {@link MediaSourceLifeCycle#playUrl(Uri, long, boolean, DrmInfo)} (Uri)} call
   * to any {@link MediaSourceEventCallback} listener
   *
   * @param timeline The current timeline
   * @param reason The reason for the timeline change.
   */
  @Override
  public void onTimelineChanged(Timeline timeline, @Player.TimelineChangeReason int reason) {
    if (timeline.isEmpty()) {
      Log.d(TAG, "onTimelineChanged() - timeline empty. reason: " + reason);
    } else {
      Timeline.Window window = timeline.getWindow(0, new Timeline.Window());
      Log.d(TAG, "onTimelineChanged() - reason: " + reason + " timeline duration: " + C.usToMs(window.durationUs)
              + " startPosition: " + C.usToMs(window.defaultPositionUs) + " startTime: " + window.windowStartTimeMs);
    }

    switch (reason) {
      case Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED:
        Log.d(TAG, "Playlist for timeline has changed, reset to deliver initial mediasource event");
        deliveredInitialTimelineChange = false;
        break;

      case Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE:
        if (! deliveredInitialTimelineChange && callback != null) {
          Log.d(TAG, "Initial source update, trigger mediaSourcePrepared() callback");
          callback.mediaSourcePrepared(currentMediaSource, player);
          deliveredInitialTimelineChange = true;
        }
    }
  }
}
