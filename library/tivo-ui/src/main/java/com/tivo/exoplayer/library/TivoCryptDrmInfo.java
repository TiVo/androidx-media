package com.tivo.exoplayer.library;

import android.content.Context;

/**
 * TivoCrypt specific DRM information
 */
public class TivoCryptDrmInfo extends DrmInfo {

    private Context context;
    private String wbKey;
    private String deviceKey;

    public TivoCryptDrmInfo(Context context, String wbKey, String deviceKey) {
        super(TIVO_CRYPT);
        this.context = context;
        this.wbKey = wbKey;
        this.deviceKey = deviceKey;
    }

    public Context getContext() {
        return context;
    }

    /**
     * White box key used in content decryption
     * @return wbKey
     */
    public String getWbKey() {
        return wbKey;
    }

    /**
     * Unique device key
     * @return deviceKey
     */
    public String getDeviceKey() {
        return deviceKey;
    }
}
