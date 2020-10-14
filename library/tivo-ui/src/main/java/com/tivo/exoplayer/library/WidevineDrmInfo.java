package com.tivo.exoplayer.library;

import com.tivo.exoplayer.library.DrmInfo;

/**
 * Widevine specific DRM information.
 */
public class WidevineDrmInfo extends DrmInfo {

    public WidevineDrmInfo(String proxy, String[] keyRequestProps)
    {
         super(DrmType.WIDEVINE);
         this.proxyUrl = proxy;
         this.keyRequestProps = keyRequestProps;
    }

    public String getProxyUrl() {
        return proxyUrl;
    }

    public String[] getKeyRequestProps() {
        return keyRequestProps;
    }

    private final String proxyUrl;
    private final String[] keyRequestProps;

}
