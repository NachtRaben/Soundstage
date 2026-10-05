package com.nachtraben.soundstage.client.gui;

import dev.armadeus.armalib.core.ui.Theme;
import dev.armadeus.armalib.ui.UiButton;
import dev.armadeus.armalib.ui.UiGraphics;
import dev.armadeus.armalib.ui.UiIcon;
import dev.armadeus.armalib.ui.UiScreen;
import dev.armadeus.armalib.ui.UiSegmented;
import dev.armadeus.armalib.ui.UiSwitch;
import com.nachtraben.soundstage.block.ScreenBlock;
import com.nachtraben.soundstage.block.ScreenBlockEntity;
import com.nachtraben.soundstage.config.AppearanceSettings;
import com.nachtraben.soundstage.config.SoundstageAppearance;
import com.nachtraben.soundstage.core.video.DisplayLayout;
import com.nachtraben.soundstage.network.DisplayUnlinkC2SPacket;
import com.nachtraben.soundstage.network.ScreenFitModeC2SPacket;
import dev.armadeus.armalib.platform.client.ClientNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import java.util.List;

public class DisplayScreen extends UiScreen {
    static final Theme THEME = ScreenTheme.THEME;

    private final ScreenBlockEntity screen;
    private ScreenBlockEntity.FitMode fitMode;

    public DisplayScreen(ScreenBlockEntity screen) {
        super(Component.translatable("gui.soundstage.display"), UiIcon.DISPLAY, THEME, null, 280, 198);
        this.screen = screen;
    }

    private int preferencesY() { return bodyY() + 26; }
    private int displayY() { return bodyY() + 76; }

    @Override
    protected void build() {
        Direction facing = screen.getBlockState().getValue(ScreenBlock.FACING);
        this.fitMode = readAnchorFitMode(facing);

        addRenderableWidget(new UiSwitch(THEME, right() - UiSwitch.WIDTH, preferencesY() + 13,
                Component.translatable("gui.soundstage.receiver.subtitles"), SoundstageAppearance::showSubtitles,
                () -> SoundstageAppearance.setShowSubtitles(!SoundstageAppearance.showSubtitles())));
        List<Component> sizes = List.of(
                Component.translatable("gui.soundstage.display.subtitle_size.small"),
                Component.translatable("gui.soundstage.display.subtitle_size.medium"),
                Component.translatable("gui.soundstage.display.subtitle_size.large"));
        int sizeWidth = UiSegmented.widthFor(sizes, 6);
        addRenderableWidget(new UiSegmented(THEME, right() - sizeWidth, preferencesY() + 30, sizeWidth, 15,
                Component.translatable("gui.soundstage.receiver.subtitle_size"), sizes,
                () -> SoundstageAppearance.subtitleSize().ordinal(),
                index -> SoundstageAppearance.setSubtitleSize(AppearanceSettings.SubtitleSize.values()[index])));

        List<Component> modes = List.of(Component.translatable("gui.soundstage.display.picture.fit"),
                Component.translatable("gui.soundstage.display.picture.fill"));
        int modeWidth = UiSegmented.widthFor(modes, 8);
        addRenderableWidget(new UiSegmented(THEME, right() - modeWidth, displayY() + 12, modeWidth, 15,
                Component.translatable("gui.soundstage.receiver.picture"), modes, () -> fitMode.ordinal(), index -> {
            fitMode = ScreenBlockEntity.FitMode.values()[index];
            ClientNetwork.sendToServer(new ScreenFitModeC2SPacket(screen.getBlockPos(), fitMode.ordinal()));
        }));

        int choiceWidth = Math.min(150, right() - left()
                - this.font.width(Component.translatable("gui.soundstage.display.subtitle_track")) - 10);
        addRenderableWidget(SubtitleChoiceButton.create(THEME, right() - choiceWidth, displayY() + 32, choiceWidth,
                screen::getReceiverPos, screen.getBlockPos()));

        if (screen.getReceiverPos() != null) {
            Component unlink = Component.translatable("gui.soundstage.display.unlink");
            int w = UiButton.widthFor(unlink, true, 9, 8);
            BlockPos at = screen.getBlockPos();
            addRenderableWidget(new UiButton(THEME, right() - w, displayY() + 56, w, 17, UiButton.Look.PILL, () -> {
                ClientNetwork.sendToServer(new DisplayUnlinkC2SPacket(at));
                onClose();
            }).icon(UiIcon.CLOSE).iconSize(9).label(unlink).accent(THEME.danger()));
        }
    }

    private ScreenBlockEntity.FitMode readAnchorFitMode(Direction facing) {
        net.minecraft.world.level.Level level = screen.getLevel();
        if (level == null) return ScreenBlockEntity.FitMode.FIT;
        DisplayLayout layout = ScreenBlockEntity.findDisplay(level, screen.getBlockPos(), facing);
        BlockPos anchorPos = ScreenBlockEntity.anchorPosition(screen.getBlockPos(), facing, layout);
        if (level.getBlockEntity(anchorPos) instanceof ScreenBlockEntity anchor) {
            return anchor.getFitMode();
        }
        return ScreenBlockEntity.FitMode.FIT;
    }

    @Override
    protected void drawContent(UiGraphics g, int mouseX, int mouseY, float partialTick) {
        Direction facing = screen.getBlockState().getValue(ScreenBlock.FACING);
        DisplayLayout layout = screen.getLevel() == null ? null
                : ScreenBlockEntity.findDisplay(screen.getLevel(), screen.getBlockPos(), facing);
        Component size = Component.translatable("gui.soundstage.receiver.display_size",
                layout == null ? 1 : layout.widthCells(), layout == null ? 1 : layout.heightCells());
        g.text(this.font, size, left(), bodyY(), THEME.text());
        BlockPos receiverPos = screen.getReceiverPos();
        Component link = receiverPos == null
                ? Component.translatable("gui.soundstage.display.not_linked")
                : Component.translatable("gui.soundstage.display.linked_to",
                        receiverPos.getX(), receiverPos.getY(), receiverPos.getZ());
        g.text(this.font, link, left(), bodyY() + 11, THEME.dim());

        heading(g, Component.translatable("gui.soundstage.display.your_preferences"), left(), preferencesY(), right());
        g.text(this.font, Component.translatable("gui.soundstage.receiver.subtitles"), left(), preferencesY() + 16,
                THEME.text());
        g.text(this.font, Component.translatable("gui.soundstage.receiver.subtitle_size"), left(), preferencesY() + 34,
                THEME.text());

        heading(g, Component.translatable("gui.soundstage.display.this_display"), left(), displayY(), right());
        g.text(this.font, Component.translatable("gui.soundstage.receiver.picture"), left(), displayY() + 16,
                THEME.text());
        g.text(this.font, Component.translatable("gui.soundstage.display.subtitle_track"), left(), displayY() + 36,
                THEME.text());
        if (receiverPos != null) {
            g.text(this.font, Component.translatable("gui.soundstage.receiver.unlink_hint"), left(), displayY() + 61,
                    THEME.dim());
        }
    }
}
