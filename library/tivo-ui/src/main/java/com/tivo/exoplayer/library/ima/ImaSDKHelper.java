package com.tivo.exoplayer.library.ima;

import android.content.Context;
import android.net.Uri;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.Log;
import com.tivo.exoplayer.library.source.ExtendedMediaSourceFactory;
import java.util.UUID;

/**
 * Encapsulates access to the IMA SDK For Android ExoPlayer integration in a single simple to use object
 *
 * <p>This ImaSDKHelper manages creating and destroying the ExoPLayer IMA Extensions {@link ImaAdsLoader}
 * in order to both display ads with content or display a standalone trailer ad expressed via a VAST document.
 * </p>
 *
 * <p>Usage is quite simple, instantiate the {@link ImaSDKHelper.Builder} with listeners, if needed, in your
 * Activity's onCreate() method then use {@link #createTrailerMediaItem(Player, AdsConfiguration)}
 * to create a MediaItem for playing back a VAST ad as a standalone trailer.</p>
 *
 */
public class ImaSDKHelper {
  public static final String TAG = "ImaSDKHelper";

  /** Generates very chatty logging from the IMA SDK */
  public static boolean DEBUG_MODE_ENABLED = false;

  private final PlayerView playerView;
  private final ExtendedMediaSourceFactory mediaSourceFactory;
  private final ImaAdsLoader.Builder adsLoaderBuilder;

  private @Nullable ImaAdsLoader currentAdsLoader;

  private @Nullable AdsConfiguration currentPlayingAd;
  private @Nullable AdListenerAdapter adListenerAdapter;
// True when deprecated methods removed
//  private @MonotonicNonNull AdListenerAdapter adListenerAdapter;

  public static class Builder {

    private final ImaSDKHelper sdkHelper;
    private final Context context;
    private boolean warmStartImaSDK;
    private @Nullable AdProgressListener adProgressListener;

    public Builder(PlayerView playerView, ExtendedMediaSourceFactory mediaSourceFactory, Context context) {
      sdkHelper = new ImaSDKHelper(playerView, mediaSourceFactory, context);
      this.context = context;
    }


    /**
     * Provide an {@link com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener} for
     * use each time the ImaAdsLoader is created by {@link ImaSDKHelper}
     *
     * // Deprecated - use {@link #setAdProgressListener(AdProgressListener)}
     * @param eventListener the listener callback.
     * @return {@link Builder} for chaining.
     */
    @Deprecated
    public Builder setAdEventListener(AdEvent.AdEventListener eventListener) {
      sdkHelper.adsLoaderBuilder.setAdEventListener(eventListener);
      return this;
    }

    /**
     * Provide an {@link com.google.ads.interactivemedia.v3.api.AdErrorEvent.AdErrorListener} for
     * use each time the ImaAdsLoader is created by {@link ImaSDKHelper}
     *
     * // Deprecated - use {@link #setAdProgressListener(AdProgressListener)}
     * @param eventListener the listener callback.
     * @return {@link Builder} for chaining.
     */
    @Deprecated
    public Builder setAdErrorListener(AdErrorEvent.AdErrorListener eventListener) {
      sdkHelper.adsLoaderBuilder.setAdErrorListener(eventListener);
      return this;
    }

    /**
     * The {@link AdProgressListener} interface simply combines both AdEvent and AdError
     * listeners and includes an callback for playback errors as well.
     *
     * @param progressListener AdProgressListener to use for callback
     * @return {@link Builder} for chaining.
     */
    public Builder setAdProgressListener(AdProgressListener progressListener) {
      adProgressListener = progressListener;
      return this;
    }

    /**
     * Causes the {@link #warmStartIMA(Context)} method to be called once on {@link #build()}
     * either use this or call the {@link #warmStartIMA(Context)} once in your application startup
     *
     * @param warmStartImaSDK true to "Warm" start the SDK on first build() call
     * @return {@link Builder} for chaining.
     */
    public Builder setWarmStartImaSDK(boolean warmStartImaSDK) {
      this.warmStartImaSDK = warmStartImaSDK;
      return this;
    }

