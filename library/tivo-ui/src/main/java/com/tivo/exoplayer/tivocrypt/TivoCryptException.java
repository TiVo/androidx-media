package com.tivo.exoplayer.tivocrypt;

import java.io.IOException;

public class TivoCryptException extends IOException {
    public static final int PHASE_INITIALIZING = 0;
    public static final int PHASE_DECRYPTING = 1;

    public TivoCryptException(int phase, int code) {
        super("TivoCrypt error code " + code + " while " +
                ((phase == PHASE_INITIALIZING) ? "initializing" :
                        "decrypting"));

        mPhase = phase;
        mCode = code;
    }

    public int getPhase() {
        return mPhase;
    }

    public int getCode() {
        return mCode;
    }

    private int mPhase;
    private int mCode;
}
