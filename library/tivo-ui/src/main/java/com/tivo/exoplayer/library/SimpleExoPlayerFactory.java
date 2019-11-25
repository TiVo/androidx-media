package com.tivo.exoplayer.library;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.IFrameAwareAdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.trickplay.TrickPlayControlFactory;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Predicate;
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
public class SimpleExoPlayerFactory implements DefaultExoPlayerErrorHandler.PlaybackExceptionRecovery, Player.EventListener {
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
  private MediaSourceEventCallback callback;

  /**
   * Parameters are preserved across player create/destroy (Activity stop/start) to save track
   * selection criteria.
   */
  private DefaultTrackSelector.Parameters currentParameters = new DefaultTrackSelector.ParametersBuilder().build();

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
  protected AnalyticsListener createPlayerErrorHandler(MediaSourceLifeCycle mediaSourceLifeCycle) {
    List<DefaultExoPlayerErrorHandler.PlaybackExceptionRecovery> errorHandlers = getDefaultPlaybackExceptionHandlers(
        mediaSourceLifeCycle);

    return new DefaultExoPlayerErrorHandler(errorHandlers);
  }

  /**
   * If you override {@link #createPlayerErrorHandler(MediaSourceLifeCycle)}, use this method to get
   * the default set of {@link com.tivo.exoplayer.library.DefaultExoPlayerErrorHandler.PlaybackExceptionRecovery}
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
  protected List<DefaultExoPlayerErrorHandler.PlaybackExceptionRecovery> getDefaultPlaybackExceptionHandlers(
      MediaSourceLifeCycle mediaSourceLifeCycle) {
    return Arrays.asList(this, mediaSourceLifeCycle);
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
        trickPlayControl.removePlayerReference();
        trickPlayControl = null;
      }
      player = null;
      mediaSourceLifeCycle = null;
      trackSelector = null;
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
   * @param playWhenReady sets the play when ready flag.
   * @param defaultTunneling - default track selection to prefer tunneling (can turn this off {@link #setTunnelingMode(boolean)}}
   * @return the newly created player.  Also always available via {@link #getCurrentPlayer()}
   */
  @CallSuper
  public SimpleExoPlayer createPlayer(boolean playWhenReady, boolean defaultTunneling) {
    if (player != null) {
      releasePlayer();
    }

    trackSelector = createTrackSelector(defaultTunneling, context);
    TrickPlayControlFactory trickPlayControlFactory = new TrickPlayControlFactory();
    trickPlayControl = trickPlayControlFactory.createTrickPlayControl(trackSelector);
    RenderersFactory renderersFactory = trickPlayControl.createRenderersFactory(context);
    LoadControl loadControl = trickPlayControl.createLoadControl(new DefaultLoadControl());
    player = ExoPlayerFactory.newSimpleInstance(context, renderersFactory, trackSelector, loadControl);

    mediaSourceLifeCycle = createMediaSourceLifeCycle();

    trickPlayControl.setPlayer(player);
    player.setPlayWhenReady(playWhenReady);
    player.addListener(this);
    player.addAnalyticsListener(new EventLogger(trackSelector));
    player.addAnalyticsListener(createPlayerErrorHandler(mediaSourceLifeCycle));
    return player;
  }

