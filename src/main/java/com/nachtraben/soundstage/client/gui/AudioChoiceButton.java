package com.nachtraben.soundstage.client.gui;

import com.nachtraben.soundstage.core.video.AudioChoice;
import com.nachtraben.soundstage.network.SpeakerControlC2SPacket;
import dev.armadeus.armalib.core.ui.Theme;
import dev.armadeus.armalib.ui.UiDropdown;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

final class AudioChoiceButton {

    private AudioChoiceButton() {}

    static UiDropdown<String> create(Theme theme, int x, int y, int width, AudioPlayerScreen screen) {
        return new UiDropdown<>(theme, x, y, width, 13, Component.translatable("gui.soundstage.player.audio_track"),
                () -> rows(screen.audioOffer),
                () -> screen.audioOffer == null ? AudioChoice.DEFAULT : screen.audioOffer.selected(),
                choice -> screen.sendControl(SpeakerControlC2SPacket.Action.CHOOSE_AUDIO, choice, 0));
    }

    static List<UiDropdown.Option<String>> rows(AudioChoice.Offer offer) {
        List<UiDropdown.Option<String>> rows = new ArrayList<>();
        if (offer == null) return rows;
        rows.add(new UiDropdown.Option<>(offer.defaultLabel() == null
                ? Component.translatable("gui.soundstage.player.audio_track.default")
                : Component.translatable("gui.soundstage.player.audio_track.default_named", offer.defaultLabel()),
                AudioChoice.DEFAULT));
        for (AudioChoice.Option option : offer.options()) {
            rows.add(new UiDropdown.Option<>(Component.literal(option.label()), option.key()));
        }
        if (offer.missingLanguage() != null) {
            rows.add(new UiDropdown.Option<>(Component.translatable("gui.soundstage.player.audio_track.missing",
                    offer.missingLanguage()), offer.selected()));
        }
        return rows;
    }
}
