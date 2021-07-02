package com.tivo.exoplayer.library;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.UnrecognizedInputFormatException;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.trickplay.TrickPlayControlFactory;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.base.Predicate;
import com.tivo.exoplayer.library.errorhandlers.AudioTrackInitPlayerErrorHandler;
import com.tivo.exoplayer.library.errorhandlers.DefaultExoPlayerErrorHandler;
import com.tivo.exoplayer.library.errorhandlers.HdmiPlayerErrorHandler;
import com.tivo.exoplayer.library.errorhandlers.PlaybackExceptionRecovery;
import com.tivo.exoplayer.library.errorhandlers.PlayerErrorHandlerListener;
import com.tivo.exoplayer.library.errorhandlers.PlayerErrorRecoverable;
import com.tivo.exoplayer.library.errorhandlers.StuckPlaylistErrorRecovery;
import com.tivo.exoplayer.library.logging.ExtendedEventLogger;
import com.tivo.exoplayer.library.tracks.TrackInfo;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

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
   * Android application context for access to Android
   */
  private final Context context;

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
   * Preferred mechanism for creating the {@link SimpleExoPlayerFactory}.  Basic builder pattern,
   * e.g. to get all the default simply:
   *
   *   SimpleExo
   */
  public static class Builder {
    private final Context context;
    private @Nullable PlayerErrorHandlerListener listener;
    private EventListenerFactory factory;

    public Builder(Context context) {
      this.context = context;
      this.factory = new EventListenerFactory() {};
    }

    /**
     * Set a playback error handler listener.  This callback is used with error recovery
     * from {@link Player.EventListener#onPlayerError(ExoPlaybackException)} calls
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

    public SimpleExoPlayerFactory build() {
      SimpleExoPlayerFactory simpleExoPlayerFactory = new SimpleExoPlayerFactory(context);
      simpleExoPlayerFactory.playerErrorHandlerListener = this.listener;
      simpleExoPlayerFactory.eventListenerFactory = this.factory;
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
   * @return returns a new {@link DefaultMediaSourceLifeCycle} unless overridden
   */
  protected MediaSourceLifeCycle createMediaSourceLifeCycle() {
    assert player != null;
    DefaultMediaSourceLifeCycle mediaSourceLifeCycle = new DefaultMediaSourceLifeCycle(player, context);
    mediaSourceLifeCycle.setMediaSourceEventCallback(callback);
    return mediaSourceLifeCycle;
  }

  /**
   * Creates an error handler.  Default is the {@link DefaultExoPlayerErrorHandler}, to add your
   * own error handling or reporting extend this class and return your class here.  Make sure
   * to honor the @CallSuper annotations to ensure proper error recovery operation.
   *
   * @param mediaSourceLifeCycle current {@link MediaSourceLifeCycle}, this is one of the error handlers
   * @return default returns {@link DefaultExoPlayerErrorHandler}, return a subclass thereof if you override
   */
  protected DefaultExoPlayerErrorHandler createPlayerErrorHandler(MediaSourceLifeCycle mediaSourceLifeCycle) {
    List<PlaybackExceptionRecovery> errorHandlers = getDefaultPlaybackExceptionHandlers(
        mediaSourceLifeCycle);

    DefaultExoPlayerErrorHandler defaultExoPlayerErrorHandler =
            new DefaultExoPlayerErrorHandler(errorHandlers, playerErrorHandlerListener);
    return defaultExoPlayerErrorHandler;
  }

  /**
   * If you override {@link #createPlayerErrorHandler(MediaSourceLifeCycle)}, use this method to get
   * the default set of {@link PlaybackExceptionRecovery}
   * handlers to pass to the {@link DefaultExoPlayerErrorHandler} you have extended.  For example:
   *
   * <pre>
   *   ...
   *
   *   protected AnalyticsListener createPlayerErrorHandler(MediaSourceLifeCycle mediaSourceLifeCycle) {
   *     return new MyExoPlayerErrorHanlder(getDefaultPlaybackExceptionHandlers(mediaSourceLifeCycle);
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
   * @param mediaSourceLifeCycle - the current MediaSourceLifeCycle
   * @return the default list of playback error handlers.
   */
  protected List<PlaybackExceptionRecovery> getDefaultPlaybackExceptionHandlers(
      MediaSourceLifeCycle mediaSourceLifeCycle) {
    return Arrays.asList(
        new AudioTrackInitPlayerErrorHandler(this),
        mediaSourceLifeCycle,
        new StuckPlaylistErrorRecovery(this),
        new HdmiPlayerErrorHandler(this, context));
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
    TrackSelection.Factory trackSelectionFactory = trickPlayControlFactory.getTrackSelectionFactory();
    trackSelector = createTrackSelector(defaultTunneling, context, trackSelectionFactory);
    trickPlayControl = trickPlayControlFactory.createTrickPlayControl(trackSelector);
    RenderersFactory renderersFactory = trickPlayControl.createRenderersFactory(context);

    LoadControl loadControl = trickPlayControl.createLoadControl(controlBuilder.createDefaultLoadControl());

    player = new SimpleExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build();


    mediaSourceLifeCycle = createMediaSourceLifeCycle();

    trickPlayControl.setPlayer(player);
    player.setPlayWhenReady(playWhenReady);

    AnalyticsListener logger = eventListenerFactory.createEventLogger(trackSelector);
    if (logger != null) {
      player.addAnalyticsListener(logger);
    }
    playerErrorHandler = createPlayerErrorHandler(mediaSourceLifeCycle);
    player.addListener(playerErrorHandler);
    return player;
  }

  /**
   * Start playback of the specified URL on the current ExoPlayer.  Must have previously
   * called {@link #createPlayer(boolean, boolean)}
   *
   * @param url - URL to play
   * @param drmInfo - DRM information
   * @param enableChunkless - flag to enable chunkless prepare, TODO - will make this default
   * @throws UnrecognizedInputFormatException - if the URI is not in a supported container format.
   */
  public void playUrl(Uri url, DrmInfo drmInfo, boolean enableChunkless) throws UnrecognizedInputFormatException {
    mediaSourceLifeCycle.playUrl(url, drmInfo, enableChunkless);
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
    int tunnelingSessionId = enableTunneling
            ? C.generateAudioSessionIdV21(context) : C.AUDIO_SESSION_ID_UNSET;

    DefaultTrackSelector.ParametersBuilder builder = currentParameters.buildUpon();
    builder.setTunnelingAudioSessionId(tunnelingSessionId);

    commitTrackSelectionParameters(builder);
  }


  @Override
  public boolean isTunnelingMode() {
    return player != null && player.getPlaybackState() != Player.STATE_ENDED
        && currentParameters.tunnelingAudioSessionId != C.AUDIO_SESSION_ID_UNSET;
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

    /* TrackSelection will weigth this language to the top if it is seen */
    builder.setPreferredTextLanguage(preferLanguage);

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
    return getMatchingAvailableTrackInfo(input -> isTextFormat(input));
  }

  /**
   * Get TrackInfo objects for all the audio tracks.
   *
   * All tracks can include tracks which the player cannot player.
   *
   * @return list of all text in the current MediaSource.
   */
  public List<TrackInfo> getAvailableAudioTracks() {
    return getMatchingAvailableTrackInfo(input -> isAudioFormat(input));
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
   * Return TrackInfo objects for the tracks matching the format indicated by the Predicate.
   * <p>
   * Use this API call to use forced track selection via overrides.  To select a TrackInfo with an override
   * use {@link #selectTrack(TrackInfo)}
   * <p>
   * The preferred method is using the APIs that use Constraint Based Selection (see
   * <a href="https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/trackselection/DefaultTrackSelector.html">DefaultTrackSelector</a>)
   * like for example {@link #setCloseCaption(boolean, String)}
   * <p>
   * Note, this will return an empty list until the media is prepared (player transitions to playback state
   * {@link com.google.android.exoplayer2.Player#STATE_READY}
   *
   * @param matching - {@link Predicate} used to filter the desired formats.
   * @return list of all tracks matching the predicate in the current MediaSource.
   */
  public List<TrackInfo> getMatchingAvailableTrackInfo(Predicate<Format> matching) {
    List<TrackInfo> availableTracks = new ArrayList<>();
    if (player != null) {
      TrackGroupArray availableTrackGroups = player.getCurrentTrackGroups();

      for (int groupIndex = 0; groupIndex < availableTrackGroups.length; groupIndex++) {
        TrackGroup group = availableTrackGroups.get(groupIndex);
        TrackSelection groupSelection = getTrackSelectionForGroup(group);
        for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
          Format format = group.getFormat(trackIndex);
          if (matching.apply(format)) {
            boolean isSelected = groupSelection != null
                    && groupSelection.getSelectedFormat().equals(format);
            availableTracks.add(new TrackInfo(format, isSelected));
          }
        }
      }
    }
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
      int rendererIndex = getRendererIndex(trackInfo.type);
      boolean isFormatSupported = false;
      TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(rendererIndex);
      for (int groupIndex = 0; groupIndex < trackGroupArray.length; groupIndex++) {
        TrackGroup trackGroup = trackGroupArray.get(groupIndex);
        for (int formatIndex = 0; formatIndex < trackGroup.length; formatIndex++) {
          if (format.equals(trackGroup.getFormat(formatIndex))) {
            int formatSupport = mappedTrackInfo.getTrackSupport(rendererIndex, groupIndex, formatIndex);
            isFormatSupported = (formatSupport == RendererCapabilities.FORMAT_HANDLED)
                    || (trackSelector.getParameters().exceedRendererCapabilitiesIfNecessary && formatSupport == RendererCapabilities.FORMAT_EXCEEDS_CAPABILITIES);
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
   * @return value or -1 if no renderer was created for the type
   */
  private int getRendererIndex(int trackType) {
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
      player.retry();
    }
  }

  @Override
  public void resetAndRetryPlayback() {
    mediaSourceLifeCycle.resetAndRestartPlayback();
  }

  private void commitTrackSelectionParameters(DefaultTrackSelector.ParametersBuilder builder) {
    currentParameters = builder.build();
    if (trackSelector != null) {
      trackSelector.setParameters(currentParameters);
    }
  }

  private DefaultTrackSelector createTrackSelector(boolean enableTunneling, Context context, TrackSelection.Factory trackSelectionFactory) {
    // Get a builder with current parameters then set/clear tunnling based on the intent
    //
    int tunnelingSessionId = C.AUDIO_SESSION_ID_UNSET;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      tunnelingSessionId = enableTunneling
              ? C.generateAudioSessionIdV21(context) : C.AUDIO_SESSION_ID_UNSET;
    }

    boolean usingSavedParameters =
        ! currentParameters.equals(new DefaultTrackSelector.ParametersBuilder(context).build());

    DefaultTrackSelector trackSelector = new DefaultTrackSelector(context, trackSelectionFactory);
    DefaultTrackSelector.ParametersBuilder builder = currentParameters.buildUpon();

    builder.setTunnelingAudioSessionId(tunnelingSessionId);

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
