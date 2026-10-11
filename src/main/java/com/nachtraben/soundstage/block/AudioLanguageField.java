package com.nachtraben.soundstage.block;

import com.nachtraben.soundstage.core.video.AudioChoice;
import dev.armadeus.armalib.platform.block.SaveView;
import org.jetbrains.annotations.Nullable;

public final class AudioLanguageField {

    static final String KEY = "audioLanguage";

    private AudioLanguageField() {
    }

    public static void save(SaveView.Writer out, @Nullable String language) {
        out.putString(KEY, language == null ? "" : language);
    }

    public static @Nullable String load(SaveView.Reader in) {
        return AudioChoice.languageTag(in.getString(KEY, ""));
    }
}
