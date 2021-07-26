package com.tivo.exoplayer.library;


import android.content.Context;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
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
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistParserFactory;
import com.google.android.exoplayer2.trickplay.hls.DualModeHlsPlaylistParserFactory;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Log;

import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.tivo.exoplayer.tivocrypt.TivoCryptDataSourceFactory;

import java.io.File;
import java.lang.reflect.Constructor;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

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
public class DefaultMediaSourceLifeCycle implements MediaSourceLifeCycle, Player.EventListener {

  private static final String TAG = "DefaultMediaSourceLifeCycle";

  protected final SimpleExoPlayer player;
  protected final Context context;
  protected @MonotonicNonNull MediaSource currentMediaSource;

  @Nullable
  private MediaSourceEventCallback callback;

  private boolean deliveredInitialTimelineChange;

  /**
   * Construct the default implementation of {@link MediaSourceLifeCycle}
   *
   * @param player - SimpleExoPlayer to build mediasources for
   * @param context - android context
   */
  public DefaultMediaSourceLifeCycle(SimpleExoPlayer player, Context context) {
    this.player = player;
    this.context = context;

    player.addListener(this);
  }

  @Override
  public void playUrl(Uri uri, long startPositionUs, DrmInfo drmInfo, boolean enableChunkless) throws UnrecognizedInputFormatException {
    Log.d("ExoPlayer", "play URL " + uri);

    MediaSource mediaSource = buildMediaSource(uri, drmInfo, buildDataSourceFactory(drmInfo), enableChunkless);
    int currentState = player.getPlaybackState();
    if (currentState == Player.STATE_BUFFERING || currentState == Player.STATE_READY) {
      Log.d(TAG, "Player not idle, stopping playback with player in state: " + currentState);
      player.stop(true);   // stop and reset position and state to idle
    }
    currentMediaSource = mediaSource;
    deliveredInitialTimelineChange = false;
    player.setPlayWhenReady(true);
    if (startPositionUs == C.POSITION_UNSET) {
      player.setMediaSource(currentMediaSource);
    } else {
      // TODO - work around bug, https://github.com/google/ExoPlayer/issues/7975 min is non-zero
      startPositionUs = Math.max(1, startPositionUs);
      player.setMediaSource(currentMediaSource, startPositionUs);
    }
    player.prepare();
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
    String userAgent = getUserAgentPrefix();

    userAgent += "-" + ExoPlayerLibraryInfo.VERSION;

    HttpDataSource.Factory upstreamFactory = new DefaultHttpDataSourceFactory(userAgent);

    // Currently Verimatrix ViewRight CAS is plugged in at the DataSource level,
    // If the library can provide the decrypting key then decryption can be done at
    // MediaSource level using DrmSessionManager with Clearkey DRM scheme.
    if (drmInfo instanceof VcasDrmInfo) {
        return buildVcasDataSourceFactory((VcasDrmInfo)drmInfo, upstreamFactory);
    } else if (drmInfo instanceof TivoCryptDrmInfo) {
      return buildTivoCryptDataSourceFactory((TivoCryptDrmInfo) drmInfo, upstreamFactory);
    }

    return new DefaultDataSourceFactory(context, upstreamFactory);
  }

  private DataSource.Factory buildTivoCryptDataSourceFactory(TivoCryptDrmInfo drmInfo, HttpDataSource.Factory upstreamFactory) {

    return new TivoCryptDataSourceFactory(upstreamFactory, drmInfo.getWbKey(), drmInfo.getContext(), drmInfo.getDeviceKey());
  }

  private DataSource.Factory buildVcasDataSourceFactory(VcasDrmInfo vDrmInfo,
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
        DataSource.Factory vfactory = (DataSource.Factory) constructor
                .newInstance(upstreamFactory,
                        filesDir,
                        context.getApplicationInfo().nativeLibraryDir,
                        context.getApplicationInfo().sourceDir,
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
   * Subclass can override this to add an application specific UserAgent prefix.
   *
   * @return useragent string, version of ExoPlayer library postfix to this.
   */
  protected String getUserAgentPrefix() {
    return "TiVoExoPlayer";
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
            .setPlaylistParserFactory(new DualModeHlsPlaylistParserFactory(new DefaultHlsPlaylistParserFactory()));
        break;
      case C.TYPE_OTHER:
        factory =  new ProgressiveMediaSource.Factory(dataSourceFactory);
        break;
      case C.TYPE_DASH:   // TODO - add library dependency for DashMediaSource.Factory
        itemBuilder.setMimeType(MimeTypes.APPLICATION_MPD);

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
                        .setMultiSession(false)
                        .build(mediaDrmCallback);
        factory.setDrmSessionManager(drmSessionManager);
        itemBuilder.setDrmUuid(C.WIDEVINE_UUID);
        itemBuilder.setDrmLicenseUri(wDrmInfo.getProxyUrl());   // TODO - not sure this is correct way to get the reqst props
      }
    }
    return factory.createMediaSource(itemBuilder.build());
  }

  private HttpMediaDrmCallback createMediaDrmCallback(
          String licenseUrl, String[] keyRequestPropertiesArray) {
    HttpDataSource.Factory licenseDataSourceFactory =
            new DefaultHttpDataSourceFactory(getUserAgentPrefix());
    HttpMediaDrmCallback drmCallback =
            new HttpMediaDrmCallback(licenseUrl, licenseDataSourceFactory);
    if (keyRequestPropertiesArray != null) {
      for (int i = 0; i < keyRequestPropertiesArray.length - 1; i += 2) {
        drmCallback.setKeyRequestProperty(keyRequestPropertiesArray[i],
                keyRequestPropertiesArray[i + 1]);
      }
    }
    return drmCallback;
  }

  @Override
  public void resetAndRestartPlayback() {
    assert currentMediaSource != null;
    player.prepare(currentMediaSource, true, true);
  }

  @Override
  public void releaseResources() {
    currentMediaSource = null;
    player.removeListener(this);
  }


  /**
   * After the player reads the initial M3u8 and parses it, the timeline is created.
   * Report the first such of these events, following a {@link MediaSource} change
   * (via {@link MediaSourceLifeCycle#playUrl(Uri, long, DrmInfo, boolean)} call to any {@link MediaSourceEventCallback} listener
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
