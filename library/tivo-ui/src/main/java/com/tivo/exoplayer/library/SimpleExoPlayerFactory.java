package com.tivo.exoplayer.library;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLivePlaybackSpeedControl;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.LivePlaybackSpeedControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.UnrecognizedInputFormatException;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParserFactory;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.trickplay.TrickPlayControlFactory;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Predicate;
import com.tivo.exoplayer.library.errorhandlers.AudioTrackInitPlayerErrorHandler;
import com.tivo.exoplayer.library.errorhandlers.BehindLiveWindowExceptionRecovery;
import com.tivo.exoplayer.library.errorhandlers.DefaultExoPlayerErrorHandler;
import com.tivo.exoplayer.library.errorhandlers.HdmiPlayerErrorHandler;
import com.tivo.exoplayer.library.errorhandlers.NetworkLossPlayerErrorHandler;
import com.tivo.exoplayer.library.errorhandlers.NoPcmAudioErrorHandler;
import com.tivo.exoplayer.library.errorhandlers.PlaybackExceptionRecovery;
import com.tivo.exoplayer.library.errorhandlers.PlayerErrorHandlerListener;
import com.tivo.exoplayer.library.errorhandlers.PlayerErrorRecoverable;
import com.tivo.exoplayer.library.errorhandlers.StuckPlaylistErrorRecovery;
import com.tivo.exoplayer.library.liveplayback.AugmentedLivePlaybackSpeedControl;
import com.tivo.exoplayer.library.logging.ExtendedEventLogger;
import com.tivo.exoplayer.library.logging.LoggingLivePlaybackSpeedControl;
import com.tivo.exoplayer.library.tracks.SyncVideoTrackSelector;
import com.tivo.exoplayer.library.tracks.TrackInfo;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Handles creating, destroying and helper classes managing a TiVo extended SimpleExoPlayer including
 * APIs to simplify common operations for {@link TrackSelection} without requiring the entire richness
 * (and commensurate complexity) of these APIs
 *
 * The {@link com.google.android.exoplayer2.SimpleExoPlayer} is not so simple, this is largely because it
 * is very extensible with a plethora of factories that produce the various supporting classes
 * for ExoPlayer.
 *
 * The TiVo rendition of ExoPlayer supports full screen visual trickplay, this requires
 * extending and customizing many of the ExoPlayer supporting classes including the
 * {@link com.google.android.exoplayer2.RenderersFactory}, {@link com.google.android.exoplayer2.LoadControl}
 * and others.
 *
 *
 */
public class SimpleExoPlayerFactory implements PlayerErrorRecoverable {
  public static final String TAG = "SimpleExoPlayerFactory";

  /**
   * Library version info suitable for logging
   */
  public static final String VERSION_INFO = "ExoPlayer Version: " + ExoPlayerLibraryInfo.VERSION_SLASHY
      + ", Build Number: " + BuildConfig.BUILD_NUMBER
      + ", Git Hash: " + BuildConfig.GIT_HASH;

  /**
   * Android application context for access to Android
   */
  private final Context context;

  /**
   * HlsPlaylistParserFactory, used only for HLS MediaSource
   */
  @MonotonicNonNull private HlsPlaylistParserFactory hlsPlaylistParserFactory;

  /**
   * Reference to the current created SimpleExoPlayer,  The {@link #createPlayer(boolean, boolean)} creates
   * this.  Access it via {@link #getCurrentPlayer()}
   */
  @Nullable
  private SimpleExoPlayer player;

  @Nullable
  private TrickPlayControl trickPlayControl;

  /**
   * Track selector used by the player.  The particular instance extends the {@link DefaultTrackSelector}
   * and has knowledge of IFrame playlists
   *
   * This factory provides convenience methods to perform common track selection operations (change language,
   * enable / disable captions, etc).  This track selector is avaliable to clients, but is only
   * really visible so it can be used in the test player's debug track selection dialog.  It will
   * be discarded when the actvitiy stops the player ({@link #releasePlayer()} is called).
   *
   * Any previous parameter selections are preserved
   */
  @Nullable
  private DefaultTrackSelector trackSelector;

  private MediaSourceLifeCycle mediaSourceLifeCycle;

  @Nullable
  private PlayerErrorHandlerListener playerErrorHandlerListener;

  @Nullable
  private MediaSourceEventCallback callback;

  private EventListenerFactory eventListenerFactory;

  /**
   * Parameters are preserved across player create/destroy (Activity stop/start) to save track
   * selection criteria.
   */
  private DefaultTrackSelector.Parameters currentParameters;

  /**
   * Listens for and attempts to recover from playback errors.
   */
  private DefaultExoPlayerErrorHandler playerErrorHandler;

  /**
   * Manage creating of cleanup of the TrickPlayControl managing all the ExoPlayer
   * factories needed to extend ExoPlayer to implement trick-play
   */
  private TrickPlayControlFactory trickPlayControlFactory;

  /**
   * Optional callback when source factories (e.g. {@link MediaItem.Builder}, etc) are created
   */
  private SourceFactoriesCreated factoriesCreatedCallback;

  /** can be set from the Factory method */
  private String userAgentPrefix;

  /** If the factory has set a value for mediaCodecAsyncMode */
  private boolean nonDefaultMediaCodecOperationMode;
  private boolean mediaCodecAsyncMode;
  private AlternatePlayerFactory alternatePlayerFactory;
  private TrackSelectorFactory trackSelectorFactory;

  /**
   * Simple callback to produce an AnalyticsListener for logging purposes.
   */
  public interface EventListenerFactory {

    /**
     * Callee should produce an {@link AnalyticsListener} that will be added to the player's
     * set replacing the default {@link ExtendedEventLogger} that the {@link SimpleExoPlayerFactory}
     * adds by default.  Feel free to subclass the {@link ExtendedEventLogger} or ExoPlayer's
     * own {@link EventLogger}
     *
     * Return null (override the default) and the {@link SimpleExoPlayerFactory} will not add
     * any EventLogger.
     *
     * @param trackSelector - track selector for use only in the constructor for EventLogger
     * @return null or an logger that implements {@link AnalyticsListener}
     */
    default AnalyticsListener createEventLogger(MappingTrackSelector trackSelector) {
      return new ExtendedEventLogger(trackSelector);
    }
  }

