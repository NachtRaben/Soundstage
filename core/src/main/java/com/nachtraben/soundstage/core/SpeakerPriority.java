package com.nachtraben.soundstage.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SpeakerPriority {

    private SpeakerPriority() {
    }

    public static List<SpeakerId> nearestFirst(List<SpeakerId> speakers,
                                               double listenerX, double listenerY, double listenerZ) {
        if (speakers.size() < 2) {
            return speakers;
        }
        List<SpeakerId> sorted = new ArrayList<>(speakers);
        sorted.sort(Comparator.comparingDouble(s -> distanceSquared(s, listenerX, listenerY, listenerZ)));
        return sorted;
    }

    public static double distanceSquared(SpeakerId speaker, double x, double y, double z) {
        double dx = speaker.x() + 0.5 - x;
        double dy = speaker.y() + 0.5 - y;
        double dz = speaker.z() + 0.5 - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
