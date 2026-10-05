package com.nachtraben.soundstage.core;

import java.util.Objects;
import java.util.function.Supplier;

public final class StreamRenderGate {

    private boolean audioOwned;

    public synchronized boolean renderVirtual(Runnable renderer) {
        Objects.requireNonNull(renderer, "renderer");
        if (audioOwned) {
            return false;
        }
        renderer.run();
        return true;
    }

    public synchronized <T> T renderAudio(Supplier<T> renderer) {
        Objects.requireNonNull(renderer, "renderer");
        audioOwned = true;
        return renderer.get();
    }

    public synchronized boolean isAudioOwned() {
        return audioOwned;
    }
}
