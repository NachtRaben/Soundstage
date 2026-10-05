package com.nachtraben.soundstage.core;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;

public final class AdmittedReference extends AudioReference {

    AdmittedReference(String identifier, String title, MediaContainerDescriptor containerDescriptor) {
        super(identifier, title, containerDescriptor);
    }

    static AdmittedReference of(AudioReference reference) {
        return reference instanceof AdmittedReference admitted ? admitted
                : new AdmittedReference(reference.identifier, reference.title, reference.containerDescriptor);
    }
}
