package com.nachtraben.soundstage.core;

public final class StartPhaseGrid {

    private boolean set;
    private double originFrac;
    private long originSeq;

    public synchronized long anchorFor(double designedExact) {
        if (!set) {
            originSeq = Math.round(designedExact);
            originFrac = designedExact;
            set = true;
            return originSeq;
        }
        return originSeq + Math.round(designedExact - originFrac);
    }

    public synchronized void reset() {
        set = false;
    }
}
