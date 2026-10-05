package com.nachtraben.soundstage.core.video;

import java.util.ArrayList;
import java.util.List;

public final class ReceiverCues {

    public static final int MAX_HELD_CUES = 500;

    private int generation = Integer.MIN_VALUE;
    private final List<SubtitleCue> cues = new ArrayList<>();

    public void apply(int packetGeneration, boolean reset, List<SubtitleCue> incoming) {
        if (packetGeneration != generation) {
            generation = packetGeneration;
            cues.clear();
        } else if (reset) {
            cues.clear();
        }
        for (SubtitleCue cue : incoming) {
            if (cues.size() >= MAX_HELD_CUES) cues.remove(0);
            cues.add(cue);
        }
    }

    public int generation() {
        return generation;
    }

    public String activeText(long trackMillis) {
        SubtitleCue active = active(trackMillis);
        return active == null ? null : active.visibleText(trackMillis);
    }

    private SubtitleCue active(long trackMillis) {
        SubtitleCue active = null;
        for (SubtitleCue cue : cues) {
            if (cue.containsMillis(trackMillis) && (active == null || cue.startMs() >= active.startMs())) {
                active = cue;
            }
        }
        return active;
    }

    public int size() {
        return cues.size();
    }

    public Long startAt(long trackMillis) {
        SubtitleCue active = active(trackMillis);
        return active == null ? null : active.startMs();
    }
}
