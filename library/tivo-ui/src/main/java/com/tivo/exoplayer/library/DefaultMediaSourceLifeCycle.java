package com.tivo.exoplayer.library;


import android.content.Context;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.drm.MediaDrmCallback;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.UnrecognizedInputFormatException;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Log;

import com.google.android.exoplayer2.util.Util;
import com.tivo.exoplayer.tivocrypt.TivoCryptDataSourceFactory;

import java.io.File;
import java.lang.reflect.Constructor;

/**
 * Manages creation and the lifecycle of playback of an ExoPlayer {@link MediaSource}
 *
 * The ExoPlayer MediaSource connects an ExoPlayer playback session to the data source (for
 * HLS this is an HTTP URL).
 *
 * The {@link SimpleExoPlayer#prepare(MediaSource)} starts the mediasource loading, or reloads it in
 * the event of a recoverable playback error (eg {@link com.google.android.exoplayer2.source.BehindLiveWindowException}
 *
 * This object manages the life-cycle of playback and error handling for a give URL
 */
public class DefaultMediaSourceLifeCycle implements MediaSourceLifeCycle, AnalyticsListener {

  private static final String TAG = "ExoPlayer";

  protected final SimpleExoPlayer player;
  protected final Context context;
  protected MediaSource currentMediaSource;

  @Nullable
  private MediaSourceEventCallback callback;

  private boolean isInitialMediaSourceEvent;

  /**
   * Construct the default implementation of {@link MediaSourceLifeCycle}
   *
   * @param player - SimpleExoPlayer to build mediasources for
   * @param context - android context
   */
  public DefaultMediaSourceLifeCycle(SimpleExoPlayer player, Context context) {
    this.player = player;
    this.context = context;

    player.addAnalyticsListener(this);
  }

  @Override
  public void playUrl(Uri uri, DrmInfo drmInfo, boolean enableChunkless) throws UnrecognizedInputFormatException {
    Log.d("ExoPlayer", "play URL " + uri);

    MediaSource mediaSource = buildMediaSource(uri, drmInfo, buildDataSourceFactory(drmInfo), enableChunkless);
    playMediaSource(mediaSource);
  }

  protected void playMediaSource(MediaSource mediaSource) {
    player.stop(true);
    currentMediaSource = mediaSource;
    isInitialMediaSourceEvent = false;
    player.setPlayWhenReady(true);
    player.prepare(currentMediaSource);
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
    @C.ContentType int type = Util.inferContentType(uri);
    switch (type) {
      case C.TYPE_HLS:
        factory = new HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(enableChunkless);
        break;
      case C.TYPE_OTHER:
        factory =  new ProgressiveMediaSource.Factory(dataSourceFactory);
        break;
      case C.TYPE_DASH:   // TODO - add library dependency for DashMediaSource.Factory
      case C.TYPE_SS: // TODO - If we want to support SmoothStreaming, add SSDataSource dependency
        throw new UnrecognizedInputFormatException("Source is not a supported container format (type: " + type + ")", uri);
    }

    if (Build.VERSION.SDK_INT >= 18) {
      if (drmInfo instanceof WidevineDrmInfo) {
        WidevineDrmInfo wDrmInfo = (WidevineDrmInfo) drmInfo;
        // setup DrmSessionManager

        MediaDrmCallback mediaDrmCallback =
                createMediaDrmCallback(wDrmInfo.getProxyUrl(), wDrmInfo.getKeyRequestProps());
        DrmSessionManager<ExoMediaCrypto> drmSessionManager =
                new DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .setMultiSession(false)
                        .build(mediaDrmCallback);
        factory.setDrmSessionManager(drmSessionManager);
      }
    }
    return factory.createMediaSource(uri);
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
  public boolean recoverFrom(ExoPlaybackException e) {
    boolean value = false;

    if (isBehindLiveWindow(e)) {
      // BehindLiveWindowException occurs when the current play point is in
      // a segment that has expired from the server.  This happens if the play point
      // falls behind the oldest segment in a live playlist.
      //
      // Recovery action is to re-prepare the current playing MediaSource.
      // The player, will then jump to the earliest available segment,
      // which is where we want to be anyway.  If not we can prepare with keep position
      //
      Log.d(TAG, "Got BehindLiveWindowException, re-preparing " +
          "player to resume playback: " + e);

      player.prepare(currentMediaSource, true, true);
      value = true;
    }
    return value;
  }

  @Override
  public boolean restartPlaybackAtLastPosition() {
    player.prepare(currentMediaSource, false, true);
    return true;
  }


  private static boolean isBehindLiveWindow(ExoPlaybackException e) {
    if (e.type != ExoPlaybackException.TYPE_SOURCE) {
      return false;
    }
    Throwable cause = e.getSourceException();
    while (cause != null) {
      if (cause instanceof BehindLiveWindowException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  /**
   * After the player reads the initial M3u8 and parses it, the timeline is created.
   * Report the first such of these events, following a {@link MediaSource} change
   * (via {@link #playUrl(Uri, DrmInfo, boolean)} call to any {@link MediaSourceEventCallback} listener
   *
   * @param eventTime The event time.
   * @param reason The reason for the timeline change.
   */
  @Override
  public void onTimelineChanged(EventTime eventTime, int reason) {
    if (reason == Player.TIMELINE_CHANGE_REASON_PREPARED) {
      if (!isInitialMediaSourceEvent && callback != null) {
        callback.mediaSourcePrepared(currentMediaSource, player);
        isInitialMediaSourceEvent = false;
      }
    }
  }
}
