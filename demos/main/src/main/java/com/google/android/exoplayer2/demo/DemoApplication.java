/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.demo;

import android.app.Application;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.database.DatabaseProvider;
import com.google.android.exoplayer2.database.ExoDatabaseProvider;
import com.google.android.exoplayer2.offline.ActionFileUpgradeUtil;
import com.google.android.exoplayer2.offline.DefaultDownloadIndex;
import com.google.android.exoplayer2.offline.DefaultDownloaderFactory;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.FileDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.tivo.android.exoplayer.tivocrypt.TivoCryptDataSourceFactory;
import java.io.File;
import java.io.IOException;

/**
 * Placeholder application to facilitate overriding Application methods for debugging and testing.
 */
public class DemoApplication extends Application {

  private static final String TAG = "DemoApplication";
  private static final String DOWNLOAD_ACTION_FILE = "actions";
  private static final String DOWNLOAD_TRACKER_ACTION_FILE = "tracked_actions";
  private static final String DOWNLOAD_CONTENT_DIRECTORY = "downloads";
  private static final String WB_KEY = "0xB9CFA8ECF9E82BEFC64B7F27BE7444774ECC10D1BDA9FF2C568CB35A34FF296390C63719EB1AA6876F7627D05F162251B23522F0EDE9A17DA73D463CED056D04B363A43D65720D1A879B926B3015226DC43B85E7848CFA85608599512EDBDC6DA79C98529F2FD96166E1912D0BF1309BB0F8996E80DBE33F037DE7AE70F912069F9370E997804CAE7C47135BAAB1A477A63F919C64EC7EB275FE938906808BD96E2407969DA92F4E2A67BBC83842D0BD1251E64B402DF0B6376FE967068AA08628A834CEB5D84EF920C9A095D90BB753262F921E649611650D25125EB8AA5550";
  private static final String FILES_DIR = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8";

  protected String userAgent;

  private DatabaseProvider databaseProvider;
  private File downloadDirectory;
  private Cache downloadCache;
  private DownloadManager downloadManager;
  private DownloadTracker downloadTracker;

  @Override
  public void onCreate() {
    super.onCreate();
    userAgent = Util.getUserAgent(this, "ExoPlayerDemo");
  }

  /** Returns a {@link DataSource.Factory}. */
  public DataSource.Factory buildDataSourceFactory() {
//    DefaultDataSourceFactory upstreamFactory =
//        new DefaultDataSourceFactory(this, buildHttpDataSourceFactory());
//    return buildReadOnlyCacheDataSource(upstreamFactory, getDownloadCache());
    DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory
        (this,
            Util.getUserAgent(this, "TiVo"));
    return new TivoCryptDataSourceFactory(dataSourceFactory, WB_KEY, this);
  }

  /** Returns a {@link HttpDataSource.Factory}. */
  public TivoCryptDataSourceFactory buildHttpDataSourceFactory() {
    DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory
        (this,
            Util.getUserAgent(this, "TiVo"));
    return new TivoCryptDataSourceFactory(dataSourceFactory, WB_KEY, this);
  }

  /** Returns whether extension renderers should be used. */
  public boolean useExtensionRenderers() {
    return "withExtensions".equals(BuildConfig.FLAVOR);
  }

  public RenderersFactory buildRenderersFactory(boolean preferExtensionRenderer) {
    @DefaultRenderersFactory.ExtensionRendererMode
    int extensionRendererMode =
        useExtensionRenderers()
            ? (preferExtensionRenderer
                ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;
    return new DefaultRenderersFactory(/* context= */ this)
        .setExtensionRendererMode(extensionRendererMode);
  }

  public DownloadManager getDownloadManager() {
    initDownloadManager();
    return downloadManager;
  }

  public DownloadTracker getDownloadTracker() {
    initDownloadManager();
    return downloadTracker;
  }

  protected synchronized Cache getDownloadCache() {
    if (downloadCache == null) {
      File downloadContentDirectory = new File(getDownloadDirectory(), DOWNLOAD_CONTENT_DIRECTORY);
      downloadCache =
          new SimpleCache(downloadContentDirectory, new NoOpCacheEvictor(), getDatabaseProvider());
    }
    return downloadCache;
  }

  private synchronized void initDownloadManager() {
    if (downloadManager == null) {
      DefaultDownloadIndex downloadIndex = new DefaultDownloadIndex(getDatabaseProvider());
      upgradeActionFile(
          DOWNLOAD_ACTION_FILE, downloadIndex, /* addNewDownloadsAsCompleted= */ false);
      upgradeActionFile(
          DOWNLOAD_TRACKER_ACTION_FILE, downloadIndex, /* addNewDownloadsAsCompleted= */ true);
      DownloaderConstructorHelper downloaderConstructorHelper =
          new DownloaderConstructorHelper(getDownloadCache(), buildHttpDataSourceFactory());
      downloadManager =
          new DownloadManager(
              this, downloadIndex, new DefaultDownloaderFactory(downloaderConstructorHelper));
      downloadTracker =
          new DownloadTracker(/* context= */ this, buildDataSourceFactory(), downloadManager);
    }
  }

  private void upgradeActionFile(
      String fileName, DefaultDownloadIndex downloadIndex, boolean addNewDownloadsAsCompleted) {
    try {
      ActionFileUpgradeUtil.upgradeAndDelete(
          new File(getDownloadDirectory(), fileName),
          /* downloadIdProvider= */ null,
          downloadIndex,
          /* deleteOnFailure= */ true,
          addNewDownloadsAsCompleted);
    } catch (IOException e) {
      Log.e(TAG, "Failed to upgrade action file: " + fileName, e);
    }
  }

  private DatabaseProvider getDatabaseProvider() {
    if (databaseProvider == null) {
      databaseProvider = new ExoDatabaseProvider(this);
    }
    return databaseProvider;
  }

  private File getDownloadDirectory() {
    if (downloadDirectory == null) {
      downloadDirectory = getExternalFilesDir(null);
      if (downloadDirectory == null) {
        downloadDirectory = getFilesDir();
      }
    }
    return downloadDirectory;
  }

  protected static CacheDataSourceFactory buildReadOnlyCacheDataSource(
      DataSource.Factory upstreamFactory, Cache cache) {
    return new CacheDataSourceFactory(
        cache,
        upstreamFactory,
        new FileDataSourceFactory(),
        /* cacheWriteDataSinkFactory= */ null,
        CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
        /* eventListener= */ null);
  }
}