  /**
   * Callback interface to allow client to hook player creation.  This callback is provided
   * to allow alternate {@link SimpleExoPlayer.Builder} implementations to construct the player
   *
   * This was implemented to allow Truestream integration without a dependency on specific Truetream
   * version in this module.  Implementers should not save the returned player
   * or use any of the factories passed to {@link #buildSimpleExoPlayer(DefaultRenderersFactory, LoadControl, DefaultTrackSelector, LivePlaybackSpeedControl)}
   * for any other purpose then construction the player.
   */
  public interface AlternatePlayerFactory {

    /**
     * Implementors must construct a SimpleExoPlayer using the arguments
     *
     * @param renderersFactory - renderers factory to pass to the player builder
     * @param loadControl - load control to pass to player builder to pass to the player builder
     * @param trackSelector - trackselector to use for player builder
     * @param livePlaybackSpeedControl livespeedcontrol to pass to the player builder
     * @return a SimpleExoPlayer instance
     */
    SimpleExoPlayer buildSimpleExoPlayer(
        DefaultRenderersFactory renderersFactory,
        LoadControl loadControl,
        DefaultTrackSelector trackSelector,
        LivePlaybackSpeedControl livePlaybackSpeedControl);
  }

  /**
   * Callback to allow creation of a sub-class of {@link DefaultTrackSelector}.
   *
   * It is strongly recommended the callee should not retain a reference to the created object,
   * as it will leak a reference to a {@link Player} object.
   */
  public interface TrackSelectorFactory {
    default DefaultTrackSelector createTrackSelector(Context context, ExoTrackSelection.Factory trackSelectionFactory) {
      return new DefaultTrackSelector(context, trackSelectionFactory);
    }
  }

  /**
   * Preferred mechanism for creating the {@link SimpleExoPlayerFactory}.  Basic builder pattern,
   * e.g. to get all the default simply:
   *
   *   SimpleExoPlayerFactory factory = new SimpleExoPlayerFactory.Builder().build();
   */
  public static class Builder {
    private final Context context;
    private @Nullable PlayerErrorHandlerListener listener;
    private EventListenerFactory factory;
    private SourceFactoriesCreated factoriesCreatedCallback;
    private String userAgentPrefix;
    private boolean mediaCodecAsyncMode;
    private boolean nonDefaultMediaCodecOperationMode = false;
    private AlternatePlayerFactory alternatePlayerFactory = null;
    private TrackSelectorFactory trackSelectorFactory;

    public Builder(Context context) {
      this.context = context;
      this.factory = new EventListenerFactory() {};
    }

    /**
     * Set a playback error handler listener.  This callback is used with error recovery
     * from {@link Player.Listener#onPlayerError(PlaybackException)} )} calls
     * this allows the client visibility into the error handling performed by
     * the {@link DefaultExoPlayerErrorHandler} which can recover from some {@link ExoPlaybackException}'s
     *
     * @param listener listener to call back
     * @return this builder for chaining
     */
    public Builder setPlaybackErrorHandlerListener(PlayerErrorHandlerListener listener) {
      this.listener = listener;
      return this;
    }

    /**
     * Allows client a hook to create their own {@link AnalyticsListener} for logging to replace the
     * default {@link EventLogger} created by the {@link SimpleExoPlayerFactory}
     *
     * @param factory - call back for creating the event logger
     * @return this builder for chaining
     */
    public Builder setEventListenerFactory(EventListenerFactory factory) {
      this.factory = factory;
      return this;
    }

    /**
     * Allows client to be notified (and possibly modify) when the ExoPlayer factory objects
     * around playing a URL (e.g. {@link MediaItem.Builder}) are created.
     *
     * @param factoriesCreated callback interface
     * @return this builder for chaining
     */
    public Builder setSourceFactoriesCreatedCallback(SourceFactoriesCreated factoriesCreated) {
      this.factoriesCreatedCallback = factoriesCreated;
      return this;
    }

    /**
     * Allow client to optionally specify their own factory to produce the {@link SimpleExoPlayer}
     * If not specified the default {@link SimpleExoPlayer.Builder} is used
     *
     * @param alternatePlayerFactory factory call back
     * @return this builder for chaining
     */
    public Builder setAlternatePlayerFactory(AlternatePlayerFactory alternatePlayerFactory) {
      this.alternatePlayerFactory = alternatePlayerFactory;
      return this;
    }

    /**
     * Allows the client to create a sub-class of {@link DefaultTrackSelector}
     * @param trackSelectorFactory - call back interface.
     * @return this builder for chaining
     */
    public Builder setTrackSelectorFactory(TrackSelectorFactory trackSelectorFactory) {
      this.trackSelectorFactory = trackSelectorFactory;
      return this;
    }

    /**
     * Allows the client to specify their own portion of the user-agent header presented for
     * HTTP requests (playlists, segments, etc.).  The generate user-agent will include ExoPlayer
     * version info.  For example, with a prefix "MyApp" the user-agent will be:
     *<pre>
     *   MyApp - [ExoPlayerLib/2.12.3-1.1-dev, Build Number: 84, Git Hash: 109232e5b]
     *</pre>
     *
     * @param prefix prefix string for user agent.
     * @return this builder for chaining
     * @deprecated use {@link Builder#setSourceFactoriesCreatedCallback(SourceFactoriesCreated)} and listen for upstreamDataSourceFactoryCreated
     */
    public Builder setUserAgentPrefix(String prefix) {
      userAgentPrefix = prefix;
      return this;
    }

    /**
     * Set to use MediaCodec in asynchronous mode for audio and video along with threading options
     * to improve performance of 60 FPS video content.  If not est the default is used.
     *
     * @param asyncMode true for "async mode" to improve 60FPS operation, if not set default is used
     * @return this builder for chaining
     */
    public Builder setMediaCodecOperationMode(boolean asyncMode) {
      mediaCodecAsyncMode = asyncMode;
      nonDefaultMediaCodecOperationMode = true;
      return this;
    }