    /**
     * Builds the {@link ImaSDKHelper}, this only needs to be done once in the Activity's
     * create method after creating the {@link PlayerView} and {@link ExtendedMediaSourceFactory}
     *
     * <p>In Activity.onStop() just call the {@link #release()} method and drop the reference
     * to ImaSDKHelper.</p>
     *
     * @return the newly built ImaSDKHelper
     */
    public ImaSDKHelper build() {
      if (warmStartImaSDK) {
        warmStartIMA(context);
        warmStartImaSDK = false;
      }

      if (adProgressListener != null) {
        AdListenerAdapter listenerAdapter = sdkHelper.createAdListenerAdapter(adProgressListener);
        sdkHelper.adsLoaderBuilder.setAdErrorListener(listenerAdapter);
        sdkHelper.adsLoaderBuilder.setAdEventListener(listenerAdapter);
      }
      return sdkHelper;
    }

    /**
     * "Warm" starts the IMA SDK.  The IMA SDK requires components from the WebView (Chromium)
     * this entails a not insignificant startup time the first time it occurs for an Android
     * process.
     * <p>To pay that startup time up-front either call this method as soon as you have an
     * {@link Context} or use {@link Builder#setWarmStartImaSDK(boolean)} when you first create
     * the {@link ImaSDKHelper}</p>
     *
     * @param context the ApplicationContext
     */
    public static void warmStartIMA(final Context context) {
      Log.d(TAG, "warmStartIMA()");
      ImaSdkFactory sdkFactory = ImaSdkFactory.getInstance();

      AdDisplayContainer adDisplayContainer =
          ImaSdkFactory.createAdDisplayContainer(new ViewGroup(context) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {}
          }, new NullVideoPlayer());

