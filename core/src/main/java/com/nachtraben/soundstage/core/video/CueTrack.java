package com.nachtraben.soundstage.core.video;

import java.util.List;
import java.util.Optional;

public record CueTrack(List<SubtitleCue> cues) {

    public static final CueTrack EMPTY = new CueTrack(List.of());

    public CueTrack {
        cues = List.copyOf(cues);
    }

    public Optional<SubtitleCue> activeAt(long timeMs) {
        SubtitleCue active = null;
        for (SubtitleCue cue : cues) {
            if (cue.containsMillis(timeMs) && (active == null || cue.startMs() >= active.startMs())) active = cue;
        }
        return Optional.ofNullable(active);
    }
}
