// Copyright 2025 TiVo Inc.  All rights reserved.
package com.tivo.exoplayer.library.errorhandlers;

import java.util.HashMap;

/**
 * Interface for handling DRM license server authentication errors.
 * Implement this interface to receive callbacks when a DRM license server
 * authentication error occurs.
 */
public interface DrmLicenseServerAuthenticationListener {
    /**
     * Callee should return the most recent DRM License server authentication headers,
     * if required also post an event to the user interface to refresh any authentication
     * tokens required.
     *
     * <p>This method may be called back in either the ExoPlayer's calling application
     * thread (the Android Main Thread) or one of ExoPlayer's DRM license request
     * thread, so callee should not block and exercise caution updating its own
     * state.</p>
     *
     * @return Map with headers required to authenticate with the DRM License Server
     */
    HashMap<String, String> getLatestAuthHeaders();
}