    public SimpleExoPlayerFactory build() {
      SimpleExoPlayerFactory simpleExoPlayerFactory = new SimpleExoPlayerFactory(context);
      simpleExoPlayerFactory.alternatePlayerFactory = this.alternatePlayerFactory;
      simpleExoPlayerFactory.playerErrorHandlerListener = this.listener;
      simpleExoPlayerFactory.eventListenerFactory = this.factory;
      simpleExoPlayerFactory.factoriesCreatedCallback = this.factoriesCreatedCallback;
      simpleExoPlayerFactory.userAgentPrefix = userAgentPrefix;
      simpleExoPlayerFactory.nonDefaultMediaCodecOperationMode = nonDefaultMediaCodecOperationMode;
      simpleExoPlayerFactory.mediaCodecAsyncMode = mediaCodecAsyncMode;
      simpleExoPlayerFactory.trackSelectorFactory = trackSelectorFactory;
      return simpleExoPlayerFactory;
    }
  }
  /**
   * Construct the factory.  This factory is intended to survive as a singleton for the entire lifecycle of
   * the application (create to destroy).  Note that it holds references to the SimpleExoPlayer it creates,
   * so calling {@link #releasePlayer()} is recommended if the application is stopped.
   *
   * This creates a DefaultTrackSelector with the default tunneling mode.  Use the {@link #createPlayer(boolean, boolean)}
   * method to create the player.
   *
   * @param context - android ApplicationContext
   */
  public SimpleExoPlayerFactory(Context context) {
    this.context = context;
    currentParameters = new DefaultTrackSelector.ParametersBuilder(context).build();
  }

  /**
   * Construct the factory, including specifying an event listener that will be called for
   * playback errors (either recovered from internally or not).
   *
   * DEPRECATED use
   * @param context - android ApplicationContext
   * @param listener - error listener
   */
  @Deprecated
  public SimpleExoPlayerFactory(Context context, @Nullable PlayerErrorHandlerListener listener) {
    this(context);
    playerErrorHandlerListener = listener;
  }

  // Factory methods.  Override these if you want to subclass the objects they produce

  /**
   * This method is called just after the player is created to create the handler for
   * {@link com.google.android.exoplayer2.source.MediaSource}.  Override this if you need
   * to produce and manage custom media sources
   *
   * DEPRECATED - use {@link Builder#setSourceFactoriesCreatedCallback(SourceFactoriesCreated)}
   *              and/or {@link Builder#setUserAgentPrefix(String)} should give all the hooks
   *              needed to avoid implementing {@link MediaSourceLifeCycle}
   * @return returns a new {@link DefaultMediaSourceLifeCycle} unless overridden
   */
  @Deprecated
  protected MediaSourceLifeCycle createMediaSourceLifeCycle() {
    return getDefaultMediaSourceLifeCycle();
  }

  private DefaultMediaSourceLifeCycle getDefaultMediaSourceLifeCycle() {
    assert player != null;
    DefaultMediaSourceLifeCycle mediaSourceLifeCycle =
        new DefaultMediaSourceLifeCycle(context, player, hlsPlaylistParserFactory);
    mediaSourceLifeCycle.setMediaSourceEventCallback(callback);
    mediaSourceLifeCycle.factoriesCreated = factoriesCreatedCallback == null
        ? new SourceFactoriesCreated() {} : factoriesCreatedCallback;
    mediaSourceLifeCycle.userAgentPrefix = userAgentPrefix == null ? "TiVoExoPlayer" : userAgentPrefix;
    return mediaSourceLifeCycle;
  }

  /**
   * Creates an error handler.  Default is the {@link DefaultExoPlayerErrorHandler}, to add your
   * own error handling or reporting extend this class and return your class here.  Make sure
   * to honor the @CallSuper annotations to ensure proper error recovery operation.
   *
   * @return default returns {@link DefaultExoPlayerErrorHandler}, return a subclass thereof if you override
   */
  protected DefaultExoPlayerErrorHandler createPlayerErrorHandler() {
    List<PlaybackExceptionRecovery> errorHandlers = getDefaultPlaybackExceptionHandlers();

    DefaultExoPlayerErrorHandler defaultExoPlayerErrorHandler =
            new DefaultExoPlayerErrorHandler(errorHandlers, playerErrorHandlerListener);
    return defaultExoPlayerErrorHandler;
  }

  /**
   * If you override {@link #createPlayerErrorHandler()}, use this method to get
   * the default set of {@link PlaybackExceptionRecovery}
   * handlers to pass to the {@link DefaultExoPlayerErrorHandler} you have extended.  For example:
   *
   * <pre>
   *   ...
   *
   *   protected AnalyticsListener createPlayerErrorHandler() {
   *     return new MyExoPlayerErrorHanlder(getDefaultPlaybackExceptionHandlers();
   *   }
   *
   *   protected void playerErrorProcessed(EventTime eventTime, ExoPlaybackException error, boolean recovered) {
   *     log the error, pass to your clients, etc...
   *   }
   *
   *   ...
   * </pre>
   *
   *
   * @return the default list of playback error handlers.
   */
  protected List<PlaybackExceptionRecovery> getDefaultPlaybackExceptionHandlers() {
    ArrayList handlers = new ArrayList(Arrays.asList(
        new AudioTrackInitPlayerErrorHandler(this),
        new BehindLiveWindowExceptionRecovery(this),
        new StuckPlaylistErrorRecovery(this),
        new NetworkLossPlayerErrorHandler(this, context),
        new HdmiPlayerErrorHandler(this, context)));
    if ( trackSelector instanceof SyncVideoTrackSelector ) {
      handlers.add(new NoPcmAudioErrorHandler(this, (SyncVideoTrackSelector) trackSelector));
    }
    return handlers;
  }

  // Public API Methods

  /**
   * Release the player.  Call this when your application is stopped by Android.
   *
   * This frees up all resources associcated with the {@link SimpleExoPlayer} and {@link TrickPlayControl}.
   *
   */
  @CallSuper
  public void releasePlayer() {
    if (player != null) {
      player.release();
      if (trickPlayControl != null) {
        trickPlayControlFactory.destroyTrickPlayControl(trickPlayControl);
        trickPlayControl = null;
      }
      player = null;
      mediaSourceLifeCycle.releaseResources();
      mediaSourceLifeCycle = null;
      trackSelector = null;
      playerErrorHandler.releaseResources();
      playerErrorHandler = null;
    }
  }

