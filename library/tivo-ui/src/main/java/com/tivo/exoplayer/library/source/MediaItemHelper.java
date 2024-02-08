package com.tivo.exoplayer.library.source;

import android.content.Context;
import android.content.Intent;
import android.support.v4.media.MediaBrowserCompat;
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
  public static final String DRM_WV_PROXY = "wv_proxy";
  public static final String DRM_VCAS_VUID = "vcas_vuid";

  public static MediaItem.Builder addVcasDrmConfig(MediaItem.Builder builder, VcasDrmInfo vcasDrmInfo) {
    builder
        .setDrmUuid(VcasDrmInfo.VCAS_UUID)
        .setTag(vcasDrmInfo);
    return builder;
  }

  public static MediaItem.Builder populateDrmPropertiesFromIntent(MediaItem.Builder builder, Intent intent, Context context) {
    @Nullable String drmSchemeExtra = intent.getStringExtra(DRM_SCHEME);
    if (drmSchemeExtra == null) {
      return builder;
    }
    switch (drmSchemeExtra) {
      case DRM_SCHEME_WIDEVINE:

        Map<String, String> keyRequestProperties = new HashMap<>();
        String vuid = intent.getStringExtra(DRM_VCAS_VUID);
        if (vuid != null) {
          keyRequestProperties.put("deviceId", vuid);
        }

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
        String storeDir = "/sdcard/demoVR";
        File vcasStoreDir = context.getExternalFilesDir("VCAS");
        if (! vcasStoreDir.exists()) {
          vcasStoreDir.mkdirs();
        }
        try {
          storeDir = vcasStoreDir.getCanonicalPath();
        } catch (IOException e) {
          Log.e(TAG, "Failed to open VCAS storage directory.", e);
        }

        Log.d(TAG, String.format("Requested Verimatrix DRM with addr:%s CAID:%s storage:%s", vcasAddr, vcasCaId, storeDir));
        DrmInfo drmInfo = new VcasDrmInfo(vcasAddr, vcasCaId, storeDir, true);
        builder
            .setDrmUuid(DrmInfo.VCAS_UUID)
            .setTag(drmInfo);
        break;
    }
    return builder;
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
