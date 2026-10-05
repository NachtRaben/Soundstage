package com.nachtraben.soundstage.core;

import java.util.Set;

public interface SpeakerHost {

    Set<SpeakerId> heldSpeakers();

    boolean needsRestart(SpeakerId speaker);

    boolean hasLostItsChannel(SpeakerId speaker);

    boolean hasChannel(SpeakerId speaker);

    void release(SpeakerId speaker);

    void begin(SpeakerId speaker);

    void traceWithoutChannel(SpeakerId speaker, byte[] stereoPcm, long sequence);

    void keepAlive();
}
