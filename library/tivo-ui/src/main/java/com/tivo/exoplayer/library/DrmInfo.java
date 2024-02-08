package com.tivo.exoplayer.library;


import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import java.util.UUID;
import org.checkerframework.checker.units.qual.C;

/** Base class to pass DRM specific metadata to
 * {@link com.tivo.exoplayer.library.SimpleExoPlayerFactory#playUrl(Uri, DrmInfo, boolean)}
 */
public class DrmInfo {
    public static final UUID VCAS_UUID = UUID.fromString("2537711a-f2d1-42c6-a9e7-fbf71440395c");
    public static final UUID TIVO_CRYPT_UUID = UUID.fromString("fcb7c33d-e404-470c-bbe5-d0fb085d2627");

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

    public static final DrmInfo NO_DRM = new DrmInfo(CLEAR);

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
