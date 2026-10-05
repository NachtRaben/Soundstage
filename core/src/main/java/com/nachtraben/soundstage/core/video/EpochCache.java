package com.nachtraben.soundstage.core.video;

import java.util.function.Supplier;

public final class EpochCache<T> {

    private long epoch;
    private T value;
    private boolean has;

    public T get(long currentEpoch, Supplier<T> compute) {
        if (!has || epoch != currentEpoch) {
            value = compute.get();
            epoch = currentEpoch;
            has = true;
        }
        return value;
    }
}
