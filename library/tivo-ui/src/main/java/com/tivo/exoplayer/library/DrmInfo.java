package com.tivo.exoplayer.library;


/** Base class to pass DRM specific metadata to
 * {@link com.tivo.exoplayer.library.SimpleExoPlayerFactory#playUrl(Uri, DrmInfo, boolean)}
 */
public class DrmInfo {

    public DrmInfo(DrmType drmType) {
        this.drmType = drmType;
    }

    public enum DrmType {
        CLEAR,
        VCAS,
        WIDEVINE
    }

    /**
     * Get DRM type
     * @return DRM type
     */
    public DrmType getDrmType() {
        return drmType;
    }

    private DrmType drmType;

}
