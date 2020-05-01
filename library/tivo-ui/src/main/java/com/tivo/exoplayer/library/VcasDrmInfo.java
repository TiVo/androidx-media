package com.tivo.exoplayer.library;

/**
 * Represents Verimatrix DRM information.
 */
public class VcasDrmInfo extends DrmInfo {

    public VcasDrmInfo(String bootAddr, String caId,
                       String storeDir, boolean debugOn) {
        super(DrmType.VCAS);
        this.bootAddr = bootAddr;
        this.caId = caId;
        this.storeDir = storeDir;
        this.debugOn = debugOn;
    }

    public String getBootAddr() {
        return bootAddr;
    }


    public String getCaId() {
        return caId;
    }

    public boolean isDebugOn() {
        return debugOn;
    }

    public String getStoreDir() {
        return storeDir;
    }

    /**
     * VCAS boot server.
     */
    private String bootAddr;

    /**
     * Registered CA device ID.
     */
    private String caId;

    /**
     * Debug switch.
     */
    private boolean debugOn;

    /**
     * Writeable path to store data.
     */
    private String storeDir;
}
