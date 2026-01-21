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
package androidx.media3.exoplayer.hls;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaItem.LiveConfiguration;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.StreamKey;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistParserFactory;
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistTracker;
import androidx.media3.exoplayer.hls.playlist.FilteringHlsPlaylistParserFactory;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParserFactory;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker;
import androidx.media3.exoplayer.source.BaseMediaSource;
import androidx.media3.exoplayer.source.CompositeSequenceableLoaderFactory;
import androidx.media3.exoplayer.source.DefaultCompositeSequenceableLoaderFactory;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MediaSourceEventListener;
import androidx.media3.exoplayer.source.MediaSourceFactory;
import androidx.media3.exoplayer.source.SequenceableLoader;
import androidx.media3.exoplayer.source.SinglePeriodTimeline;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.CmcdConfiguration;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.exoplayer.util.SntpClient;
import androidx.media3.extractor.Extractor;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;

/** An HLS {@link MediaSource}. */
@UnstableApi
public final class HlsMediaSource extends BaseMediaSource
    implements HlsPlaylistTracker.PrimaryPlaylistListener {

  static {
    MediaLibraryInfo.registerModule("media3.exoplayer.hls");
  }

  /**
   * The types of metadata that can be extracted from HLS streams.
   *
   * <p>Allowed values:
   *
   * <ul>
   *   <li>{@link #METADATA_TYPE_ID3}
   *   <li>{@link #METADATA_TYPE_EMSG}
   * </ul>
   *
   * <p>See {@link Factory#setMetadataType(int)}.
   */
  @Documented
  @Retention(SOURCE)
  @Target(TYPE_USE)
  @IntDef({METADATA_TYPE_ID3, METADATA_TYPE_EMSG})
  public @interface MetadataType {}

  /** Type for ID3 metadata in HLS streams. */
  public static final int METADATA_TYPE_ID3 = 1;
  /** Type for ESMG metadata in HLS streams. */
  public static final int METADATA_TYPE_EMSG = 3;

  /** Factory for {@link HlsMediaSource}s. */
  @SuppressWarnings("deprecation") // Implement deprecated type for backwards compatibility.
  public static final class Factory implements MediaSourceFactory {

    private final HlsDataSourceFactory hlsDataSourceFactory;

    private HlsExtractorFactory extractorFactory;
    private HlsPlaylistParserFactory playlistParserFactory;
    private HlsPlaylistTracker.Factory playlistTrackerFactory;
    private CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
    @Nullable private CmcdConfiguration.Factory cmcdConfigurationFactory;
    private DrmSessionManagerProvider drmSessionManagerProvider;
    private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private boolean allowChunklessPreparation;
    private @MetadataType int metadataType;
    private boolean useSessionKeys;
    private long elapsedRealTimeOffsetMs;
    private long defaultStartOffsetUs;
    private long timestampAdjusterInitializationTimeoutMs;

    /**
     * Creates a new factory for {@link HlsMediaSource}s.
     *
     * <p>The factory will use the following default components:
     *
     * <ul>
     *   <li>{@link DefaultDrmSessionManagerProvider}
     *   <li>{@link DefaultHlsPlaylistParserFactory}
     *   <li>{@link DefaultHlsPlaylistTracker#FACTORY}
     *   <li>{@link HlsExtractorFactory#DEFAULT}
     *   <li>{@link DefaultLoadErrorHandlingPolicy}
     *   <li>{@link DefaultCompositeSequenceableLoaderFactory}
     * </ul>
     *
     * @param dataSourceFactory A data source factory that will be wrapped by a {@link
     *     DefaultHlsDataSourceFactory} to create {@link DataSource}s for manifests, segments and
     *     keys.
     */
    public Factory(DataSource.Factory dataSourceFactory) {
      this(new DefaultHlsDataSourceFactory(dataSourceFactory));
    }

    /**
     * Creates a new factory for {@link HlsMediaSource}s.
     *
     * <p>The factory will use the following default components:
     *
     * <ul>
     *   <li>{@link DefaultDrmSessionManagerProvider}
     *   <li>{@link DefaultHlsPlaylistParserFactory}
     *   <li>{@link DefaultHlsPlaylistTracker#FACTORY}
     *   <li>{@link HlsExtractorFactory#DEFAULT}
     *   <li>{@link DefaultLoadErrorHandlingPolicy}
     *   <li>{@link DefaultCompositeSequenceableLoaderFactory}
     * </ul>
     *
     * @param hlsDataSourceFactory An {@link HlsDataSourceFactory} for {@link DataSource}s for
     *     manifests, segments and keys.
     */
    public Factory(HlsDataSourceFactory hlsDataSourceFactory) {
      this.hlsDataSourceFactory = checkNotNull(hlsDataSourceFactory);
      drmSessionManagerProvider = new DefaultDrmSessionManagerProvider();
      playlistParserFactory = new DefaultHlsPlaylistParserFactory();
      playlistTrackerFactory = DefaultHlsPlaylistTracker.FACTORY;
      extractorFactory = HlsExtractorFactory.DEFAULT;
      loadErrorHandlingPolicy = new DefaultLoadErrorHandlingPolicy();
      compositeSequenceableLoaderFactory = new DefaultCompositeSequenceableLoaderFactory();
      metadataType = METADATA_TYPE_ID3;
      elapsedRealTimeOffsetMs = C.TIME_UNSET;
      defaultStartOffsetUs = C.TIME_UNSET;
      allowChunklessPreparation = true;
    }

    /**
     * Sets the factory for {@link Extractor}s for the segments. The default value is {@link
     * HlsExtractorFactory#DEFAULT}.
     *
     * @param extractorFactory An {@link HlsExtractorFactory} for {@link Extractor}s for the
     *     segments.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setExtractorFactory(@Nullable HlsExtractorFactory extractorFactory) {
      this.extractorFactory =
          extractorFactory != null ? extractorFactory : HlsExtractorFactory.DEFAULT;
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public Factory setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
      this.loadErrorHandlingPolicy =
          checkNotNull(
              loadErrorHandlingPolicy,
              "MediaSource.Factory#setLoadErrorHandlingPolicy no longer handles null by"
                  + " instantiating a new DefaultLoadErrorHandlingPolicy. Explicitly construct and"
                  + " pass an instance in order to retain the old behavior.");
      return this;
    }

    /**
     * Sets the factory from which playlist parsers will be obtained.
     *
     * @param playlistParserFactory An {@link HlsPlaylistParserFactory}.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setPlaylistParserFactory(HlsPlaylistParserFactory playlistParserFactory) {
      this.playlistParserFactory =
          checkNotNull(
              playlistParserFactory,
              "HlsMediaSource.Factory#setPlaylistParserFactory no longer handles null by"
                  + " instantiating a new DefaultHlsPlaylistParserFactory. Explicitly"
                  + " construct and pass an instance in order to retain the old behavior.");
      return this;
    }

    /**
     * Sets the {@link HlsPlaylistTracker} factory.
     *
     * @param playlistTrackerFactory A factory for {@link HlsPlaylistTracker} instances.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setPlaylistTrackerFactory(HlsPlaylistTracker.Factory playlistTrackerFactory) {
      this.playlistTrackerFactory =
          checkNotNull(
              playlistTrackerFactory,
              "HlsMediaSource.Factory#setPlaylistTrackerFactory no longer handles null by"
                  + " defaulting to DefaultHlsPlaylistTracker.FACTORY. Explicitly"
                  + " pass a reference to this instance in order to retain the old behavior.");
      return this;
    }

    /**
     * Sets the factory to create composite {@link SequenceableLoader}s for when this media source
     * loads data from multiple streams (video, audio etc...).
     *
     * @param compositeSequenceableLoaderFactory A factory to create composite {@link
     *     SequenceableLoader}s for when this media source loads data from multiple streams (video,
     *     audio etc...).
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setCompositeSequenceableLoaderFactory(
        CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory) {
      this.compositeSequenceableLoaderFactory =
          checkNotNull(
              compositeSequenceableLoaderFactory,
              "HlsMediaSource.Factory#setCompositeSequenceableLoaderFactory no longer handles null"
                  + " by instantiating a new DefaultCompositeSequenceableLoaderFactory. Explicitly"
                  + " construct and pass an instance in order to retain the old behavior.");
      return this;
    }

    /**
     * Sets whether chunkless preparation is allowed. If true, preparation without chunk downloads
     * will be enabled for streams that provide sufficient information in their multivariant
     * playlist.
     *
     * @param allowChunklessPreparation Whether chunkless preparation is allowed.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setAllowChunklessPreparation(boolean allowChunklessPreparation) {
      this.allowChunklessPreparation = allowChunklessPreparation;
      return this;
    }

    /**
     * Set the default live offset in microseconds.
     *
     * The value is processed the same as the HLS
     * <a href="https://tools.ietf.org/html/rfc8216#section-4.3.5.2">EXT-X-START</a> with the
     * PRECISE=no.  That is a negative time is how far back from the live window size to start,
     * positive is from the begining of the live window.  If the HLS server specifies an EXT-X-START
     * then this value is ignored, in other words the precidence is:
     *
     * <ol>
     *     <li>Offset requested by EXT-X-START</li>
     *     <li>Default value set via this API</li>
     *     <li>Hard-coded default (3 segments back from the duration of the live window)</li>
     * </ol>
     *
     * Deprecated - will use the new Live Streaming API: https://exoplayer.dev/live-streaming.html
     *   the setLiveTargetOffsetMs() should be near equiv. to this call
     * @param offsetUs - offset in microseconds
     * @return This factory, to allow chaining
     */
    @Deprecated
    public Factory setDefaultLiveOffset(long offsetUs) {
      return this;
    }

    /**
     * Set the default start offset in microseconds.
     *
     * The value is processed the same as the HLS
     * <a href="https://tools.ietf.org/html/rfc8216#section-4.3.5.2">EXT-X-START</a> with the
     * PRECISE=no.  That is a negative time is how far back from the live window size to start,
     * positive is from the begining of the live window.
     *
     * <ol>
     *     <li>Offset requested by EXT-X-START</li>
     *     <li>Default value set via this API</li>
     *     <li>Hard-coded default (3 segments back from the duration of the live window)</li>
     * </ol>
     *
     * @param offsetUs - offset in microseconds
     * @return This factory, to allow chaining
     */
    public Factory setDefaultStartOffset(long offsetUs) {
      this.defaultStartOffsetUs = offsetUs;
      return this;
    }

    /**
     * Sets the type of metadata to extract from the HLS source (defaults to {@link
     * #METADATA_TYPE_ID3}).
     *
     * <p>HLS supports in-band ID3 in both TS and fMP4 streams, but in the fMP4 case the data is
     * wrapped in an EMSG box [<a href="https://aomediacodec.github.io/av1-id3/">spec</a>].
     *
     * <p>If this is set to {@link #METADATA_TYPE_ID3} then raw ID3 metadata of will be extracted
     * from TS sources. From fMP4 streams EMSGs containing metadata of this type (in the variant
     * stream only) will be unwrapped to expose the inner data. All other in-band metadata will be
     * dropped.
     *
     * <p>If this is set to {@link #METADATA_TYPE_EMSG} then all EMSG data from the fMP4 variant
     * stream will be extracted. No metadata will be extracted from TS streams, since they don't
     * support EMSG.
     *
     * @param metadataType The type of metadata to extract.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setMetadataType(@MetadataType int metadataType) {
      this.metadataType = metadataType;
      return this;
    }

    /**
     * Sets whether to use #EXT-X-SESSION-KEY tags provided in the multivariant playlist. If
     * enabled, it's assumed that any single session key declared in the multivariant playlist can
     * be used to obtain all of the keys required for playback. For media where this is not true,
     * this option should not be enabled.
     *
     * @param useSessionKeys Whether to use #EXT-X-SESSION-KEY tags.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setUseSessionKeys(boolean useSessionKeys) {
      this.useSessionKeys = useSessionKeys;
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public Factory setCmcdConfigurationFactory(CmcdConfiguration.Factory cmcdConfigurationFactory) {
      this.cmcdConfigurationFactory = checkNotNull(cmcdConfigurationFactory);
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public Factory setDrmSessionManagerProvider(
        DrmSessionManagerProvider drmSessionManagerProvider) {
      this.drmSessionManagerProvider =
          checkNotNull(
              drmSessionManagerProvider,
              "MediaSource.Factory#setDrmSessionManagerProvider no longer handles null by"
                  + " instantiating a new DefaultDrmSessionManagerProvider. Explicitly construct"
                  + " and pass an instance in order to retain the old behavior.");
      return this;
    }

    /**
     * Sets the timeout for the loading thread to wait for the timestamp adjuster to initialize, in
     * milliseconds.The default value is zero, which is interpreted as an infinite timeout.
     *
     * @param timestampAdjusterInitializationTimeoutMs The timeout in milliseconds. A timeout of
     *     zero is interpreted as an infinite timeout.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setTimestampAdjusterInitializationTimeoutMs(
        long timestampAdjusterInitializationTimeoutMs) {
      this.timestampAdjusterInitializationTimeoutMs = timestampAdjusterInitializationTimeoutMs;
      return this;
    }

    /**
     * Sets the offset between {@link SystemClock#elapsedRealtime()} and the time since the Unix
     * epoch. By default, is it set to {@link C#TIME_UNSET}.
     *
     * @param elapsedRealTimeOffsetMs The offset between {@link SystemClock#elapsedRealtime()} and
     *     the time since the Unix epoch, in milliseconds.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    @VisibleForTesting
    /* package */ Factory setElapsedRealTimeOffsetMs(long elapsedRealTimeOffsetMs) {
      this.elapsedRealTimeOffsetMs = elapsedRealTimeOffsetMs;
      return this;
    }

    /**
     * Returns a new {@link HlsMediaSource} using the current parameters.
     *
     * @param mediaItem The {@link MediaItem}.
     * @return The new {@link HlsMediaSource}.
     * @throws NullPointerException if {@link MediaItem#localConfiguration} is {@code null}.
     */
    @Override
    public HlsMediaSource createMediaSource(MediaItem mediaItem) {
      checkNotNull(mediaItem.localConfiguration);
      HlsPlaylistParserFactory playlistParserFactory = this.playlistParserFactory;
      List<StreamKey> streamKeys = mediaItem.localConfiguration.streamKeys;
      if (!streamKeys.isEmpty()) {
        playlistParserFactory =
            new FilteringHlsPlaylistParserFactory(playlistParserFactory, streamKeys);
      }
      @Nullable
      CmcdConfiguration cmcdConfiguration =
          cmcdConfigurationFactory == null
              ? null
              : cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);

      return new HlsMediaSource(
          mediaItem,
          hlsDataSourceFactory,
          extractorFactory,
          compositeSequenceableLoaderFactory,
          cmcdConfiguration,
          drmSessionManagerProvider.get(mediaItem),
          loadErrorHandlingPolicy,
          playlistTrackerFactory.createTracker(
              hlsDataSourceFactory, loadErrorHandlingPolicy, playlistParserFactory),
          elapsedRealTimeOffsetMs,
          allowChunklessPreparation,
          metadataType,
          useSessionKeys,
          defaultStartOffsetUs,
          timestampAdjusterInitializationTimeoutMs);
    }

    @Override
    public @C.ContentType int[] getSupportedTypes() {
      return new int[] {C.CONTENT_TYPE_HLS};
    }
  }

  private final HlsExtractorFactory extractorFactory;
  private final MediaItem.LocalConfiguration localConfiguration;
  private final HlsDataSourceFactory dataSourceFactory;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
  @Nullable private final CmcdConfiguration cmcdConfiguration;
  private final DrmSessionManager drmSessionManager;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final boolean allowChunklessPreparation;
  private final @MetadataType int metadataType;
  private final boolean useSessionKeys;
  private final HlsPlaylistTracker playlistTracker;
  private final long elapsedRealTimeOffsetMs;
  private final MediaItem mediaItem;
  private final long defaultStartOffsetUs;
  private final long timestampAdjusterInitializationTimeoutMs;

  private MediaItem.LiveConfiguration liveConfiguration;
  @Nullable private TransferListener mediaTransferListener;

  private HlsMediaSource(
      MediaItem mediaItem,
      HlsDataSourceFactory dataSourceFactory,
      HlsExtractorFactory extractorFactory,
      CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      @Nullable CmcdConfiguration cmcdConfiguration,
      DrmSessionManager drmSessionManager,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      HlsPlaylistTracker playlistTracker,
      long elapsedRealTimeOffsetMs,
      boolean allowChunklessPreparation,
      @MetadataType int metadataType,
      boolean useSessionKeys,
      long defaultStartOffsetUs,
      long timestampAdjusterInitializationTimeoutMs) {
    this.localConfiguration = checkNotNull(mediaItem.localConfiguration);
    this.mediaItem = mediaItem;
    this.liveConfiguration = mediaItem.liveConfiguration;
    this.dataSourceFactory = dataSourceFactory;
    this.extractorFactory = extractorFactory;
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    this.cmcdConfiguration = cmcdConfiguration;
    this.drmSessionManager = drmSessionManager;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.playlistTracker = playlistTracker;
    this.elapsedRealTimeOffsetMs = elapsedRealTimeOffsetMs;
    this.allowChunklessPreparation = allowChunklessPreparation;
    this.metadataType = metadataType;
    this.useSessionKeys = useSessionKeys;
    this.defaultStartOffsetUs = defaultStartOffsetUs;
    this.timestampAdjusterInitializationTimeoutMs = timestampAdjusterInitializationTimeoutMs;
  }

  @Override
  public MediaItem getMediaItem() {
    return mediaItem;
  }

  @Override
  protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    this.mediaTransferListener = mediaTransferListener;
    drmSessionManager.setPlayer(
        /* playbackLooper= */ checkNotNull(Looper.myLooper()), getPlayerId());
    drmSessionManager.prepare();
    MediaSourceEventListener.EventDispatcher eventDispatcher =
        createEventDispatcher(/* mediaPeriodId= */ null);
    playlistTracker.start(
        localConfiguration.uri, eventDispatcher, /* primaryPlaylistListener= */ this);
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    playlistTracker.maybeThrowPrimaryPlaylistRefreshError();
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher = createEventDispatcher(id);
    DrmSessionEventListener.EventDispatcher drmEventDispatcher = createDrmEventDispatcher(id);
    return new HlsMediaPeriod(
        extractorFactory,
        playlistTracker,
        dataSourceFactory,
        mediaTransferListener,
        cmcdConfiguration,
        drmSessionManager,
        drmEventDispatcher,
        loadErrorHandlingPolicy,
        mediaSourceEventDispatcher,
        allocator,
        compositeSequenceableLoaderFactory,
        allowChunklessPreparation,
        metadataType,
        useSessionKeys,
        getPlayerId(),
        timestampAdjusterInitializationTimeoutMs);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((HlsMediaPeriod) mediaPeriod).release();
  }

  @Override
  protected void releaseSourceInternal() {
    playlistTracker.stop();
    drmSessionManager.release();
  }

  @Override
  public void onPrimaryPlaylistRefreshed(HlsMediaPlaylist mediaPlaylist) {
    long windowStartTimeMs =
        mediaPlaylist.hasProgramDateTime ? Util.usToMs(mediaPlaylist.startTimeUs) : C.TIME_UNSET;
    // For playlist types EVENT and VOD we know segments are never removed, so the presentation
    // started at the same time as the window. Otherwise, we don't know the presentation start time.
    long presentationStartTimeMs =
        mediaPlaylist.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_EVENT
                || mediaPlaylist.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_VOD
            ? windowStartTimeMs
            : C.TIME_UNSET;
    // The multivariant playlist is non-null because the first playlist has been fetched by now.
    HlsManifest manifest =
        new HlsManifest(checkNotNull(playlistTracker.getMultivariantPlaylist()), mediaPlaylist);
    SinglePeriodTimeline timeline =
        playlistTracker.isLive()
            ? createTimelineForLive(
                mediaPlaylist, presentationStartTimeMs, windowStartTimeMs, manifest)
            : createTimelineForOnDemand(
                mediaPlaylist, presentationStartTimeMs, windowStartTimeMs, manifest);
    refreshSourceInfo(timeline);
  }

  private SinglePeriodTimeline createTimelineForLive(
      HlsMediaPlaylist playlist,
      long presentationStartTimeMs,
      long windowStartTimeMs,
      HlsManifest manifest) {
    long offsetFromInitialStartTimeUs =
        playlist.startTimeUs - playlistTracker.getInitialStartTimeUs();
    long periodDurationUs =
        playlist.hasEndTag ? offsetFromInitialStartTimeUs + playlist.durationUs : C.TIME_UNSET;
    long liveEdgeOffsetUs = getLiveEdgeOffsetUs(playlist);
    long targetLiveOffsetUs;
    if (liveConfiguration.targetOffsetMs != C.TIME_UNSET) {
      // Media item has a defined target offset.
      targetLiveOffsetUs = Util.msToUs(liveConfiguration.targetOffsetMs);
    } else {
      // Decide target offset from playlist.
      targetLiveOffsetUs = getTargetLiveOffsetUs(playlist, liveEdgeOffsetUs);
    }
    // Ensure target live offset is within the live window and greater than the live edge offset.
    targetLiveOffsetUs =
        Util.constrainValue(
            targetLiveOffsetUs, liveEdgeOffsetUs, playlist.durationUs + liveEdgeOffsetUs);
    updateLiveConfiguration(playlist, targetLiveOffsetUs);
    long windowDefaultStartPositionUs =
        getLiveWindowDefaultStartPositionUs(playlist, liveEdgeOffsetUs);
    boolean suppressPositionProjection =
        playlist.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_EVENT
            && playlist.hasPositiveStartOffset;
    return new SinglePeriodTimeline(
        presentationStartTimeMs,
        windowStartTimeMs,
        SntpClient.getElapsedRealtimeOffsetMs(),
        periodDurationUs,
        /* windowDurationUs= */ playlist.durationUs,
        /* windowPositionInPeriodUs= */ offsetFromInitialStartTimeUs,
        windowDefaultStartPositionUs,
        /* isSeekable= */ true,
        /* isDynamic= */ !playlist.hasEndTag,
        suppressPositionProjection,
        manifest,
        mediaItem,
        liveConfiguration);
  }

  private SinglePeriodTimeline createTimelineForOnDemand(
      HlsMediaPlaylist playlist,
      long presentationStartTimeMs,
      long windowStartTimeMs,
      HlsManifest manifest) {
    long windowDefaultStartPositionUs;
    if (playlist.startOffsetUs == C.TIME_UNSET || playlist.segments.isEmpty()) {
      windowDefaultStartPositionUs = 0;
    } else {
      if (playlist.preciseStart || playlist.startOffsetUs == playlist.durationUs) {
        windowDefaultStartPositionUs = playlist.startOffsetUs;
      } else {
        windowDefaultStartPositionUs =
            findClosestPrecedingSegment(playlist.segments, playlist.startOffsetUs)
                .relativeStartTimeUs;
      }
    }
    return new SinglePeriodTimeline(
        presentationStartTimeMs,
        windowStartTimeMs,
        /* elapsedRealtimeEpochOffsetMs= */ C.TIME_UNSET,
        /* periodDurationUs= */ playlist.durationUs,
        /* windowDurationUs= */ playlist.durationUs,
        /* windowPositionInPeriodUs= */ 0,
        windowDefaultStartPositionUs,
        /* isSeekable= */ true,
        /* isDynamic= */ false,
        /* suppressPositionProjection= */ true,
        manifest,
        mediaItem,
        /* liveConfiguration= */ null);
  }

  private long getLiveEdgeOffsetUs(HlsMediaPlaylist playlist) {
    return playlist.hasProgramDateTime
        ? Util.msToUs(Util.getNowUnixTimeMs(elapsedRealTimeOffsetMs)) - playlist.getEndTimeUs()
        : 0;
  }

  private long getLiveWindowDefaultStartPositionUs(
      HlsMediaPlaylist playlist, long liveEdgeOffsetUs) {
    long startPositionUs =
        playlist.startOffsetUs != C.TIME_UNSET
            ? playlist.startOffsetUs
            : playlist.durationUs
                + liveEdgeOffsetUs
                - Util.msToUs(liveConfiguration.targetOffsetMs);
    if (playlist.preciseStart) {
      return startPositionUs;
    }
    @Nullable
    HlsMediaPlaylist.Part part =
        findClosestPrecedingIndependentPart(playlist.trailingParts, startPositionUs);
    if (part != null) {
      return part.relativeStartTimeUs;
    }
    if (playlist.segments.isEmpty()) {
      return 0;
    }
    HlsMediaPlaylist.Segment segment =
        findClosestPrecedingSegment(playlist.segments, startPositionUs);
    part = findClosestPrecedingIndependentPart(segment.parts, startPositionUs);
    if (part != null) {
      return part.relativeStartTimeUs;
    }
    return segment.relativeStartTimeUs;
  }

  private void updateLiveConfiguration(HlsMediaPlaylist playlist, long targetLiveOffsetUs) {
    boolean disableSpeedAdjustment =
        mediaItem.liveConfiguration.minPlaybackSpeed == C.RATE_UNSET
            && mediaItem.liveConfiguration.maxPlaybackSpeed == C.RATE_UNSET
            && playlist.serverControl.holdBackUs == C.TIME_UNSET
            && playlist.serverControl.partHoldBackUs == C.TIME_UNSET;
    liveConfiguration =
        new LiveConfiguration.Builder()
            .setTargetOffsetMs(Util.usToMs(targetLiveOffsetUs))
            .setMinPlaybackSpeed(disableSpeedAdjustment ? 1f : liveConfiguration.minPlaybackSpeed)
            .setMaxPlaybackSpeed(disableSpeedAdjustment ? 1f : liveConfiguration.maxPlaybackSpeed)
            .build();
  }

  /**
   * Gets the target live offset, in microseconds, for a live playlist.
   *
   * <p>The target offset is derived by checking the following in this order:
   *
   * <ol>
   *   <li>The playlist defines a start offset.
   *   <li>The playlist defines a part hold back in server control and has part duration.
   *   <li>The playlist defines a hold back in server control.
   *   <li>Fallback to {@code 3 x target duration}.
   * </ol>
   *
   * @param playlist The playlist.
   * @param liveEdgeOffsetUs The current live edge offset.
   * @return The selected target live offset, in microseconds.
   */
  private long getTargetLiveOffsetUs(HlsMediaPlaylist playlist, long liveEdgeOffsetUs) {
    HlsMediaPlaylist.ServerControl serverControl = playlist.serverControl;
    long targetOffsetUs;
    if (playlist.startOffsetUs != C.TIME_UNSET) {
      // At this point, playlist.startOffsetUs has been normalized
      // to a positive offset from the beginning of the playlist window.

      // Set targetOffsetUs as an offset from the end of the window.
      targetOffsetUs = playlist.durationUs - playlist.startOffsetUs;
    } else if (serverControl.partHoldBackUs != C.TIME_UNSET
        && playlist.partTargetDurationUs != C.TIME_UNSET) {
      // Select part hold back only if the playlist has a part target duration.
      targetOffsetUs = serverControl.partHoldBackUs;
    } else if (serverControl.holdBackUs != C.TIME_UNSET) {
      targetOffsetUs = serverControl.holdBackUs;
    } else if (defaultStartOffsetUs != C.TIME_UNSET) {
      Log.i("HlsMediaSource", "using defaultStartOffsetUs " + defaultStartOffsetUs);
      // Similar to the playlist.startOffsetUs case above.  Normalize first,
      // then subtract from the playlist.durationUs.
      targetOffsetUs = playlist.durationUs - normalizeStartOffset(playlist.durationUs, defaultStartOffsetUs);
    } else {
      Log.i("HlsMediaSource", "NOT using defaultStartOffsetUs");
      // Fallback, see RFC 8216, Section 4.4.3.8.
      targetOffsetUs = 3 * playlist.targetDurationUs;
    }
    return targetOffsetUs + liveEdgeOffsetUs;
  }

  private long normalizeStartOffset(long duration, long startOffsetArg) {
    // If positive, startOffset is an offset from the window beginning;
    // if negative, an offset from the window end.
    // Here we normalize it to a positive offset from the beginning of the window.
    // See the HlsMediaPlaylist constructor.
    long startOffset =
            startOffsetArg == C.TIME_UNSET
                    ? C.TIME_UNSET
                    : startOffsetArg >= 0
                      ? min(duration, startOffsetArg)
                      : max(0, duration + startOffsetArg);
    Log.i("HlsMediaSource", "normalize returning startOffset: " + startOffset);
    return startOffset;
  }

  @Nullable
  private static HlsMediaPlaylist.Part findClosestPrecedingIndependentPart(
      List<HlsMediaPlaylist.Part> parts, long positionUs) {
    @Nullable HlsMediaPlaylist.Part closestPart = null;
    for (int i = 0; i < parts.size(); i++) {
      HlsMediaPlaylist.Part part = parts.get(i);
      if (part.relativeStartTimeUs <= positionUs && part.isIndependent) {
        closestPart = part;
      } else if (part.relativeStartTimeUs > positionUs) {
        break;
      }
    }
    return closestPart;
  }

  /**
   * Gets the segment that contains {@code positionUs}, or the last segment if the position is
   * beyond the segments list.
   */
  private static HlsMediaPlaylist.Segment findClosestPrecedingSegment(
      List<HlsMediaPlaylist.Segment> segments, long positionUs) {
    int segmentIndex =
        Util.binarySearchFloor(
            segments, positionUs, /* inclusive= */ true, /* stayInBounds= */ true);
    return segments.get(segmentIndex);
  }
}
