package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.audio.video.SoundChoices;
import com.nachtraben.soundstage.core.video.AudioChoice;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

final class TrackAudioChoice {

    private final String identifier;
    private volatile SoundChoices host;
    private List<AudioChoice.Entry> entries;
    private String key;

    TrackAudioChoice(String identifier) {
        this.identifier = identifier;
    }

    void host(@Nullable SoundChoices host) {
        this.host = host;
    }

    boolean wanted() {
        SoundChoices told = host;
        return told != null && (told.language() != null || told.trackChoice(identifier) != null);
    }

    String begin(List<AudioChoice.Entry> offered) {
        SoundChoices told = host;
        String resolved;
        synchronized (this) {
            entries = List.copyOf(offered);
            key = AudioChoice.resolve(entries, told == null ? null : told.language(),
                    told == null ? null : told.trackChoice(identifier));
            resolved = key;
        }
        if (told != null) told.offered();
        return resolved;
    }

    boolean reapply() {
        SoundChoices told = host;
        synchronized (this) {
            if (entries == null || told == null) return false;
            String before = key;
            key = AudioChoice.resolve(entries, told.language(), told.trackChoice(identifier));
            return !Objects.equals(before, key);
        }
    }

    void withdraw() {
        synchronized (this) {
            entries = null;
            key = null;
        }
        SoundChoices told = host;
        if (told != null) told.offered();
    }

    synchronized @Nullable List<AudioChoice.Entry> entries() {
        return entries;
    }

    synchronized @Nullable String key() {
        return key;
    }
}
