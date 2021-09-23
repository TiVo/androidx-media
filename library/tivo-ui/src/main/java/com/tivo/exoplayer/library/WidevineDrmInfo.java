package com.tivo.exoplayer.library;

/**
 * Widevine specific DRM information.
 */
public class WidevineDrmInfo extends DrmInfo {

    public WidevineDrmInfo(String proxy, String[] keyRequestProps, boolean multiSession)
    {
         super(WIDEVINE);
         this.proxyUrl = proxy;
         this.keyRequestProps = keyRequestProps;
        this.multiSession = multiSession;
    }

    public String getProxyUrl() {
        return proxyUrl;
    }

    public String[] getKeyRequestProps() {
        return keyRequestProps;
    }

    private final String proxyUrl;
    private final String[] keyRequestProps;
    private final boolean multiSession;

    public boolean isMultiSessionEnabled() {
        return multiSession;
    }
}
