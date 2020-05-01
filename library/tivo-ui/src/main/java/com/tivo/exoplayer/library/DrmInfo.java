package com.tivo.exoplayer.library;

public class DrmInfo {

    public DrmInfo(DrmType drmType) {
        this.drmType = drmType;
    }

    public enum DrmType {
        CLEAR, VCAS, WIDEVINE
    }

    public DrmType getDrmType() {
        return drmType;
    }

    public void setDrmType(DrmType drmType) {
        this.drmType = drmType;
    }

    private DrmType drmType;

}
