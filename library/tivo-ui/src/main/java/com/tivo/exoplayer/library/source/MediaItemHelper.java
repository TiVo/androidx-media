package com.tivo.exoplayer.library.source;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.common.collect.ImmutableList;
import com.tivo.exoplayer.library.DrmInfo;
import com.tivo.exoplayer.library.VcasDrmInfo;
import com.tivo.exoplayer.library.WidevineDrmInfo;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Collection of methods to add and parse non=standard additions to the ExoPlayer
 * {@link com.google.android.exoplayer2.MediaItem}.
 */
public class MediaItemHelper {
  public static final String TAG = "MedioItemHelper";

  public static final String DRM_SCHEME = "drm_scheme";

  public static final String DRM_SCHEME_VCAS = "vcas";
  public static final String DRM_VCAS_CA_ID = "vcas_ca_id";
  public static final String DRM_VCAS_ADDR = "vcas_addr";

  public static final String DRM_SCHEME_WIDEVINE = "widevine";
  public static final String DRM_SCHEME_WIDEVINE_TIVODRM = "tivo_drm";
  public static final String DRM_WV_PROXY = "wv_proxy";
  public static final String DRM_VCAS_VUID = "vcas_vuid";
  public static final String DRM_WV_AUTH = "auth";
  private static final String VCAS_DEBUG = "vcas_debug";

  public static MediaItem.Builder populateDrmPropertiesFromIntent(MediaItem.Builder builder, Intent intent, Context context) {
    boolean vcasDebug = intent.getBooleanExtra(VCAS_DEBUG, false);
    @Nullable String drmSchemeExtra = intent.getStringExtra(DRM_SCHEME);
    if (drmSchemeExtra == null) {
      return builder;
    }
    Map<String, String> keyRequestProperties = new HashMap<>();

    switch (drmSchemeExtra) {
      case DRM_SCHEME_WIDEVINE:
        String vuid = intent.getStringExtra(DRM_VCAS_VUID);
        if (vuid != null) {
          keyRequestProperties.put("deviceId", vuid);
        }
        break;

      case DRM_SCHEME_WIDEVINE_TIVODRM:
        String auth = intent.getStringExtra(DRM_WV_AUTH);
        if (auth != null) {
          keyRequestProperties.put("Authorization", auth);
        }

    }

    switch (drmSchemeExtra) {
      case DRM_SCHEME_WIDEVINE:
      case DRM_SCHEME_WIDEVINE_TIVODRM:
        builder
            .setDrmUuid(C.WIDEVINE_UUID)
            .setDrmLicenseUri(intent.getStringExtra(DRM_WV_PROXY))
            .setDrmMultiSession(true)
            .setDrmSessionForClearTypes(ImmutableList.of(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO))
            .setDrmLicenseRequestHeaders(keyRequestProperties);
        break;

      case DRM_SCHEME_VCAS:
        String vcasAddr = intent.getStringExtra(DRM_VCAS_ADDR);
        String vcasCaId = intent.getStringExtra(DRM_VCAS_CA_ID);
        addVcasDrmToMediaItemBuilder(builder, context, vcasAddr, vcasCaId, vcasDebug);
        break;
    }
    return builder;
  }

  /**
   * Set the {@link MediaItem} to enable using VCAS DRM
   *
   * <p>Initializes the VCAS storage directory in a place appropriate that is private to the
   * application and sets the DRM info needed for the {@link VerimatrixDataSourceFactory}</p>
   *
   * @param builder builder for the {@link MediaItem} to use
   * @param context application context (used for storage access)
   * @param vcasAddr ViewRight server to use
   * @param vcasCaId Device ID to set for VCAS
   * @param debugOn log client info to VR_client.log.
   */
  public static void addVcasDrmToMediaItemBuilder(MediaItem.Builder builder, Context context, String vcasAddr, String vcasCaId,
      boolean debugOn) {
    File vcasStoreDir = context.getExternalFilesDir("VCAS");
    if (vcasStoreDir == null) {
      vcasStoreDir = new File(context.getFilesDir(), "VCAS");
    }
    if (! vcasStoreDir.exists()) {
      vcasStoreDir.mkdirs();
    }
    try {
      String storeDir = vcasStoreDir.getCanonicalPath();

      Log.d(TAG, String.format("Requested Verimatrix DRM with addr:%s CAID:%s storage:%s, debug:%s"
          , vcasAddr, vcasCaId, storeDir, debugOn));
      DrmInfo drmInfo = new VcasDrmInfo(vcasAddr, vcasCaId, storeDir, debugOn);
      builder
          .setDrmUuid(DrmInfo.VCAS_UUID)
          .setTag(drmInfo);
    } catch (IOException e) {
      Log.e(TAG, "Failed to open VCAS storage directory.", e);
      throw new RuntimeException("Failed to access storage for VCAS in vcasStoreDir = " + vcasStoreDir, e);
    }
  }

  public static MediaItem.Builder populateDrmFromDrmInfo(MediaItem.Builder builder, DrmInfo drmInfo) {
    switch (drmInfo.getDrmType()) {
      case DrmInfo.CLEAR:
        break;
      case DrmInfo.TIVO_CRYPT:
        builder
            .setDrmUuid(DrmInfo.TIVO_CRYPT_UUID)
            .setTag(drmInfo);
        break;
      case DrmInfo.VCAS:
        builder
            .setDrmUuid(DrmInfo.VCAS_UUID)
            .setTag(drmInfo);
        break;
      case DrmInfo.WIDEVINE:
        WidevineDrmInfo widevineDrmInfo = (WidevineDrmInfo) drmInfo;

        builder
            .setDrmUuid(C.WIDEVINE_UUID)
            .setDrmLicenseUri(widevineDrmInfo.getProxyUrl())
            .setDrmMultiSession(widevineDrmInfo.isMultiSessionEnabled())
            .setDrmSessionForClearTypes(ImmutableList.of(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO))
            .setDrmLicenseRequestHeaders(widevineDrmInfo.getKeyRequestProps());
        break;
    }
    return builder;
  }

}
