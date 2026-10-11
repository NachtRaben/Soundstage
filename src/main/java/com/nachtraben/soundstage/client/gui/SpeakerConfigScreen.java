package com.nachtraben.soundstage.client.gui;

import com.nachtraben.soundstage.block.speaker.AbstractSpeakerBlock;
import com.nachtraben.soundstage.block.speaker.SpeakerBlockEntity;
import com.nachtraben.soundstage.client.SoundstageClientInit;
import com.nachtraben.soundstage.network.SpeakerAimC2SPacket;
import com.nachtraben.soundstage.network.SpeakerDelayC2SPacket;
import com.nachtraben.soundstage.network.SpeakerGrilleC2SPacket;
import com.nachtraben.soundstage.network.SpeakerRangeC2SPacket;
import com.nachtraben.soundstage.network.SpeakerVolumeC2SPacket;
import dev.armadeus.armalib.core.ui.Theme;
import dev.armadeus.armalib.platform.client.ClientNetwork;
import dev.armadeus.armalib.ui.UiButton;
import dev.armadeus.armalib.ui.UiGraphics;
import dev.armadeus.armalib.ui.UiIcon;
import dev.armadeus.armalib.ui.UiScreen;
import dev.armadeus.armalib.ui.UiSlider;
import dev.armadeus.armalib.ui.UiSwitch;
import dev.armadeus.armalib.ui.UiText;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;

public class SpeakerConfigScreen extends UiScreen {
    static final Theme THEME = ScreenTheme.THEME;

    private static final int ROW_HEIGHT = 16;
    private static final int LABEL_WIDTH = 50;
    private static final int VALUE_WIDTH = 50;

    private final SpeakerBlockEntity sbe;
    private boolean grilleOn;
    private UiSlider pitch, yaw, range, volume, delay;
    private UiButton openReceiver;

    public SpeakerConfigScreen(SpeakerBlockEntity sbe) {
        super(sbe.getBlockState().getBlock().getName(), UiIcon.SPEAKER, THEME, null, 300, 196);
        this.sbe = sbe;
        this.grilleOn = sbe.getBlockState().getValue(AbstractSpeakerBlock.GRILLE);
    }

    private int aimY() { return bodyY(); }
    private int soundY() { return bodyY() + 50; }
    private int grilleY() { return bodyY() + 116; }
    private int linkY() { return bodyY() + 142; }

    @Override
    protected void build() {
        float initialPitch = sbe.getPitch();
        float initialYaw = sbe.getYawOffset();

        pitch = slider(aimY() + 12, initialPitch, -90, 90, 22.5, 0.5, UiSlider.Kind.CENTRED,
                value -> Component.translatable("gui.soundstage.speaker.pitch",
                        String.format(Locale.ROOT, "%.1f", value)),
                value -> ClientNetwork.sendToServer(new SpeakerAimC2SPacket(sbe.getBlockPos(), value.floatValue(),
                        yaw == null ? initialYaw : (float) yaw.actualValue())));
        yaw = slider(aimY() + 12 + ROW_HEIGHT, initialYaw, -180, 180, 22.5, 0.5, UiSlider.Kind.CENTRED,
                value -> Component.translatable("gui.soundstage.speaker.yaw",
                        String.format(Locale.ROOT, "%.1f", value)),
                value -> ClientNetwork.sendToServer(new SpeakerAimC2SPacket(sbe.getBlockPos(),
                        pitch == null ? initialPitch : (float) pitch.actualValue(), value.floatValue())));
        range = slider(soundY() + 12, sbe.getRange(), SpeakerBlockEntity.MIN_RANGE, SpeakerBlockEntity.MAX_RANGE, 4.0,
                Double.NaN, UiSlider.Kind.PLAIN,
                value -> Component.translatable("gui.soundstage.speaker.range", value.intValue()),
                value -> ClientNetwork.sendToServer(new SpeakerRangeC2SPacket(sbe.getBlockPos(), value.intValue())));
        volume = slider(soundY() + 12 + ROW_HEIGHT, sbe.getVolumePercent(), SpeakerBlockEntity.MIN_VOLUME_PERCENT,
                SpeakerBlockEntity.MAX_VOLUME_PERCENT, 1.0, Double.NaN, UiSlider.Kind.PLAIN,
                value -> Component.translatable("gui.soundstage.speaker.volume", value.intValue()),
                value -> ClientNetwork.sendToServer(new SpeakerVolumeC2SPacket(sbe.getBlockPos(), value.intValue())));
        delay = slider(soundY() + 12 + 2 * ROW_HEIGHT, sbe.getDelayMs(), SpeakerBlockEntity.MIN_DELAY_MS,
                SpeakerBlockEntity.MAX_DELAY_MS, 20.0, Double.NaN, UiSlider.Kind.PLAIN,
                value -> Component.translatable("gui.soundstage.speaker.delay", value.intValue()),
                value -> ClientNetwork.sendToServer(new SpeakerDelayC2SPacket(sbe.getBlockPos(), value.intValue())));

        addRenderableWidget(new UiSwitch(THEME, right() - UiSwitch.WIDTH, grilleY() + 2,
                Component.translatable("gui.soundstage.speaker.grille_label"), () -> grilleOn, () -> {
            grilleOn = !grilleOn;
            ClientNetwork.sendToServer(new SpeakerGrilleC2SPacket(sbe.getBlockPos(), grilleOn));
        }));

        if (sbe.getReceiverPos() != null) {
            Component open = Component.translatable("gui.soundstage.speaker.open_receiver");
            int w = UiButton.widthFor(open, true, 10, 8);
            openReceiver = addRenderableWidget(new UiButton(THEME, right() - w, linkY() - 4, w, 17, UiButton.Look.PILL,
                    () -> SoundstageClientInit.openSpeakerScreen(sbe)).icon(UiIcon.MUSIC).iconSize(10).label(open));
        }
    }

