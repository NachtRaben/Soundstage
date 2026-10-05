package com.nachtraben.soundstage.audio.video;

import com.nachtraben.soundstage.core.video.CueTrack;
import com.nachtraben.soundstage.core.video.SubtitleChoice;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface SubtitleSource {

    List<SubtitleChoice.Option> options();

    @Nullable String autoKey(@Nullable String playerLanguage);

    void start(String key, Consumer<CueTrack> deliver, BooleanSupplier wanted);
}
