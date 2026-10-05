package com.nachtraben.soundstage.client.gui;

import dev.armadeus.armalib.ui.UiDraw;
import dev.armadeus.armalib.ui.UiGraphics;
import dev.armadeus.armalib.ui.UiIcon;
import dev.armadeus.armalib.ui.UiSegmented;
import dev.armadeus.armalib.ui.UiSwitch;
import com.nachtraben.soundstage.block.ReceiverBlockEntity;
import com.nachtraben.soundstage.block.ScreenBlock;
import com.nachtraben.soundstage.block.ScreenBlockEntity;
import com.nachtraben.soundstage.config.AppearanceSettings;
import com.nachtraben.soundstage.config.SoundstageAppearance;
import com.nachtraben.soundstage.core.video.DisplayLayout;
import com.nachtraben.soundstage.network.DisplayUnlinkC2SPacket;
import com.nachtraben.soundstage.network.ScreenFitModeC2SPacket;
import com.nachtraben.soundstage.network.SpeakerControlC2SPacket;
import dev.armadeus.armalib.platform.client.ClientNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DisplaysTab extends ReceiverTab {

    private static final int VIDEO_Y = 32;
    private static final int SUBTITLE_TRACK_Y = 58;
    private static final int LIST_HEADING_Y = 84;
    private static final int LIST_TOP = 96;
    private static final int ROW_HEIGHT = 27;
    private static final int PREFERENCES_HEIGHT = 34;
    private static final int FIT_WIDTH = 64, UNLINK_WIDTH = 16, CONTROL_HEIGHT = 14;

    private record Display(BlockPos anchor, int widthCells, int heightCells, Direction facing,
                           ScreenBlockEntity.FitMode mode) {}

    private final List<Display> displays = new ArrayList<>();
    private int unloadedScreens;
    private double scroll;
    private int maxScroll;
    private int refreshTicks;

    DisplaysTab(AudioPlayerScreen screen) {
        super(screen);
    }

    private int right() {
        return x + width - AudioPlayerScreen.PADDING;
    }

    private int preferencesTop() {
        return y + height - PREFERENCES_HEIGHT;
    }

    private int listTop() {
        return screen.panelY() + LIST_TOP;
    }

    private int listBottom() {
        return preferencesTop() - 6;
    }

    @Override
    void init(int x, int y, int width, int height) {
        super.init(x, y, width, height);
        int top = screen.panelY();
        int right = right();
        refresh();

        screen.addTabWidget(new UiSwitch(THEME, right - UiSwitch.WIDTH, top + VIDEO_Y + 5,
                Component.translatable("gui.soundstage.receiver.video"), () -> screen.pictureEnabled, () -> {
            screen.pictureEnabled = !screen.pictureEnabled;
            screen.sendControl(SpeakerControlC2SPacket.Action.TOGGLE_PICTURE, "", 0);
        }));
        int choiceWidth = Math.min(150, right - (x + AudioPlayerScreen.PADDING)
                - font().width(Component.translatable("gui.soundstage.display.subtitle_track")) - 10);
        BlockPos receiverPos = screen.pos;
        screen.addTabWidget(SubtitleChoiceButton.create(THEME, right - choiceWidth, top + SUBTITLE_TRACK_Y - 3,
                choiceWidth, () -> receiverPos, receiverPos));

        int prefTop = preferencesTop() + 14;
        screen.addTabWidget(new UiSwitch(THEME, x + AudioPlayerScreen.PADDING + font().width(
                Component.translatable("gui.soundstage.receiver.subtitles")) + 8, prefTop,
                Component.translatable("gui.soundstage.receiver.subtitles"), SoundstageAppearance::showSubtitles,
                () -> SoundstageAppearance.setShowSubtitles(!SoundstageAppearance.showSubtitles())));
        List<Component> sizes = List.of(
                Component.translatable("gui.soundstage.display.subtitle_size.small"),
                Component.translatable("gui.soundstage.display.subtitle_size.medium"),
                Component.translatable("gui.soundstage.display.subtitle_size.large"));
        int sizeWidth = UiSegmented.widthFor(sizes, 6);
        screen.addTabWidget(new UiSegmented(THEME, right - sizeWidth, prefTop - 1, sizeWidth, 15,
                Component.translatable("gui.soundstage.receiver.subtitle_size"), sizes,
                () -> SoundstageAppearance.subtitleSize().ordinal(),
                index -> SoundstageAppearance.setSubtitleSize(AppearanceSettings.SubtitleSize.values()[index])));
    }

    @Override
    void tick() {
        if (++refreshTicks % 20 == 0) refresh();
    }

    private void refresh() {
        displays.clear();
        unloadedScreens = 0;
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getBlockEntity(screen.pos) instanceof ReceiverBlockEntity receiver)) return;
        Map<BlockPos, Display> byAnchor = new LinkedHashMap<>();
        java.util.Set<BlockPos> covered = new java.util.HashSet<>();
        for (BlockPos member : receiver.getScreens()) {
            if (covered.contains(member)) continue;
            if (!level.isLoaded(member) || !(level.getBlockEntity(member) instanceof ScreenBlockEntity screenBlock)) {
                unloadedScreens++;
                continue;
            }
            Direction facing = screenBlock.getBlockState().getValue(ScreenBlock.FACING);
            DisplayLayout layout = ScreenBlockEntity.findDisplay(level, member, facing);
            covered.addAll(ScreenBlockEntity.memberPositions(member, facing, layout));
            BlockPos anchor = ScreenBlockEntity.anchorPosition(member, facing, layout);
            byAnchor.computeIfAbsent(anchor, key -> {
                ScreenBlockEntity.FitMode mode = level.getBlockEntity(key) instanceof ScreenBlockEntity anchorBlock
                        ? anchorBlock.getFitMode() : ScreenBlockEntity.FitMode.FIT;
                return new Display(key, layout.widthCells(), layout.heightCells(), facing, mode);
            });
        }
        displays.addAll(byAnchor.values());
    }

    @Override
    void renderBackground(UiGraphics g, int mouseX, int mouseY, float partialTick) {
        int top = screen.panelY();
        var font = font();
        int left = x + AudioPlayerScreen.PADDING, right = right();

        g.text(font, Component.translatable("gui.soundstage.receiver.video"), left, top + VIDEO_Y + 1, THEME.text());
        paragraph(g, Component.translatable("gui.soundstage.receiver.video_hint"), left, top + VIDEO_Y + 13,
                right - UiSwitch.WIDTH - 10 - left, 1, THEME.dim());

        g.text(font, Component.translatable("gui.soundstage.display.subtitle_track"), left, top + SUBTITLE_TRACK_Y + 1,
                THEME.text());

        heading(g, Component.translatable("gui.soundstage.receiver.linked_displays", displays.size()), left,
                top + LIST_HEADING_Y, right);

        int listTop = listTop(), listBottom = listBottom();
        int rows = displays.size() + (unloadedScreens > 0 ? 1 : 0);
        maxScroll = Math.max(0, rows * ROW_HEIGHT - (listBottom - listTop));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        g.enableScissor(x + 8, listTop, x + width - 8, listBottom);
        if (rows == 0) {
            g.text(font, Component.translatable("gui.soundstage.receiver.no_displays"), left, listTop + 4, THEME.dim());
        }
        for (int i = 0; i < displays.size(); i++) {
            Display display = displays.get(i);
            int rowTop = listTop - (int) scroll + i * ROW_HEIGHT;
            UiDraw.roundRect(g, x + 10, rowTop, width - 20, ROW_HEIGHT - 3, 4, THEME.surface());
            UiDraw.icon(g, UiIcon.DISPLAY, left + 2, rowTop + 5, 14, THEME.muted());
            g.text(font, Component.translatable("gui.soundstage.receiver.display_size", display.widthCells(),
                    display.heightCells()), left + 22, rowTop + 4, THEME.text());
            g.text(font, Component.translatable("gui.soundstage.receiver.display_where",
                    Component.translatable("gui.soundstage.receiver.facing." + display.facing().getSerializedName()),
                    display.anchor().getX(), display.anchor().getY(), display.anchor().getZ()),
                    left + 22, rowTop + 14, THEME.dim());
            drawFit(g, display, rowTop, mouseX, mouseY);
            int unlinkX = right - UNLINK_WIDTH;
            int controlTop = rowTop + (ROW_HEIGHT - 3 - CONTROL_HEIGHT) / 2;
            boolean hot = inside(mouseX, mouseY, unlinkX, controlTop, UNLINK_WIDTH, CONTROL_HEIGHT);
            if (hot) UiDraw.roundRect(g, unlinkX, controlTop, UNLINK_WIDTH, CONTROL_HEIGHT, 3, 0x33FFFFFF);
            UiDraw.iconCentred(g, UiIcon.CLOSE, unlinkX + UNLINK_WIDTH / 2.0, controlTop + CONTROL_HEIGHT / 2.0, 9,
                    hot ? THEME.danger() : THEME.muted());
        }
        if (unloadedScreens > 0) {
            int rowTop = listTop - (int) scroll + displays.size() * ROW_HEIGHT;
            g.text(font, Component.translatable("gui.soundstage.receiver.displays_out_of_range", unloadedScreens),
                    left, rowTop + 8, THEME.dim());
        }
        g.disableScissor();

        int prefTop = preferencesTop();
        heading(g, Component.translatable("gui.soundstage.display.your_preferences"), left, prefTop, right);
        g.text(font, Component.translatable("gui.soundstage.receiver.subtitles"), left, prefTop + 17, THEME.text());
        Component size = Component.translatable("gui.soundstage.receiver.subtitle_size");
        List<Component> sizes = List.of(
                Component.translatable("gui.soundstage.display.subtitle_size.small"),
                Component.translatable("gui.soundstage.display.subtitle_size.medium"),
                Component.translatable("gui.soundstage.display.subtitle_size.large"));
        g.text(font, size, right - UiSegmented.widthFor(sizes, 6) - 6 - font.width(size), prefTop + 17,
                THEME.muted());
    }

    private void drawFit(UiGraphics g, Display display, int rowTop, int mouseX, int mouseY) {
        int left = fitX(), top = rowTop + (ROW_HEIGHT - 3 - CONTROL_HEIGHT) / 2;
        UiDraw.roundRect(g, left, top, FIT_WIDTH, CONTROL_HEIGHT, 3.5, THEME.panel());
        double cell = (FIT_WIDTH - 2) / 2.0;
        var font = font();
        for (int i = 0; i < 2; i++) {
            ScreenBlockEntity.FitMode mode = ScreenBlockEntity.FitMode.values()[i];
            double cellLeft = left + 1 + cell * i;
            boolean chosen = display.mode() == mode;
            boolean hot = !chosen && inside(mouseX, mouseY, cellLeft, top, cell, CONTROL_HEIGHT);
            if (chosen) UiDraw.roundRect(g, cellLeft, top + 1, cell, CONTROL_HEIGHT - 2, 2.5, THEME.surfaceRaised());
            else if (hot) UiDraw.roundRect(g, cellLeft, top + 1, cell, CONTROL_HEIGHT - 2, 2.5, 0x22FFFFFF);
            Component label = Component.translatable(mode == ScreenBlockEntity.FitMode.FILL
                    ? "gui.soundstage.display.picture.fill" : "gui.soundstage.display.picture.fit");
            g.text(font, label, (int) Math.round(cellLeft + (cell - font.width(label)) / 2), top + 3,
                    chosen ? THEME.text() : hot ? 0xFFC9D3DC : THEME.muted());
        }
    }

    private int fitX() {
        return right() - UNLINK_WIDTH - 6 - FIT_WIDTH;
    }

    @Override
    void renderForeground(UiGraphics g, int mouseX, int mouseY, float partialTick) {
        int row = rowAt(mouseX, mouseY);
        if (row < 0) return;
        int controlTop = listTop() - (int) scroll + row * ROW_HEIGHT + (ROW_HEIGHT - 3 - CONTROL_HEIGHT) / 2;
        if (inside(mouseX, mouseY, right() - UNLINK_WIDTH, controlTop, UNLINK_WIDTH, CONTROL_HEIGHT)) {
            screen.tooltip(g, Component.translatable("gui.soundstage.receiver.unlink_display"), mouseX, mouseY);
        }
    }

    private int rowAt(double mouseX, double mouseY) {
        int listTop = listTop();
        if (mouseY < listTop || mouseY >= listBottom() || mouseX < x + 10 || mouseX >= x + width - 10) return -1;
        int row = (int) ((mouseY - listTop + scroll) / ROW_HEIGHT);
        return row >= 0 && row < displays.size() ? row : -1;
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = rowAt(mouseX, mouseY);
        if (row < 0) return false;
        Display display = displays.get(row);
        int controlTop = listTop() - (int) scroll + row * ROW_HEIGHT + (ROW_HEIGHT - 3 - CONTROL_HEIGHT) / 2;
        if (inside(mouseX, mouseY, right() - UNLINK_WIDTH, controlTop, UNLINK_WIDTH, CONTROL_HEIGHT)) {
            ClientNetwork.sendToServer(new DisplayUnlinkC2SPacket(display.anchor()));
            playClick();
            return true;
        }
        double cell = (FIT_WIDTH - 2) / 2.0;
        for (int i = 0; i < 2; i++) {
            ScreenBlockEntity.FitMode mode = ScreenBlockEntity.FitMode.values()[i];
            if (inside(mouseX, mouseY, fitX() + 1 + cell * i, controlTop, cell, CONTROL_HEIGHT) && display.mode() != mode) {
                ClientNetwork.sendToServer(new ScreenFitModeC2SPacket(display.anchor(), mode.ordinal()));
                displays.set(row, new Display(display.anchor(), display.widthCells(), display.heightCells(),
                        display.facing(), mode));
                playClick();
                return true;
            }
        }
        return true;
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (mouseY < listTop() || mouseY > listBottom()) return false;
        scroll = Math.max(0, Math.min(maxScroll, scroll - scrollY * ROW_HEIGHT));
        return true;
    }
}
