package com.tivo.exoplayer.library;


import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Base class to pass DRM specific metadata to
 * {@link com.tivo.exoplayer.library.SimpleExoPlayerFactory#playUrl(Uri, DrmInfo, boolean)}
 */
public class DrmInfo {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            CLEAR,
            WIDEVINE,
            VCAS,
            TIVO_CRYPT
    })
    public @interface DrmType {}

    public static final int CLEAR = 0;
    public static final int VCAS = 1;
    public static final int WIDEVINE = 2;
    public static final int TIVO_CRYPT = 3;


    public DrmInfo(@DrmType int drmType) {
        this.drmType = drmType;
    }

    /**
     * Get DRM type
     * @return DRM type
     */
    public @DrmType int getDrmType() {
        return drmType;
    }
    
    private @DrmType int drmType;

}
