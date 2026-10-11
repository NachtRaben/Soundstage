package com.nachtraben.soundstage.audio.video;

import org.jetbrains.annotations.Nullable;

public interface SoundChoices {
    @Nullable String language();

    @Nullable String trackChoice(String identifier);

    void offered();
}