  /**
   * Start playback of the specified URL on the current ExoPlayer.  Must have previously
   * called {@link #createPlayer(boolean, boolean)}
   *
   * @param url - URL to play
   * @param enableChunkless - flag to enable chunkless prepare, TODO - will make this default
   */
  public void playUrl(Uri url, boolean enableChunkless) {
    mediaSourceLifeCycle.playUrl(url, enableChunkless);
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

  /**
   * Updates track selection to prefer tunneling or regular video pipeline,
   * this can be called before the player is created.  If the player is
   * created it will force an immediate track selection.
   *
   * Note, this setting persists across player create/destroy
   *
   * @param enableTunneling - true to prefer tunneled decoder (if available)
   */
  public void setTunnelingMode(boolean enableTunneling) {
    int tunnelingSessionId = enableTunneling
            ? C.generateAudioSessionIdV21(context) : C.AUDIO_SESSION_ID_UNSET;

    DefaultTrackSelector.ParametersBuilder builder = currentParameters.buildUpon();
    builder.setTunnelingAudioSessionId(tunnelingSessionId);

    commitTrackSelectionParameters(builder);
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
   * @return list of all text in the current MediaSource.
   */
  public List<TrackInfo> getAvailableTextTracks() {
    return getMatchingAvailableTrackInfo(input -> isTextFormat(input));
  }

  /**
   * Get TrackInfo objects for all the audio tracks.
   *
   * @return list of all text in the current MediaSource.
   */
  public List<TrackInfo> getAvailableAudioTracks() {
    return getMatchingAvailableTrackInfo(input -> isAudioFormat(input));
  }

  /**
   * Return TrackInfo objects for the tracks matching the format indicated by the Predicate.
   *
   * Use this API call to use forced track selection via overrides.  To select a TrackInfo with an override
   * use {@link #selectTrack(TrackInfo)}
   *
   * The preferred method is using the APIs that use Constraint Based Selection (see
   * <a href="https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/trackselection/DefaultTrackSelector.html">DefaultTrackSelector</a>)
   * like for example {@link #setCloseCaption(boolean, String)}
   *
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
          if (matching.evaluate(format)) {
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


  @Override
  public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    for (int i=0; i < trackSelections.length; i++) {
      TrackSelection selection = trackSelections.get(i);
      if (selection instanceof IFrameAwareAdaptiveTrackSelection) {
        IFrameAwareAdaptiveTrackSelection ifAwareSelection = (IFrameAwareAdaptiveTrackSelection) selection;
        ifAwareSelection.setTrickPlayControl(trickPlayControl);
      }
    }
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
  public boolean recoverFrom(ExoPlaybackException e) {
    boolean handled = false;

    if (e.type == ExoPlaybackException.TYPE_RENDERER) {
      Exception renderException = e.getRendererException();
      if (renderException instanceof AudioSink.InitializationException) {
        setTunnelingMode(false);
        player.retry();

        handled = true;
      } else if (renderException instanceof AudioSink.WriteException) {
        // TODO - we decided this was to complicated, so we ignore the error in ExoPlayer core :-(
//        AudioSink.WriteException writeException = (AudioSink.WriteException) renderException;
//        if (writeException.errorCode == android.media.AudioTrack.ERROR_DEAD_OBJECT) {
//          DefaultTrackSelector.Parameters trackSelectorParameters = trackSelector.getParameters();
//          DefaultTrackSelector.ParametersBuilder builder = trackSelectorParameters.buildUpon();
//
//          TrackSelectionArray trackSelections = player.getCurrentTrackSelections();
//          for (int i = 0; i < player.getRendererCount() && i < trackSelections.length; i++) {
//            if (player.getRendererType(i) == C.TRACK_TYPE_AUDIO && trackSelections.get(i) != null) {
//                builder.setRendererDisabled(i, true);
//              Log.d("ExoPlayer", "AudioSink.WriteException - disable audio track " + player.getRendererType(i) + " to recover");
//            }
//          }
//          trackSelector.setParameters(builder);
//
//          Log.d("ExoPlayer", "AudioSink.WriteException - reset player with prepare");
//          player.prepare(mediaSource, true, true);
//          handled = true;
      }

    }

    return handled;
  }

  private void commitTrackSelectionParameters(DefaultTrackSelector.ParametersBuilder builder) {
    currentParameters = builder.build();
    if (trackSelector != null) {
      trackSelector.setParameters(currentParameters);
    }
  }

  private DefaultTrackSelector createTrackSelector(boolean enableTunneling, Context context) {
    // Get a builder with current parameters then set/clear tunnling based on the intent
    //
    int tunnelingSessionId = enableTunneling
            ? C.generateAudioSessionIdV21(context) : C.AUDIO_SESSION_ID_UNSET;

    boolean usingSavedParameters =
        ! currentParameters.equals(new DefaultTrackSelector.ParametersBuilder().build());

    TrackSelection.Factory trackSelectionFactory =  new IFrameAwareAdaptiveTrackSelection.Factory();
    DefaultTrackSelector trackSelector = new DefaultTrackSelector(trackSelectionFactory);
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
