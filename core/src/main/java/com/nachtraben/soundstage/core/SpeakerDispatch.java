package com.nachtraben.soundstage.core;

import java.util.ArrayList;
import java.util.List;

public final class SpeakerDispatch {

    private SpeakerDispatch() {
    }

    public static void reconcile(SpeakerHost host, List<SpeakerId> targets, byte[] stereoPcm,
                                 long sequence) {
        for (SpeakerId held : new ArrayList<>(host.heldSpeakers())) {
            if (!targets.contains(held) || host.needsRestart(held)) {
                host.release(held);
            }
        }

        for (SpeakerId target : targets) {
            boolean held = host.heldSpeakers().contains(target);

            if (held && host.hasLostItsChannel(target)) {
                host.release(target);
                held = false;
            }

            if (!held) {
                host.begin(target);
            }

            if (!host.hasChannel(target)) {
                host.traceWithoutChannel(target, stereoPcm, sequence);
            }
        }

        host.keepAlive();
    }
}
