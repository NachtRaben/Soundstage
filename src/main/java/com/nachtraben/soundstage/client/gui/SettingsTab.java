package com.nachtraben.soundstage.client.gui;

import com.nachtraben.soundstage.block.IAudioReceiver;
import com.nachtraben.soundstage.client.audio.LiveInputClient;
import com.nachtraben.soundstage.network.SpeakerControlC2SPacket;
import dev.armadeus.armalib.ui.UiButton;
import dev.armadeus.armalib.ui.UiDraw;
import dev.armadeus.armalib.ui.UiGraphics;
import dev.armadeus.armalib.ui.UiIcon;
import dev.armadeus.armalib.ui.UiSwitch;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class SettingsTab extends ReceiverTab {

    private static final int FIRST_ROW_Y = 32;
    private static final int ROW_HEIGHT = 46;
    private static final int TEXT_WIDTH = 300;

    SettingsTab(AudioPlayerScreen screen) {
        super(screen);
    }

    private int rowTop(int row) {
        return screen.panelY() + FIRST_ROW_Y + row * ROW_HEIGHT;
    }

    private int right() {
        return x + width - AudioPlayerScreen.PADDING;
    }

    private boolean mayToggleLock() {
        IAudioReceiver receiver = screen.receiverBlock();
        var player = Minecraft.getInstance().player;
        return receiver != null && player != null && receiver.mayToggleLock(player);
    }

    @Override
    void init(int x, int y, int width, int height) {
        super.init(x, y, width, height);
        int right = right();
        screen.addTabWidget(new UiSwitch(THEME, right - UiSwitch.WIDTH, rowTop(0) + 12,
                Component.translatable("gui.soundstage.receiver.levelling"), () -> screen.levelling, () -> {
            screen.levelling = !screen.levelling;
            screen.sendControl(SpeakerControlC2SPacket.Action.TOGGLE_LEVELLING, "", 0);
        }));
        screen.addTabWidget(new UiSwitch(THEME, right - UiSwitch.WIDTH, rowTop(1) + 12,
                Component.translatable("gui.soundstage.receiver.lock"), () -> screen.locked, () -> {
            screen.locked = !screen.locked;
            screen.sendControl(SpeakerControlC2SPacket.Action.TOGGLE_LOCK, "", 0);
        }).enabled(this::mayToggleLock));
        Component open = shareLabel();
        int openWidth = Math.max(
                UiButton.widthFor(Component.translatable("gui.soundstage.receiver.share_open"), true, 10, 8),
                UiButton.widthFor(Component.translatable("gui.soundstage.player.sharing"), true, 10, 8));
        screen.addTabWidget(new UiButton(THEME, right - openWidth, rowTop(2) + 10, openWidth, 17, UiButton.Look.PILL,
                () -> Minecraft.getInstance().setScreen(new LiveInputScreen(screen.pos, screen)))
                .icon(UiIcon.SHARE).iconSize(10).label(this::shareLabel)
                .selected(() -> LiveInputClient.isSharingTo(screen.pos)));
    }

    private Component shareLabel() {
        return Component.translatable(LiveInputClient.isSharingTo(screen.pos)
                ? "gui.soundstage.player.sharing" : "gui.soundstage.receiver.share_open");
    }

    @Override
    void renderBackground(UiGraphics g, int mouseX, int mouseY, float partialTick) {
        IAudioReceiver receiver = screen.receiverBlock();
        var player = Minecraft.getInstance().player;
        boolean owner = receiver != null && player != null && player.getUUID().equals(receiver.getOwnerUUID());
        row(g, 0, "gui.soundstage.receiver.levelling",
                Component.translatable("gui.soundstage.receiver.levelling_hint"));
        row(g, 1, "gui.soundstage.receiver.lock", Component.translatable(!mayToggleLock()
                ? "gui.soundstage.receiver.lock_hint_other"
                : owner ? "gui.soundstage.receiver.lock_hint_owner" : "gui.soundstage.receiver.lock_hint_operator"));
        row(g, 2, "gui.soundstage.receiver.share", Component.translatable("gui.soundstage.receiver.share_hint"));
    }

    private void row(UiGraphics g, int index, String titleKey, Component hint) {
        int top = rowTop(index);
        int left = x + AudioPlayerScreen.PADDING;
        UiDraw.roundRect(g, x + 8, top, width - 16, ROW_HEIGHT - 5, 5, THEME.surface());
        g.text(font(), Component.translatable(titleKey), left + 2, top + 7, THEME.text());
        paragraph(g, hint, left + 2, top + 19, TEXT_WIDTH, 2, THEME.muted());
    }
}
