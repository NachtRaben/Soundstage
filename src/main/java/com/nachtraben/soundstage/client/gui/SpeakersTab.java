package com.nachtraben.soundstage.client.gui;

import dev.armadeus.armalib.ui.UiDraw;
import dev.armadeus.armalib.ui.UiGraphics;
import dev.armadeus.armalib.ui.UiIcon;
import dev.armadeus.armalib.ui.UiText;
import com.nachtraben.soundstage.block.ReceiverBlockEntity;
import com.nachtraben.soundstage.block.speaker.AbstractSpeakerBlock;
import com.nachtraben.soundstage.block.speaker.SpeakerBlockEntity;
import com.nachtraben.soundstage.core.ChannelMode;
import com.nachtraben.soundstage.network.SpeakerRoutingC2SPacket;
import dev.armadeus.armalib.platform.client.ClientNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

final class SpeakersTab extends ReceiverTab {

    private static final int HEADING_Y = 32;
    private static final int LIST_TOP = 44;
    private static final int ROW_HEIGHT = 27;
    private static final int CHANNEL_WIDTH = 78, CHANNEL_HEIGHT = 14;

    private static final class Entry {
        final BlockPos pos;
        final Component name;
        ChannelMode mode;

        Entry(BlockPos pos, Component name, ChannelMode mode) {
            this.pos = pos;
            this.name = name;
            this.mode = mode;
        }
    }

    private final List<Entry> speakers = new ArrayList<>();
    private int unloaded;
    private double scroll;
    private int maxScroll;
    private int refreshTicks;

    SpeakersTab(AudioPlayerScreen screen) {
        super(screen);
    }

    private int right() {
        return x + width - AudioPlayerScreen.PADDING;
    }

    private int listTop() {
        return screen.panelY() + LIST_TOP;
    }

    private int listBottom() {
        return y + height - 6;
    }

    @Override
    void init(int x, int y, int width, int height) {
        super.init(x, y, width, height);
        refresh();
    }

    @Override
    void tick() {
        if (++refreshTicks % 20 == 0) refresh();
    }

