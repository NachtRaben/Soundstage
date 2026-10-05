package com.nachtraben.soundstage.client.gui;

import com.nachtraben.soundstage.client.video.ClientSubtitleOptions;
import com.nachtraben.soundstage.core.video.SubtitleChoice;
import com.nachtraben.soundstage.network.SubtitleChoiceC2SPacket;
import com.nachtraben.soundstage.network.SubtitleOptionsS2CPacket;
import dev.armadeus.armalib.core.ui.Theme;
import dev.armadeus.armalib.platform.client.ClientNetwork;
import dev.armadeus.armalib.ui.UiDropdown;
import com.nachtraben.soundstage.RigStaging;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

final class SubtitleChoiceButton {

    private SubtitleChoiceButton() {}

    static UiDropdown<String> create(Theme theme, int x, int y, int width, Supplier<@Nullable BlockPos> receiverPos,
                                     BlockPos sendAt) {
        return new UiDropdown<>(theme, x, y, width, 15, Component.translatable("gui.soundstage.display.subtitle_track"),
                () -> options(ClientSubtitleOptions.get(receiverPos.get())),
                () -> {
                    SubtitleOptionsS2CPacket options = ClientSubtitleOptions.get(receiverPos.get());
                    return options == null ? SubtitleChoice.AUTO : options.choice();
                },
                choice -> {
                    SubtitleOptionsS2CPacket options = ClientSubtitleOptions.get(receiverPos.get());
                    if (options == null) return;
                    if (RigStaging.fault("dropdown-ignores-rows")) return;
                    ClientNetwork.sendToServer(new SubtitleChoiceC2SPacket(sendAt, choice));
                });
    }

    static List<UiDropdown.Option<String>> options(@Nullable SubtitleOptionsS2CPacket packet) {
        List<UiDropdown.Option<String>> rows = new ArrayList<>();
        if (packet == null) {
            rows.add(new UiDropdown.Option<>(Component.translatable("gui.soundstage.display.subtitle_track.none"),
                    SubtitleChoice.AUTO));
            return rows;
        }
        Component resolved = Component.translatable("gui.soundstage.display.subtitle_track.auto_off");
        if (packet.autoKey() != null) {
            for (SubtitleChoice.Option option : packet.options()) {
                if (option.key().equals(packet.autoKey())) resolved = Component.literal(option.label());
            }
        }
        rows.add(new UiDropdown.Option<>(Component.translatable("gui.soundstage.display.subtitle_track.auto", resolved),
                SubtitleChoice.AUTO));
        rows.add(new UiDropdown.Option<>(Component.translatable("gui.soundstage.display.subtitle_track.off"),
                SubtitleChoice.OFF));
        for (SubtitleChoice.Option option : packet.options()) {
            rows.add(new UiDropdown.Option<>(Component.literal(option.label()), option.key()));
        }
        return rows;
    }
}