      // Create an AdsLoader, then just release it.  That is enough to start the WebView, this costs a couple of seconds
      ImaSdkSettings settings = sdkFactory.createImaSdkSettings();
      AdsLoader adsLoader = sdkFactory.createAdsLoader(context, settings, adDisplayContainer);
      adsLoader.release();
      Log.d(TAG, "warmStartIMA() return");
    }
  }

  /**
   * Listen for any events or errors related to ad playback with a final call-back when the
   * ad playback completes.
   */
  public interface AdProgressListener {

    /**
     * Same set of errors as would be reported by {@link AdErrorEvent.AdErrorListener} but
     * with an identifier for the VAST request that produced the event.
     *
     * <p>Note an error any single ad, in an ad POD, may report an error but playback
     * will simply transition to the next ad, ad playback does not stop. Eventually,
     * {@link #onAdsCompleted(AdsConfiguration, boolean)} is always called.
     *
     * @param playingAd AdsConfiguration for the VAST request that triggered error
     * @param adErrorEvent same as reported by AdErrorEvent.AdErrorListener
     */
    default void onAdError(AdsConfiguration playingAd, AdErrorEvent adErrorEvent) { }

    /**
     * Same set of events as would be reported by {@link AdEvent.AdEventListener} but
     * with an identifier for the VAST request that produced the event
     *
     * @param playingAd AdsConfiguration for the VAST request that triggered event
     * @param adEvent same as reported by AdEvent as reported by AdEvent.AdEventListener
     */
    default void onAdEvent(AdsConfiguration playingAd, AdEvent adEvent) { }

    /**
     * Single callback for completion of VAST request.  This is called after
     * the final call to {@link #onAdEvent(AdsConfiguration, AdEvent)} ({@link AdEvent.AdEventType#ALL_ADS_COMPLETED})
     * or if the ad playback is aborted for any reason.
     * 
     * @param completedAd - the identifier of the completing ad
     * @param wasAborted - if ad playback was canceled or aborted before final AdEvent delivered
     */
    void onAdsCompleted(AdsConfiguration completedAd, boolean wasAborted);
  }

  /**
   * Adapts {@link AdEvent.AdEventListener} and {@link AdErrorEvent.AdErrorListener} to 
   * an {@link AdProgressListener}.
   */
  private class AdListenerAdapter
      implements AdErrorEvent.AdErrorListener, AdEvent.AdEventListener, Player.Listener {
    private final AdProgressListener delegate;
    private @Nullable Player currentPlayer;
    private @Nullable AdEvent delayedPause;
    private boolean isTrailer;
    private boolean isStartedReceived;

    private AdListenerAdapter(AdProgressListener delegate) {
      this.delegate = delegate;
    }

    // AdErrorListener

    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
      resetStateOnAdChanged();
      delegate.onAdError(currentPlayingAd, adErrorEvent);
    }

    // AdEventListener

    @Override
    public void onAdEvent(AdEvent adEvent) {
      switch (adEvent.getType()) {
        case STARTED:
          isStartedReceived = true;
          removePlayerListener();
          delegate.onAdEvent(currentPlayingAd, adEvent);
          break;

        case PAUSED:
          if (isShouldDelayPauseEvent()) {
            delayedPause = adEvent;
          } else if (delayedPause == null) {
            delegate.onAdEvent(currentPlayingAd, adEvent);
          } else {
            throw new IllegalStateException("onAdEvent() second PAUSE with delayed PAUSE pending");
          }
          break;

        case ALL_ADS_COMPLETED:
          delegate.onAdEvent(currentPlayingAd, adEvent);
          delegate.onAdsCompleted(currentPlayingAd, false);
          currentPlayingAd = null;
          break;

        default:
          delegate.onAdEvent(currentPlayingAd, adEvent);
          break;
      }
    }

    // Player.Listener

    @Override
    public void onPlaybackStateChanged(@Player.State int playbackState) {
      Log.d(TAG, "onPlaybackStateChanged() - state: " + playbackState);
      if (isDelayedPauseEventDeliverable(playbackState)) {
        delegate.onAdEvent(currentPlayingAd, delayedPause);
        delayedPause = null;
      }

    }

    private boolean isShouldDelayPauseEvent() {
      return currentPlayer != null && isTrailer && !isStartedReceived;
    }

    private boolean isDelayedPauseEventDeliverable(@Player.State int playbackState) {
      return playbackState == Player.STATE_READY && delayedPause != null;
    }

    private void setPlayer(Player player, boolean isTrailer) {
      resetStateOnAdChanged();
      this.isTrailer = isTrailer;
      if (isTrailer) {
        currentPlayer = player;
        currentPlayer.addListener(this);
      }
    }

    private void resetStateOnAdChanged() {
      removePlayerListener();
      delayedPause = null;
      isTrailer = false;
      isStartedReceived = false;
    }

    private void removePlayerListener() {
      if (currentPlayer != null) {
        currentPlayer.removeListener(this);
        currentPlayer = null;
      }
    }

    private void adPlaybackAborted() {
      resetStateOnAdChanged();
      delegate.onAdsCompleted(currentPlayingAd, true);
    }
  }

  /**
   * Identifies the playing ad (note this will be MediaItem.AdsConfiguration once that
   * is made pubilc (AndroidX)
   */
  public static class AdsConfiguration {
    /** The ad tag URI to load. */
    public final Uri adTagUri;
    /**
     * An opaque identifier for ad playback state associated with this item, or {@code null} if the
     * combination of the {@link MediaItem.Builder#setMediaId(String) media ID} and {@link #adTagUri
     * ad tag URI} should be used as the ads identifier.
     */
    @Nullable public final Object adsId;

    public AdsConfiguration(Uri adTagUri, @Nullable Object adsId) {
      this.adTagUri = adTagUri;
      this.adsId = adsId;
    }
  }
  
  private ImaSDKHelper(PlayerView playerView, ExtendedMediaSourceFactory mediaSourceFactory, Context context) {
    this.playerView = playerView;
    this.mediaSourceFactory = mediaSourceFactory;
    adsLoaderBuilder = new ImaAdsLoader.Builder(context);
  }

  private void setupAdsLoader(Player currentPlayer, boolean isTrailer) {
    if (currentAdsLoader != null) {
      currentAdsLoader.release();
    }

    if (adListenerAdapter != null) {
      adListenerAdapter.setPlayer(currentPlayer, isTrailer);
    }

    currentAdsLoader = adsLoaderBuilder
        .setDebugModeEnabled(DEBUG_MODE_ENABLED)
        .build();
    currentAdsLoader.setPlayer(currentPlayer);

    // setup MediaSourceFactory IMA components
    mediaSourceFactory.setAdsLoaderProvider(adsConfiguration -> currentAdsLoader);
    mediaSourceFactory.setAdViewProvider(playerView);
  }


  private AdListenerAdapter createAdListenerAdapter(AdProgressListener progressListener) {
    adListenerAdapter = new AdListenerAdapter(progressListener);
    return adListenerAdapter;
  }

  // External API methods

  public void activityPaused() {
    // TODO - must pause player and maintain ad playback state
  }

  public void activityResumed() {
    // TODO - must resume playing the ad active at pause or resume content
  }

  /**
   * Returns the unique id of the current playing ad or null if no ad is playing.
   *
   * <p>Note {@link Player#isPlayingAd()} will also be true once this ad has
   * actually begun playback.  Also if
   * {@link com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime#currentMediaPeriodId} had
   * an {@link com.google.android.exoplayer2.source.MediaPeriodId#adGroupIndex that is not INDEX_UNSET
   * then the player is playing an ad.</p>
   *
   * @return {@link AdsConfiguration} of current playing ad.
   */
  public @Nullable AdsConfiguration getCurrentPlayingAd() {
    return currentPlayingAd;
  }

  /**
   * Creates a {@link MediaItem.Builder} that will build a {@link MediaItem}
   * that plays the VAST document from vastUri along with empty content.
   *
   * // Deprecated use {@link #createTrailerMediaItem(Player, AdsConfiguration)}
   * @param player - the current {@link Player} instance
   * @param vastUri - Uri to fetch the VAST document with the trailer
   * @return {@link MediaItem.Builder} ready to call build() and pass to player
   */
  @Deprecated
  public MediaItem.Builder createTrailerMediaItem(Player player, Uri vastUri) {
    AdsConfiguration adsConfiguration = new AdsConfiguration(vastUri, UUID.randomUUID());
    return createTrailerMediaItem(player, adsConfiguration);
  }

  /**
   * Creates a {@link MediaItem.Builder} that will build a {@link MediaItem}
   * that plays the VAST document from vastUri along with empty content.
   *
   * @param player - the current {@link Player} instance
   * @param adsConfiguration - AdsConfiguration specifying the VAST Url and an adsId
   * @return {@link MediaItem.Builder} ready to call build() and pass to player
   */
  public MediaItem.Builder createTrailerMediaItem(Player player, AdsConfiguration adsConfiguration) {
    if (currentPlayingAd != null && adListenerAdapter != null) {
      adListenerAdapter.adPlaybackAborted();
    }
    setupAdsLoader(player, true);
    if (adsConfiguration.adsId == null) {
      adsConfiguration = new AdsConfiguration(adsConfiguration.adTagUri, UUID.randomUUID());
    }
    currentPlayingAd = adsConfiguration;
    return new MediaItem.Builder()
        .setUri(ExtendedMediaSourceFactory.SILENCE_URI)
        .setAdTagUri(currentPlayingAd.adTagUri, currentPlayingAd.adsId);
  }

  /**
   * Setups up the IMA SDK and decorates the input @link MediaItem.Builder} with
   * attributes to enable playing the content with the ads expressed by the
   * VAST document specified by the vastUri.
   *
   * // Deprecated use {@link #includeAdsWithMediaItem(Player, MediaItem.Builder, AdsConfiguration)}
   * @param player - the current {@link Player} instance
   * @param builder - {@link MediaItem.Builder} to decorate with the Ad
   * @param vastUri - URI for the VAST document describing the ads
   * @param adsId - optional (nullable) AdsID to use for suspend resume
   */
  @Deprecated
  public void includeAdsWithMediaItem(
      Player player,
      @NonNull MediaItem.Builder builder,
      @NonNull Uri vastUri,
      @Nullable Object adsId) {
    includeAdsWithMediaItem(player, builder, new AdsConfiguration(vastUri, adsId));
  }

  /**
   * Setups up the IMA SDK and decorates the input @link MediaItem.Builder} with
   * attributes to enable playing the content with the ads expressed by adsConfiguration
   *
   * @param player - the current {@link Player} instance
   * @param builder - {@link MediaItem.Builder} that will be decorated with the ad to play
   * @param adsConfiguration - AdsConfiguration specifying the VAST Url and an adsId
   */
  public void includeAdsWithMediaItem(
      Player player,
      @NonNull MediaItem.Builder builder,
      AdsConfiguration adsConfiguration) {
    if (currentPlayingAd != null && adListenerAdapter != null) {
      adListenerAdapter.adPlaybackAborted();
    }
    setupAdsLoader(player, false);   // TODO, investigate keeping the currentAdsLoader to note what ads have already played
    if (adsConfiguration.adsId == null) {
      adsConfiguration = new AdsConfiguration(adsConfiguration.adTagUri, UUID.randomUUID());
    }
    currentPlayingAd = adsConfiguration;
    builder.setAdTagUri(adsConfiguration.adTagUri, adsConfiguration.adsId);
  }

  /**
   * Call release() in our Activity.onStop() method then drop the
   * reference.
   */
  public void release() {
    if (currentAdsLoader != null) {
      currentAdsLoader.release();
    }
    currentAdsLoader = null;
  }
}
