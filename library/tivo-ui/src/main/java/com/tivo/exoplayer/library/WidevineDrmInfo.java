package com.tivo.exoplayer.library;

import java.util.Map;

/**
 * Widevine specific DRM information.
 */
public class WidevineDrmInfo extends DrmInfo {

    public WidevineDrmInfo(String proxy, Map<String, String> keyRequestProps, boolean multiSession)
    {
        super(WIDEVINE);
        this.proxyUrl = proxy;
        this.keyRequestProps = keyRequestProps;
        this.multiSession = multiSession;
    }

    public String getProxyUrl() {
        return proxyUrl;
    }

    public Map<String, String> getKeyRequestProps() {
        return keyRequestProps;
    }

    private final String proxyUrl;
    private final Map<String, String> keyRequestProps;
    private final boolean multiSession;

    public boolean isMultiSessionEnabled() {
        return multiSession;
    }
}