    private UiSlider slider(int y, double current, double min, double max, double step, double mark, UiSlider.Kind kind,
                            Function<Double, Component> narration, Consumer<Double> onChange) {
        int x = left() + LABEL_WIDTH;
        UiSlider slider = new UiSlider(THEME, x, y, right() - VALUE_WIDTH - x, 12, current, min, max, step, mark, kind,
                narration, onChange);
        return addRenderableWidget(slider);
    }

    @Override
    protected void drawContent(UiGraphics g, int mouseX, int mouseY, float partialTick) {
        heading(g, Component.translatable("gui.soundstage.speaker.aim"), left(), aimY(), right());
        row(g, pitch, "gui.soundstage.speaker.pitch_label", value -> String.format(Locale.ROOT, "%.1f°", value));
        row(g, yaw, "gui.soundstage.speaker.yaw_label", value -> String.format(Locale.ROOT, "%.1f°", value));
        heading(g, Component.translatable("gui.soundstage.speaker.sound"), left(), soundY(), right());
        row(g, range, "gui.soundstage.speaker.range_label",
                value -> Component.translatable("gui.soundstage.speaker.range_value", (int) value).getString());
        row(g, volume, "gui.soundstage.speaker.volume_label", value -> (int) value + "%");
        row(g, delay, "gui.soundstage.speaker.delay_label", value -> (int) value + " ms");

        g.text(this.font, Component.translatable("gui.soundstage.speaker.grille_label"), left(), grilleY(),
                THEME.text());
        g.text(this.font, Component.translatable("gui.soundstage.speaker.grille_hint"), left(), grilleY() + 11,
                THEME.dim());

        BlockPos receiver = sbe.getReceiverPos();
        Component link = receiver == null ? Component.translatable("gui.soundstage.speaker.not_linked")
                : Component.translatable("gui.soundstage.speaker.linked_to", receiver.getX(), receiver.getY(),
                        receiver.getZ());
        int room = (openReceiver == null ? right() : openReceiver.getX() - 6) - left();
        g.text(this.font, UiText.ellipsize(this.font, link.getString(), room), left(), linkY(), THEME.muted());
    }

    private void row(UiGraphics g, UiSlider slider, String labelKey, DoubleFunction<String> value) {
        int textY = slider.getY() + 2;
        g.text(this.font, Component.translatable(labelKey), left(), textY, THEME.muted());
        String shown = value.apply(slider.actualValue());
        g.text(this.font, shown, right() - this.font.width(shown), textY, THEME.text());
    }
}