    private void refresh() {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getBlockEntity(screen.pos) instanceof ReceiverBlockEntity receiver)) {
            speakers.clear();
            return;
        }
        List<Entry> fresh = new ArrayList<>();
        unloaded = 0;
        for (BlockPos pos : receiver.getSpeakers()) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker)) {
                unloaded++;
                continue;
            }
            fresh.add(new Entry(pos, level.getBlockState(pos).getBlock().getName(), speaker.getChannelMode()));
        }
        speakers.clear();
        speakers.addAll(fresh);
    }

    private ChannelMode[] validModes(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockState(pos).getBlock() instanceof AbstractSpeakerBlock block
                ? block.getValidModes()
                : new ChannelMode[]{ChannelMode.MONO, ChannelMode.LEFT, ChannelMode.RIGHT};
    }

    private static Component modeLabel(ChannelMode mode) {
        return Component.translatable("gui.soundstage.channel." + mode.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    void renderBackground(UiGraphics g, int mouseX, int mouseY, float partialTick) {
        int top = screen.panelY();
        var font = font();
        int left = x + AudioPlayerScreen.PADDING, right = right();
        Component hint = Component.translatable("gui.soundstage.receiver.channel_hint");
        heading(g, Component.translatable("gui.soundstage.receiver.linked_speakers", speakers.size()), left, top + HEADING_Y,
                right - font.width(hint) - 8);
        g.text(font, hint, right - font.width(hint), top + HEADING_Y, THEME.dim());

        int listTop = listTop(), listBottom = listBottom();
        int rows = speakers.size() + (unloaded > 0 ? 1 : 0);
        maxScroll = Math.max(0, rows * ROW_HEIGHT - (listBottom - listTop));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        g.enableScissor(x + 8, listTop, x + width - 8, listBottom);
        if (rows == 0) {
            g.text(font, Component.translatable("gui.soundstage.routing.empty"), left, listTop + 4, THEME.dim());
        }
        var player = Minecraft.getInstance().player;
        for (int i = 0; i < speakers.size(); i++) {
            Entry entry = speakers.get(i);
            int rowTop = listTop - (int) scroll + i * ROW_HEIGHT;
            UiDraw.roundRect(g, x + 10, rowTop, width - 20, ROW_HEIGHT - 3, 4, THEME.surface());
            UiDraw.icon(g, UiIcon.SPEAKER, left + 2, rowTop + 5, 14, THEME.muted());
            g.text(font, UiText.ellipsize(font, entry.name.getString(), right - CHANNEL_WIDTH - 12 - (left + 22)),
                    left + 22, rowTop + 4, THEME.text());
            int distance = player == null ? -1 : (int) Math.round(Math.sqrt(player.distanceToSqr(
                    entry.pos.getX() + 0.5, entry.pos.getY() + 0.5, entry.pos.getZ() + 0.5)));
            g.text(font, Component.translatable(distance == 1 ? "gui.soundstage.receiver.speaker_where_one"
                    : "gui.soundstage.receiver.speaker_where", entry.pos.getX(), entry.pos.getY(), entry.pos.getZ(), distance),
                    left + 22, rowTop + 14, THEME.dim());

            int channelX = right - CHANNEL_WIDTH, channelTop = rowTop + (ROW_HEIGHT - 3 - CHANNEL_HEIGHT) / 2;
            boolean hot = inside(mouseX, mouseY, channelX, channelTop, CHANNEL_WIDTH, CHANNEL_HEIGHT)
                    && mouseY >= listTop && mouseY < listBottom;
            UiDraw.roundRect(g, channelX, channelTop, CHANNEL_WIDTH, CHANNEL_HEIGHT, 3.5,
                    hot ? THEME.surfaceRaised() : THEME.panel());
            Component label = modeLabel(entry.mode);
            g.text(font, label, channelX + (CHANNEL_WIDTH - font.width(label)) / 2, channelTop + 3, THEME.text());
            UiDraw.iconCentred(g, UiIcon.CHEVRON_RIGHT, channelX + CHANNEL_WIDTH - 7, channelTop + CHANNEL_HEIGHT / 2.0, 7,
                    hot ? THEME.text() : THEME.dim());
        }
        if (unloaded > 0) {
            int rowTop = listTop - (int) scroll + speakers.size() * ROW_HEIGHT;
            g.text(font, Component.translatable("gui.soundstage.receiver.speakers_out_of_range", unloaded),
                    left, rowTop + 8, THEME.dim());
        }
        g.disableScissor();
    }

    private int rowAt(double mouseX, double mouseY) {
        int listTop = listTop();
        if (mouseY < listTop || mouseY >= listBottom() || mouseX < x + 10 || mouseX >= x + width - 10) return -1;
        int row = (int) ((mouseY - listTop + scroll) / ROW_HEIGHT);
        return row >= 0 && row < speakers.size() ? row : -1;
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = rowAt(mouseX, mouseY);
        if (row < 0) return false;
        Entry entry = speakers.get(row);
        int channelTop = listTop() - (int) scroll + row * ROW_HEIGHT + (ROW_HEIGHT - 3 - CHANNEL_HEIGHT) / 2;
        if (!inside(mouseX, mouseY, right() - CHANNEL_WIDTH, channelTop, CHANNEL_WIDTH, CHANNEL_HEIGHT)) return true;
        ChannelMode[] modes = validModes(entry.pos);
        int at = java.util.Arrays.asList(modes).indexOf(entry.mode);
        int step = button == 1 ? -1 : 1;
        entry.mode = modes[Math.floorMod(at + step, modes.length)];
        ClientNetwork.sendToServer(new SpeakerRoutingC2SPacket(screen.pos, entry.pos, entry.mode.name()));
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
        refreshTicks = 0;
        return true;
    }

    @Override
    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (mouseY < listTop() || mouseY > listBottom()) return false;
        scroll = Math.max(0, Math.min(maxScroll, scroll - scrollY * ROW_HEIGHT));
        return true;
    }
}