  /**
   * Create a new {@link SimpleExoPlayer} with the common defaults (play when ready, no
   * tunneling)
   *
   * @return the newly created player.  Also always available via {@link #getCurrentPlayer()}
   */
  public SimpleExoPlayer createPlayer() {
    return createPlayer(true, false);
  }

  /**
   * Create a new {@link SimpleExoPlayer}.  This is the partner method to {link {@link #releasePlayer()}}
   *
   * Call this when your application is started.
   *
   * This API takes the defaults for {@link DefaultLoadControl} which can be changed using the sister API
   * {@link #createPlayer(boolean, boolean, DefaultLoadControl.Builder)}
   *
   * @param playWhenReady sets the play when ready flag.
   * @param defaultTunneling - default track selection to prefer tunneling (can turn this off {@link #setTunnelingMode(boolean)}}
   * @return the newly created player.  Also always available via {@link #getCurrentPlayer()}
   */
  @CallSuper
  public SimpleExoPlayer createPlayer(boolean playWhenReady, boolean defaultTunneling) {
    DefaultLoadControl.Builder builder = new DefaultLoadControl.Builder();

    builder.setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,   // Player will start fetching if buffered falls less than this
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,   // Player will stop all fetching after this. Sets max that is held
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS);

    return createPlayer(playWhenReady, defaultTunneling, builder);
  }

  /**
   * Create a new {@link SimpleExoPlayer}.  This is the partner method to {link {@link #releasePlayer()}}
   *
   * Call this when your application is started.  This API allows control of the buffering levels by passing in
   * a LoadControl builder.  It is recommended not to change anything other than the
   * {@link DefaultLoadControl.Builder#setBufferDurationsMs(int, int, int, int)}. These are described in the ExoPlayer
   * Javadoc.  Quick summary on setBufferDuration follows:
   *
   * The first parameter set where the player starts fetching again (if buffering falls below this
   * threshold value, the player will load segments),  the second parameter sets the max the player
   * will ever buffer.  Note, for Live these limits are restricted by distance to the live edge.
   * For mobile playback, the spread between MIN and MAX allows radio off time once the player reaches
   * MAX it will not turn the radio on and start fetching again till it hits MIN, this helps battery life
   *
   * The 3rd and 4th parameters set how much buffer the player requires following fresh playback start or
   * after re-buffering following a stall.  Setting the 3rd parameter lower reduces channel change latency at
   * the cost of higher risk of initial re-buffering.
   *
   * @param playWhenReady sets the play when ready flag.
   * @param defaultTunneling - default track selection to prefer tunneling (can turn this off {@link #setTunnelingMode(boolean)}}
   * @param controlBuilder - DefaultLoadControl.Builder, allows changing buffering behavior for how often player fetches and what
   *                       the player keeps {@link DefaultLoadControl.Builder#setBufferDurationsMs(int, int, int, int)}
   * @return the newly created player.  Also always available via {@link #getCurrentPlayer()}
   */
  @CallSuper
  public SimpleExoPlayer createPlayer(boolean playWhenReady, boolean defaultTunneling, DefaultLoadControl.Builder controlBuilder) {
    assert controlBuilder != null;
    if (player != null) {
      releasePlayer();
    }

    trickPlayControlFactory = new TrickPlayControlFactory();
    ExoTrackSelection.Factory trackSelectionFactory = trickPlayControlFactory.getTrackSelectionFactory();
    trackSelector = createTrackSelector(defaultTunneling, context, trackSelectionFactory);
    trickPlayControl = trickPlayControlFactory.createTrickPlayControl(trackSelector);
    hlsPlaylistParserFactory = trickPlayControl.createHlsPlaylistParserFactory(true);

    DefaultRenderersFactory renderersFactory = trickPlayControl.createRenderersFactory(context);

    if (nonDefaultMediaCodecOperationMode) {
      renderersFactory.experimentalSetAsynchronousBufferQueueingEnabled(mediaCodecAsyncMode);
      boolean forceAsyncQueueingSynchronizationWorkaround =
          !Util.MANUFACTURER.toLowerCase().contains("amazon") && mediaCodecAsyncMode;
      renderersFactory.experimentalSetForceAsyncQueueingSynchronizationWorkaround(forceAsyncQueueingSynchronizationWorkaround);
    }

    LoadControl loadControl = trickPlayControl.createLoadControl(controlBuilder.createDefaultLoadControl());

    // Wrap default with our own version that logs useful bits, TODO factory method for default
    LivePlaybackSpeedControl livePlaybackSpeedControl =
        new AugmentedLivePlaybackSpeedControl(
            new LoggingLivePlaybackSpeedControl(
                new DefaultLivePlaybackSpeedControl.Builder().build())
        );
    if (alternatePlayerFactory != null) {
      player = alternatePlayerFactory.buildSimpleExoPlayer(
          renderersFactory,
          loadControl,
          trackSelector,
          livePlaybackSpeedControl);
    } else {
      player = new SimpleExoPlayer.Builder(context, renderersFactory)
          .setTrackSelector(trackSelector)
          .setLoadControl(loadControl)
          .setLivePlaybackSpeedControl(livePlaybackSpeedControl)
          .build();
    }

    mediaSourceLifeCycle = createMediaSourceLifeCycle();

    trickPlayControl.setPlayer(player);

    AnalyticsListener logger = eventListenerFactory.createEventLogger(trackSelector);
    if (logger != null) {
      player.addAnalyticsListener(logger);
    }
    playerErrorHandler = createPlayerErrorHandler();
    playerErrorHandler.setCurrentTrackSelector(trackSelector);
    player.addListener(playerErrorHandler);
    player.setPlayWhenReady(playWhenReady);
    return player;
  }

  /**
   * Inject a test ExoPlayer created by TestExoPlayerBuilder for some basic tests of logic in
   * this factory, this is enough for testing the renderer control and track selecton logic in
   * this class, but nothing else as the player is not linked to media source creating or trick play
   *
   * @param testPlayer a test SimpleExoPlayer
   * @param testSelector and it's track selector.
   */
  @VisibleForTesting
  void injectForTesting(SimpleExoPlayer testPlayer,
      DefaultTrackSelector testSelector) {
    player = testPlayer;
    trackSelector = testSelector;
  }

  /**
   * Start playback of the specified URL on the current ExoPlayer.  Must have previously
   * called {@link #createPlayer(boolean, boolean)}
   *
   * @param url - URL to play
   * @param drmInfo - DRM information
   * @param enableChunkless - flag to enable chunkless prepare, TODO - will make this default
   * @throws UnrecognizedInputFormatException - if the URI is not in a supported container format.
   *
   * Deprecated, enableChunkless can be done by using the setSourceFactoriesCreatedCallback, see below
   * <pre>
   *           .setSourceFactoriesCreatedCallback(new SourceFactoriesCreated() {
   *               @Override
   *               public void factoriesCreated(@C.ContentType int type, MediaItem.Builder itemBuilder, MediaSourceFactory factory) {
   *                 switch (type) {
   *                   case C.TYPE_HLS:
   *                     HlsMediaSource.Factory hlsFactory = (HlsMediaSource.Factory) factory;
   *                     boolean allowChunkless = getIntent().getBooleanExtra(CHUNKLESS_PREPARE, false);
   *                     break;
   *                  ...
   *                 }
   *               }
   *             })
   *
   *</pre>
   */
  @Deprecated
  public void playUrl(Uri url, DrmInfo drmInfo, boolean enableChunkless) throws UnrecognizedInputFormatException {
    mediaSourceLifeCycle.playUrl(url, C.POSITION_UNSET, drmInfo, enableChunkless);
  }

  /**
   * Start playback of the specified URL on the current ExoPlayer.  Must have previously
   * called {@link #createPlayer(boolean, boolean)}
   *
   * @param url - URL to play
   * @param startPosUs - starting position, or {@link C#POSITION_UNSET} for the default (live offset or 0 for VOD)
   * @param drmInfo - DRM information
   * @param enableChunkless - flag to enable chunkless prepare, TODO - will make this default
   * @throws UnrecognizedInputFormatException - if the URI is not in a supported container format.
   *
   * Deprecated, see {@link #playUrl(Uri, DrmInfo)}  etc for alternative
   */
  @Deprecated
  public void playUrl(Uri url, long startPosUs, DrmInfo drmInfo, boolean enableChunkless) throws UnrecognizedInputFormatException {
    mediaSourceLifeCycle.playUrl(url, startPosUs, drmInfo, enableChunkless);
  }


  /**
   * Start playback of the specified URL on the current ExoPlayer.  Must have previously
   * called {@link #createPlayer()} or one of its variant signatures.  Playback starts
   * at the default position (0 for VOD or the live edge for live)
   *
   * @param url - URL to play
   * @param drmInfo - DRM information, use {@link DrmInfo#NO_DRM} for clear
   * @throws UnrecognizedInputFormatException - if the URI is not in a supported container format.
   */
  public void playUrl(Uri url, DrmInfo drmInfo) throws UnrecognizedInputFormatException {
    playUrl(url, drmInfo, C.TIME_UNSET);
  }

  /**
   * Same as {@link #playUrl(Uri, DrmInfo)}} only specfiying a initial playback start position
   *
   * @param url - URL to play
   * @param drmInfo - DRM information, use {@link DrmInfo#NO_DRM} for clear
   * @param startPosMs - starting position (milliseconds) must be 0 - duration of playlist (or live offset)
   * @throws UnrecognizedInputFormatException - if the URI is not in a supported container format.
   */
  public void playUrl(Uri url, DrmInfo drmInfo, long startPosMs) throws UnrecognizedInputFormatException {
    mediaSourceLifeCycle.playUrl(url, startPosMs, drmInfo);
  }


  /**
   * Same as {@link #playUrl(Uri, DrmInfo, long)}} only allows changing the default for playWhenReady that
   * was specified in the call to {@link #createPlayer(boolean, boolean)}.  Note this change to playWhenReady
   * is persistent.
   *
   * @param url - URL to play
   * @param drmInfo - DRM information, use {@link DrmInfo#NO_DRM} for clear
   * @param startPosMs - starting position (milliseconds) must be 0 - duration of playlist (or live offset)
   *                   specifying {@link C#TIME_UNSET} will start at the default position
   * @param startPlaying - override the the initial setting for playWhenReady, if false will start paused
   * @throws UnrecognizedInputFormatException - if the URI is not in a supported container format.
   */
  public void playUrl(Uri url, DrmInfo drmInfo, long startPosMs, boolean startPlaying) throws UnrecognizedInputFormatException {
    mediaSourceLifeCycle.playUrl(url, startPosMs, startPlaying, drmInfo);
  }

  /**
   *
   * @param callback {@link MediaSourceEventCallback} implementation to be called or null to remove reference
   */
  public void setMediaSourceEventCallback(@Nullable MediaSourceEventCallback callback) {
    this.callback = callback;
    if (mediaSourceLifeCycle != null) {
      mediaSourceLifeCycle.setMediaSourceEventCallback(callback);
    }
  }

  // Track Selection

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  @Override
  public void setTunnelingMode(boolean enableTunneling) {
    DefaultTrackSelector.ParametersBuilder builder = currentParameters.buildUpon();
    builder.setTunnelingEnabled(enableTunneling);

    commitTrackSelectionParameters(builder);
  }


  @Override
  public boolean isTunnelingMode() {
    return player != null && player.getPlaybackState() != Player.STATE_ENDED
        && currentParameters.tunnelingEnabled;
  }

  /**
   * Sets the defaults for close caption display (on/off, preferred language)
   *
   * The ExoPlayer {@link DefaultTrackSelector} selects the caption track to the text
   * renderer if it matches the language.
   *
   * Also, sets the flag to allow "Unknown" language for poorly authored metadata
   * (lack of playlist metadata for language)
   *
   * @param enable - true to turn on captions, false to turn them off
   * @param preferLanguage - optional, if non null (eg "en") will attempt to match
   */
  public void setCloseCaption(boolean enable, @Nullable String preferLanguage) {

    DefaultTrackSelector.ParametersBuilder builder = currentParameters.buildUpon();

    /* Poorly authored metadata seems to leave off the language, this flag allows unknown
     * language to be selected if no track matches the preferred language.
     */
    builder.setSelectUndeterminedTextLanguage(enable);

    /* TrackSelection will weight this language to the top if it is seen */
    if (enable) {
      builder.setPreferredTextLanguage(preferLanguage);
    } else {
      builder.setPreferredTextLanguage(C.LANGUAGE_UNDETERMINED);
    }

    /* Lastly, disable the track by 'masking' the selection attribute flags.  Otherwise we
     * turn on select default, autoselect and force.  These flags
     * are described here: https://developer.apple.com/documentation/http_live_streaming/hls_authoring_specification_for_apple_devices
     */
    @C.SelectionFlags int maskFlags = enable ? 0
        : C.SELECTION_FLAG_AUTOSELECT | C.SELECTION_FLAG_DEFAULT | C.SELECTION_FLAG_FORCED;

    builder.setDisabledTextTrackSelectionFlags(maskFlags);

    commitTrackSelectionParameters(builder);
  }

  /**
   * Audio track selection will pick the best audio track that is the closes match to this language by default.
   *
   * @param preferedAudioLanguage - langauge string for audio (e.g. Locale.getDefault().getLanguage())
   */
  public void setPreferredAudioLanguage(String preferedAudioLanguage) {
    DefaultTrackSelector.ParametersBuilder builder = currentParameters.buildUpon();
    builder.setPreferredAudioLanguage(preferedAudioLanguage);
    commitTrackSelectionParameters(builder);
  }

  /**
   * Get the current parameters for track selection.   These start out as matching
   * {@link DefaultTrackSelector.Parameters#DEFAULT}, then any changes to the track selection
   * by this objects track selection API methods updates them
   * (NOTE values are <b>not</b> saved by any use of {@link #getTrackSelector()} to alter parameters)
   *
   * @return the last set of parameters values, or the defaults.  Preserved across player release {@link #releasePlayer()})
   */
  public DefaultTrackSelector.Parameters getCurrentParameters() {
    return currentParameters;
  }

  /**
   * Update the current parameters with any external changes.
   *
   * The preferred method is to use track selection API methods like {@link #setPreferredAudioLanguage(String)}.
   * However if you do use {@link #getCurrentParameters()}, mutate them, then call this method, your
   * mutations are merged into the parameters/
   *
   * @param parameters used to update the last saved parameters
   */
  public void setCurrentParameters(DefaultTrackSelector.Parameters parameters) {
    this.currentParameters = parameters.buildUpon().build();
  }

  /**
   * Get TrackInfo objects for all the text tracks.
   *
   * All tracks can include tracks which the player cannot player.
   *
   * @return list of all text in the current MediaSource.
   */
  public List<TrackInfo> getAvailableTextTracks() {
    return getSelectableTrackInfoForTrackType(C.TRACK_TYPE_TEXT);
  }

  /**
   * Get TrackInfo objects for all the audio tracks.
   *
   * All tracks can include tracks which the player cannot player.
   *
   * @return list of all text in the current MediaSource.
   */
  public List<TrackInfo> getAvailableAudioTracks() {
    return getSelectableTrackInfoForTrackType(C.TRACK_TYPE_AUDIO);
  }

  /**
   * Set rendering of specified track type on or off.  Can use this to disable, for example,
   * rendering of CC by using {@link C#TRACK_TYPE_TEXT}
   *
   * This change is not remembered in the {@link #getCurrentParameters()}
   *
   * @param trackType the track to disable rendering for, one of the {@link C} TRACK_TYPE_x constants
   * @param trackState true to render, false to disable rendering
   */
  public void setRendererState(int trackType, boolean trackState) {
    DefaultTrackSelector.ParametersBuilder builder = currentParameters.buildUpon();
    if (trackSelector != null) {
      for (int i = 0; i < player.getRendererCount(); i++) {
        if (player.getRendererType(i) == trackType) {
            builder.setRendererDisabled(i, trackState);
        }
      }
      trackSelector.setParameters(builder);
    }
  }

  /**
   * Return TrackInfo objects for the tracks matching the indicated trackType, one of the {@code TRACK_TYPE_*}
   * constants.  For example:
   *     getSelectableTrackInfoForTrackType(C.TRACK_TYPE_TEXT)
   * Will return text tracks (both subtitle and closed caption tracks, anything that con be rendered as
   * text.
   *
   * One (or more for adaptive tracks) of the returned TrackInfo objects may have {@link TrackInfo#isSelected}
   * set true. These selected track[s] are enabled to be played by the renderer, assuming it is enabled. This selected state is
   * either the result of default Constraint Based Selection
   * (see <a href="https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/trackselection/DefaultTrackSelector.html">DefaultTrackSelector</a>)
   * or if a selection override was set in place using {@link #selectTrack(TrackInfo)}
   *
   * @param trackType The {@code TRACK_TYPE_*} constant for the requested renderer.
   * @return List of {@link TrackInfo} that match the type, one or more may be marked {@link TrackInfo#isSelected}
   */
  public List<TrackInfo> getSelectableTrackInfoForTrackType(int trackType) {
    List<TrackInfo> availableTracks = Collections.emptyList();
    if (trackSelector != null || player == null) {
      availableTracks = getSelectableTrackInfoForTrackType(trackType, trackSelector, player);
    }

    return availableTracks;
  }

  /**
   * Static equivalent for {@link #getSelectableTrackInfoForTrackType(int)}, in place for ExoPlayerPlayer migration
   *
   * @param trackType The {@code TRACK_TYPE_*} constant for the requested renderer.
   * @param defaultTrackSelector the current DefaultTrackSelector
   * @param player the current player
   * @return List of {@link TrackInfo} that match the type, one or more may be marked {@link TrackInfo#isSelected}
   */
  public static List<TrackInfo> getSelectableTrackInfoForTrackType(int trackType,
      DefaultTrackSelector defaultTrackSelector, SimpleExoPlayer player) {
    List<TrackInfo> availableTracks = new ArrayList<>();
    TrackSelectionArray selections = player.getCurrentTrackSelections();
    @Nullable MappingTrackSelector.MappedTrackInfo mappedTrackInfo = defaultTrackSelector.getCurrentMappedTrackInfo();
    if (mappedTrackInfo != null) {
      int rendererIndex = getRendererIndex(trackType, player);
      TrackSelection selection = selections.get(rendererIndex);
      TrackGroupArray rendererTrackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
      for (int groupIndex = 0; groupIndex < rendererTrackGroups.length; groupIndex++) {
        TrackGroup trackGroup = rendererTrackGroups.get(groupIndex);
        for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
          Format format = trackGroup.getFormat(trackIndex);
          boolean isSelected = selection != null
              && selection.getTrackGroup() == trackGroup
              && selection.indexOf(trackIndex) != C.INDEX_UNSET;
          int formatSupport = mappedTrackInfo.getTrackSupport(rendererIndex, groupIndex, trackIndex);
          availableTracks.add(new TrackInfo(format, isSelected, formatSupport));
        }
      }
    }
    return availableTracks;
  }

  /**
   * Method does not work properly, switch to {@link #getSelectableTrackInfoForTrackType(int)}
   *
   * @param matching - {@link Predicate} used to filter the desired formats.
   * @return list of all tracks matching the predicate in the current MediaSource.
   */
  @Deprecated
  public List<TrackInfo> getMatchingAvailableTrackInfo(Predicate<Format> matching) {
    List<TrackInfo> availableTracks = new ArrayList<>();
    return availableTracks;
  }

  /**
   * Filter a the list of TrackInfo objects to include only those which can be played by their associated
   * Renderer.
   *
   * Track selection will only pick a track if it can be played on the associated Renderer or their are
   * if {@link DefaultTrackSelector.Parameters#exceedRendererCapabilitiesIfNecessary} is set (the default) and
   * there are no other playable available Formats for the Renderer.   The methods that return
   * avialable tracks ({@link #getAvailableAudioTracks()} or {@link #getAvailableTextTracks()}) are used for
   * a track selection dialog to override the default track selection, as such they allow selecting tracks
   * that may not play.  This filter method allows a UI to only show tracks that the Renderer reports it
   * will play.
   *
   * @param originalTrackInfoList list to filter, from {@link #getAvailableAudioTracks()} or {@link #getAvailableTextTracks()}
   * @return filtered list of tracks or empty list if the player is not yet created and run first track selection
   */
  public List<TrackInfo> getTracksFilteredForRendererSupport(List<TrackInfo> originalTrackInfoList) {
    List<TrackInfo> filteredTrackInfoList = new ArrayList<>();

    if (trackSelector == null || originalTrackInfoList == null) {
      Log.e(TAG, "getTracksFilteredForRendererSupport() : trackSelector or original list is null, returning empty list");
      return filteredTrackInfoList;
    }

    MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
    if (mappedTrackInfo == null) {
      Log.e(TAG, "getTracksFilteredForRendererSupport() : " +
              "mappedTrackInfo is null. returning empty list. Call this after player selections are made, probably from onTracksChanged.");
      return filteredTrackInfoList;
    }

    for (TrackInfo trackInfo : originalTrackInfoList) {
      Format format = trackInfo.format;
      int rendererIndex = getRendererIndex(trackInfo.type, player);
      boolean isFormatSupported = false;
      TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(rendererIndex);
      for (int groupIndex = 0; groupIndex < trackGroupArray.length; groupIndex++) {
        TrackGroup trackGroup = trackGroupArray.get(groupIndex);
        for (int formatIndex = 0; formatIndex < trackGroup.length; formatIndex++) {
          if (format.equals(trackGroup.getFormat(formatIndex))) {
            int formatSupport = mappedTrackInfo.getTrackSupport(rendererIndex, groupIndex, formatIndex);
            isFormatSupported = (formatSupport == C.FORMAT_HANDLED)
                    || (trackSelector.getParameters().exceedRendererCapabilitiesIfNecessary && formatSupport == C.FORMAT_EXCEEDS_CAPABILITIES);
          }
        }
      }
      if (isFormatSupported) {
        filteredTrackInfoList.add(trackInfo);
      } else {
        Log.w(TAG, "could not find format mapped in mappedTrackInfo. Format = " + format);
      }
    }

    return filteredTrackInfoList;
  }

  /**
   * Force select the track specified, overriding any constraint based selection or any previous
   * selection.  NOTE this requires the player has been created (after {@link #createPlayer()} has
   * been called and before {@link #releasePlayer()})
   *
   * @param trackInfo the {@link TrackInfo} for the track to select (the {@link Format} keys selection)
   * @return true if the track with the indicated Format was found and selected.
   */
  public boolean selectTrack(TrackInfo trackInfo) {
    boolean wasSelected = false;
    MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();


    // If we have a player and have mapped any tracks to renderers
    if (player != null && mappedTrackInfo != null) {

      // Find the renderer for the TrackInfo we are selecting, then find the TrackGroups mapped to
      // It (for audio and text there should only be one trackgroup in the array)
      //
      for (int rendererIndex = 0; rendererIndex < player.getRendererCount(); rendererIndex++) {
        if (player.getRendererType(rendererIndex) == trackInfo.type) {
          TrackGroupArray rendererTrackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
          if (rendererTrackGroups != null) {
            DefaultTrackSelector.SelectionOverride override =
                makeOverrideSelectingFormat(rendererTrackGroups, trackInfo.format);
            if (override != null) {
              DefaultTrackSelector.ParametersBuilder builder = trackSelector.buildUponParameters();
              builder.clearSelectionOverrides(rendererIndex);
              builder.setSelectionOverride(rendererIndex, rendererTrackGroups, override);
              commitTrackSelectionParameters(builder);
              wasSelected =  true;
            }
          }
        }
      }
    }
    return wasSelected;
  }

  /**
   * Make a single track selection (only useful really for {@link com.google.android.exoplayer2.trackselection.FixedTrackSelection})
   * override to use for forced track selection.
   *
   * @param rendererTrackGroups - the renderer to search for the track in
   * @param format - the track to find.
   * @return override or null if not found
   */
  @Nullable
  private DefaultTrackSelector.SelectionOverride makeOverrideSelectingFormat(TrackGroupArray rendererTrackGroups, Format format) {
    DefaultTrackSelector.SelectionOverride override = null;
    for (int groupIndex = 0; override == null && groupIndex < rendererTrackGroups.length; groupIndex++) {
      TrackGroup group = rendererTrackGroups.get(groupIndex);
      for (int trackIndex = 0; override == null && trackIndex < group.length; trackIndex++) {
        if (format.equals(group.getFormat(trackIndex))) {
          override = new DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex);
        }
      }
    }
    return override;
  }

  // Getters

  /**
   * Get the current active {@link SimpleExoPlayer} instance.
   *
   * @return current player, will be null if releasePlayer() was called without a subsequent create
   */
  @Nullable
  public SimpleExoPlayer getCurrentPlayer() {
    return player;
  }

  /**
   * Get the active {@link TrickPlayControl} control associated with the player.
   *
   * @return current TrickPlayControl, will be null if releasePlayer() was called without a subsequent create
   */
  @Nullable
  public TrickPlayControl getCurrentTrickPlayControl() {
    return trickPlayControl;
  }

  /**
   * This is used by the ExoPlayer demo to facilitate using the TrackSelectionDialogBuilder in
   * the library-ui.
   *
   * Using higher level methods like {@link #setCloseCaption} is preferred, but this method is available
   * to allow clients to perform any track selections not supported by this class.
   *
   * DEPPRECATED - use {@link #getCurrentParameters()} if something is not supported by the track control API in this class
   * @return the current track selector, this will only be available after {@link #createPlayer(boolean, boolean)} is called
   */
  @Deprecated
  public DefaultTrackSelector getTrackSelector() {
    return trackSelector;
  }

  /**
   * Initialize ExoPlayer logging.  The build default is {@link Log#LOG_LEVEL_ALL}.  Recommended
   * default is {@link Log#LOG_LEVEL_INFO}
   *
   * Call this at least when the app is started to allow the properties file to override.
   *
   * @param context - Android application context (used to find files dir)
   * @param logLevelInfo - set the default loglevel (properties file in [app-files]/exo.properties can
   *                       override this.
   */
  public static void initializeLogging(Context context, int logLevelInfo) {
    Log.setLogLevel(logLevelInfo);
    File appFiles = context.getExternalFilesDir(null);
    File exoProperties = new File(appFiles, "exo.properties");
    if (exoProperties.canRead()) {
      try {
        FileInputStream inputStream = new FileInputStream(exoProperties);
        Properties properties = new Properties();
        properties.load(inputStream);
        Object logLevel = properties.get("debugLevel");
        if (logLevel != null) {
          Log.setLogLevel(Integer.valueOf(logLevel.toString()));
          Log.i("ExoPlayer", "log level set to " + logLevel);
        }
      } catch (IOException e) {
        Log.w("ExoPlayer", "defaulting logging to warning level, properties file read failed.");
      }
    } else {
      Log.i("ExoPlayer", "defaulting logging to level: " + logLevelInfo +  ", properties file not found or read failed.");
    }
  }

  /**
   * Returns true if the {@link Format} specified is an audio track format.
   *
   * @param format {@link Format} object to test.
   * @return true if the format is audio
   */
  public static boolean isAudioFormat(Format format) {
    boolean isAudio = false;

    int trackType = MimeTypes.getTrackType(format.sampleMimeType);
    if (trackType == C.TRACK_TYPE_AUDIO) {
      isAudio = true;
    } else {
      isAudio = MimeTypes.getAudioMediaMimeType(format.codecs) != null;
    }
    return isAudio;
  }

  /**
   * Returns true if the {@link Format} specified is an text track format
   *
   * @param format {@link Format} object to test.
   * @return true if format is text
   */
  public static boolean isTextFormat(Format format) {
    return MimeTypes.getTrackType(format.sampleMimeType) == C.TRACK_TYPE_TEXT;
  }

  // Internal methods

  /**
   * Get the current Renderer's index for the specified C.TRACK_TYPE_x value
   *
   * @param trackType C.TRACK_TYPE_ value
   * @param player the current {@link SimpleExoPlayer}, must be non-null
   * @return value or -1 if no renderer was created for the type
   */
  private static int getRendererIndex(int trackType, SimpleExoPlayer player) {
    int index = C.INDEX_UNSET;
    for (int i = 0; i < player.getRendererCount() && index == C.INDEX_UNSET; i++) {
      if (player.getRendererType(i) == trackType) {
        index = i;
      }
    }
    return index;
  }

  private TrackSelection getTrackSelectionForGroup(TrackGroup group) {
    TrackSelection selection = null;
    TrackSelectionArray selectionArray = player.getCurrentTrackSelections();
    for (int i = 0; i < selectionArray.length && selection == null; i++) {
      TrackSelection trackSelection = selectionArray.get(i);
      if (trackSelection != null && group.equals(trackSelection.getTrackGroup())) {
        selection = trackSelection;
      }
    }
    return selection;
  }


  @Override
  public void retryPlayback() {
    if (player != null) {
      Log.i(TAG, "retryPlayback() - issuing prepare() to player.");
      player.prepare();
    }
  }

  private void commitTrackSelectionParameters(DefaultTrackSelector.ParametersBuilder builder) {
    currentParameters = builder.build();
    if (trackSelector != null) {
      trackSelector.setParameters(currentParameters);
    }
  }

  private DefaultTrackSelector createTrackSelector(boolean enableTunneling, Context context, ExoTrackSelection.Factory trackSelectionFactory) {
    // Get a builder with current parameters then set/clear tunnling based on the intent
    //
    boolean usingSavedParameters =
        ! currentParameters.equals(new DefaultTrackSelector.ParametersBuilder(context).build());

    DefaultTrackSelector trackSelector = this.trackSelectorFactory.createTrackSelector(context, trackSelectionFactory);
    DefaultTrackSelector.ParametersBuilder builder = currentParameters.buildUpon();

    builder.setTunnelingEnabled(enableTunneling);

    // Selection overrides can't persist across player rebuild, they are track index based
    //
    if (usingSavedParameters) {
      builder.clearSelectionOverrides();
    }
    currentParameters = builder.build();
    trackSelector.setParameters(currentParameters);
    return trackSelector;
  }

}
