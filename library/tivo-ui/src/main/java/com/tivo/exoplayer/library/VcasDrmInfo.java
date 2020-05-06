package com.tivo.exoplayer.library;

/**
 * Verimatrix specific DRM information.
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

    /**
     * Get VCAS boot server address.
     * @return address as addr:port
     */
    public String getBootAddr() {
        return bootAddr;
    }

    /**
     * Get CA device ID registered with VCAS.
     * @return device ID
     */
    public String getCaId() {
        return caId;
    }

    /**
     * Checks if debug is enabled
     * @return whether debug is enabled
     */
    public boolean isDebugOn() {
        return debugOn;
    }

    /**
     * Writable path to store data.
     * @return file path to store
     */
    public String getStoreDir() {
        return storeDir;
    }


    private String bootAddr;
    private String caId;
    private boolean debugOn;
    private String storeDir;
}
