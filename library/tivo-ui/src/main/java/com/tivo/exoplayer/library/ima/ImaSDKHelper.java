package com.tivo.exoplayer.library.ima;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
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
 * <p>This ImaSDKHelper manages creating and destroing the ExoPLayer IMA Extensions {@link ImaAdsLoader}
 * in order to both display ads with content or display a standalone trailer ad expressed via a VAST document.
 * </p>
 *
 * <p>Usage is quite simple, instantiate the {@link ImaSDKHelper.Builder} with listeners, if needed, in your
 * Activity's {@link android.app.Activity#onCreate(Bundle)} method then use {@link #createTrailerMediaItem(Player, Uri)}
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

  public static class Builder {

    private final ImaSDKHelper sdkHelper;
    private final Context context;
    private boolean warmStartImaSDK;

    public Builder(PlayerView playerView, ExtendedMediaSourceFactory mediaSourceFactory, Context context) {
      sdkHelper = new ImaSDKHelper(playerView, mediaSourceFactory, context);
      this.context = context;
    }


    /**
     * Provide an {@link com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener} for
     * use each time the ImaAdsLoader is created by {@link ImaSDKHelper}
     *
     * @param eventListener the listener callback.
     * @return {@link Builder} for chaining.
     */
    public Builder setAdEventListener(AdEvent.AdEventListener eventListener) {
      sdkHelper.adsLoaderBuilder.setAdEventListener(eventListener);
      return this;
    }

    /**
     * Provide an {@link com.google.ads.interactivemedia.v3.api.AdErrorEvent.AdErrorListener} for
     * use each time the ImaAdsLoader is created by {@link ImaSDKHelper}
     *
     * @param eventListener the listener callback.
     * @return {@link Builder} for chaining.
     */
    public Builder setAdErrorListener(AdErrorEvent.AdErrorListener eventListener) {
      sdkHelper.adsLoaderBuilder.setAdErrorListener(eventListener);
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
     * <p>In {@link Activity#onStop()} just call the {@link #release()} method and drop the reference
     * to ImaSDKHelper.</p>
     *
     * @return the newly built ImaSDKHelper
     */
    public ImaSDKHelper build() {
      if (warmStartImaSDK) {
        warmStartIMA(context);
        warmStartImaSDK = false;
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

  private ImaSDKHelper(PlayerView playerView, ExtendedMediaSourceFactory mediaSourceFactory, Context context) {
    this.playerView = playerView;
    this.mediaSourceFactory = mediaSourceFactory;
    adsLoaderBuilder = new ImaAdsLoader.Builder(context);
  }

  private void setupAdsLoader(Player currentPlayer) {
    if (currentAdsLoader != null) {
      currentAdsLoader.release();
    }

    currentAdsLoader = adsLoaderBuilder
        .setDebugModeEnabled(DEBUG_MODE_ENABLED)
        .build();
    currentAdsLoader.setPlayer(currentPlayer);

    // setup MediaSourceFactory IMA components
    mediaSourceFactory.setAdsLoaderProvider(adsConfiguration -> currentAdsLoader);
    mediaSourceFactory.setAdViewProvider(playerView);
  }

  // External API methods

  public void activityPaused() {
    // TODO - must pause player and maintain ad playback state
  }

  public void activityResumed() {
    // TODO - must resume playing the ad active at pause or resume content
  }

  /**
   * Creates a {@link MediaItem.Builder} that will build a {@link MediaItem}
   * that plays the VAST document from vastUri along with empty content.
   *
   * @param player - the current {@link Player} instance
   * @param vastUri - Uri to fetch the VAST document with the trailer
   * @return {@link MediaItem.Builder} ready to call build() and pass to player
   */
  public MediaItem.Builder createTrailerMediaItem(Player player, Uri vastUri) {
    setupAdsLoader(player);
    return new MediaItem.Builder()
        .setUri(ExtendedMediaSourceFactory.SILENCE_URI)
        .setAdTagUri(vastUri, UUID.randomUUID());
  }

  /**
   * Setups up the IMA SDK and decorates the input @link MediaItem.Builder} with
   * attributes to enable playing the content with the ads expressed by the
   * VAST document specified by the vastUri.
   *
   * @param player - the current {@link Player} instance
   * @param builder - {@link MediaItem.Builder} to decorate with the Ad
   * @param vastUri - URI for the VAST document describing the ads
   * @param adsId - optional (nullable) AdsID to use for suspend resume
   */
  public void includeAdsWithMediaItem(
      Player player,
      @NonNull MediaItem.Builder builder,
      @NonNull Uri vastUri,
      @Nullable Object adsId) {
    setupAdsLoader(player);   // TODO, investigate keeping the currentAdsLoader to note what ads have already played
    builder.setAdTagUri(vastUri, adsId);
  }

  /**
   * Call release() in our {@link Activity#onStop()} method then drop the
   * reference.
   */
  public void release() {
    if (currentAdsLoader != null) {
      currentAdsLoader.release();
    }
    currentAdsLoader = null;
  }
}
