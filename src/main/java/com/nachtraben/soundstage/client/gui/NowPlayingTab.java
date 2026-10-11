package com.nachtraben.soundstage.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.nachtraben.soundstage.block.IAudioReceiver;
import com.nachtraben.soundstage.core.TrackTime;
import com.nachtraben.soundstage.network.QueueOpC2SPacket;
import com.nachtraben.soundstage.network.SpeakerControlC2SPacket;
import dev.armadeus.armalib.ui.ScrollingText;
import dev.armadeus.armalib.ui.UiButton;
import dev.armadeus.armalib.ui.UiDraw;
import dev.armadeus.armalib.ui.UiDropdown;
import dev.armadeus.armalib.ui.UiGraphics;
import dev.armadeus.armalib.ui.UiIcon;
import dev.armadeus.armalib.ui.UiSlider;
import dev.armadeus.armalib.ui.UiText;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

final class NowPlayingTab extends ReceiverTab {

    private static final int ART_X = 12, ART_Y = 34, ART_WIDTH = 112, ART_HEIGHT = 96;
    private static final int COLUMN_X = 138;
    private static final int TITLE_Y = 38, ARTIST_Y = 51, CHIPS_Y = 64;
    private static final int SEEK_Y = 80;
    private static final int TRANSPORT_CENTRE_Y = 110;
    private static final int VOLUME_Y = 136;
    private static final int UP_NEXT_Y = 158;
    private static final int ROWS_Y = 170;
    private static final int ROW_HEIGHT = 13;
    private static final int ROWS = 4;
    private static final int TIME_WIDTH = 30;
    private static final int AUDIO_WIDTH = 124, AUDIO_ICON = 10;

    private final ScrollingText titleLine = new ScrollingText();
    private final ScrollingText artistLine = new ScrollingText();
    private final ScrollingText hoveredRowLine = new ScrollingText();
    private UiSlider volumeSlider;
    private UiDropdown<String> audioDropdown;

    NowPlayingTab(AudioPlayerScreen screen) {
        super(screen);
    }

    private int columnRight() {
        return x + width - AudioPlayerScreen.PADDING;
    }

    @Override
    void init(int x, int y, int width, int height) {
        super.init(x, y, width, height);
        int top = screen.panelY();
        int column = x + COLUMN_X, right = columnRight();

        screen.addTabWidget(
                new SeekBar(column + TIME_WIDTH + 4, top + SEEK_Y, right - column - 2 * (TIME_WIDTH + 4), 10));

        audioDropdown = AudioChoiceButton.create(THEME, right - AUDIO_WIDTH, top + ARTIST_Y - 2, AUDIO_WIDTH, screen);
        audioDropdown.visible = screen.offersAudioChoice();
        screen.addTabWidget(audioDropdown);

        int centre = column + (right - column) / 2;
        int rowTop = top + TRANSPORT_CENTRE_Y;
        screen.addTabWidget(new UiButton(THEME, centre - 13, rowTop - 13, 26, 26, UiButton.Look.PLAY,
                () -> screen.sendControl(SpeakerControlC2SPacket.Action.PLAY_PAUSE, "", 0))
                .icon(() -> screen.playing ? UiIcon.PAUSE : UiIcon.PLAY).iconSize(13)
                .tooltip(() -> Component.translatable(screen.playing ? "gui.soundstage.player.pause"
                        : "gui.soundstage.player.play")));
        screen.addTabWidget(new UiButton(THEME, centre - 13 - 8 - 20, rowTop - 10, 20, 20, UiButton.Look.ICON,
                () -> screen.sendControl(SpeakerControlC2SPacket.Action.STOP, "", 0))
                .icon(UiIcon.STOP).iconSize(12).tooltip(Component.translatable("gui.soundstage.player.stop")));
        screen.addTabWidget(new UiButton(THEME, centre + 13 + 8, rowTop - 10, 20, 20, UiButton.Look.ICON,
                () -> screen.sendControl(SpeakerControlC2SPacket.Action.SKIP, "", 0))
                .icon(UiIcon.NEXT).iconSize(12).tooltip(Component.translatable("gui.soundstage.player.skip")));
        screen.addTabWidget(screen.shuffleButton(centre - 13 - 8 - 20 - 10 - 20, rowTop - 10, 20));
        screen.addTabWidget(screen.loopButton(centre + 13 + 8 + 20 + 10, rowTop - 10, 20));

        int sliderLeft = x + 30, sliderRight = right - 36;
        volumeSlider = new UiSlider(THEME, sliderLeft, top + VOLUME_Y, sliderRight - sliderLeft, 12, screen.volume,
                IAudioReceiver.MIN_VOLUME, IAudioReceiver.MAX_VOLUME, 1.0,
                (double) (IAudioReceiver.LAVAPLAYER_MAX_VOLUME - IAudioReceiver.MIN_VOLUME)
                        / (IAudioReceiver.MAX_VOLUME - IAudioReceiver.MIN_VOLUME),
                UiSlider.Kind.BOOST,
                value -> Component.translatable("gui.soundstage.player.volume", value.intValue()),
                value -> {
                    int newVolume = value.intValue();
                    if (newVolume != screen.volume) {
                        screen.volume = newVolume;
                        screen.sendControl(SpeakerControlC2SPacket.Action.SET_VOLUME, "", newVolume);
                    }
                });
        screen.addTabWidget(volumeSlider);

        Component viewQueue = viewQueueLabel();
        int linkWidth = UiButton.widthFor(viewQueue, true, 8, 2);
        screen.addTabWidget(
                new UiButton(THEME, right - linkWidth, top + UP_NEXT_Y - 2, linkWidth, 12, UiButton.Look.LINK,
                () -> screen.showTab(AudioPlayerScreen.Tab.QUEUE))
                .label(this::viewQueueLabel).icon(UiIcon.CHEVRON_RIGHT).iconSize(8).iconAfter());
    }

    private Component viewQueueLabel() {
        return Component.translatable("gui.soundstage.receiver.view_queue", screen.queue.size());
    }

    private int[] titleBox() {
        int top = screen.panelY() + TITLE_Y;
        return new int[]{x + COLUMN_X, top - 1, columnRight(), top + font().lineHeight + 1};
    }

    private int[] artistBox() {
        int top = screen.panelY() + ARTIST_Y;
        int right = screen.offersAudioChoice() ? columnRight() - AUDIO_WIDTH - AUDIO_ICON - 8 : columnRight();
        return new int[]{x + COLUMN_X, top - 1, right, top + font().lineHeight + 1};
    }

    private int[] rowTextBox(int index, boolean hovered) {
        int top = screen.panelY() + ROWS_Y + index * ROW_HEIGHT;
        int right = columnRight() - (hovered ? 18 : 4);
        return new int[]{x + 30, top + 1, right, top + 2 + font().lineHeight};
    }

    @Override
    void renderBackground(UiGraphics g, int mouseX, int mouseY, float partialTick) {
        int top = screen.panelY();
        var font = font();
        screen.drawArtwork(g, x + ART_X, top + ART_Y, ART_WIDTH, ART_HEIGHT, 4, THEME.panel());

        String titleText = screen.hasTrack() ? screen.trackTitle
                : Component.translatable("gui.soundstage.player.no_track").getString();
        int[] title = titleBox();
        titleLine.draw(g, font, titleText, Component.literal(titleText).withStyle(Style.EMPTY.withBold(true)),
                title[0], title[1] + 1, title[2] - title[0], screen.hasTrack() ? THEME.text() : THEME.muted());
        boolean audio = screen.offersAudioChoice();
        if (audioDropdown != null) audioDropdown.visible = audio;
        if (audio) {
            UiDraw.icon(g, UiIcon.VOLUME, columnRight() - AUDIO_WIDTH - AUDIO_ICON - 3, top + ARTIST_Y,
                    AUDIO_ICON, THEME.muted());
        }
        int[] artist = artistBox();
        artistLine.draw(g, font, screen.artist, Component.literal(screen.artist), artist[0], artist[1] + 1,
                artist[2] - artist[0], THEME.muted());

        int chipX = x + COLUMN_X;
        int chipY = top + CHIPS_Y;
        if (screen.isLive() && screen.playing) {
            chipX += chip(g, Component.translatable("gui.soundstage.receiver.state.live"), chipX, chipY,
                    THEME.danger()) + 4;
        } else if (screen.playing) {
            chipX += chip(g, Component.translatable("gui.soundstage.receiver.state.playing"), chipX, chipY,
                    THEME.success()) + 4;
        } else if (screen.hasTrack()) {
            chipX += chip(g, Component.translatable("gui.soundstage.receiver.state.paused"), chipX, chipY,
                    THEME.warm()) + 4;
        } else if (screen.synced) {
            chipX += chip(g, Component.translatable("gui.soundstage.receiver.state.idle"), chipX, chipY,
                    THEME.muted()) + 4;
        }
        if (screen.levelling) {
            chipX += chip(g, Component.translatable("gui.soundstage.receiver.state.levelling"), chipX, chipY,
                    THEME.accent()) + 4;
        }
        if (screen.isReceiver() && !screen.pictureEnabled) {
            chip(g, Component.translatable("gui.soundstage.receiver.state.sound_only"), chipX, chipY, THEME.muted());
        }

        int right = columnRight();
        int seekTextY = top + SEEK_Y + 1;
        if (screen.trackDuration > 0) {
            String elapsed = TrackTime.format(screen.position(), screen.trackDuration);
            String total = TrackTime.format(screen.trackDuration, screen.trackDuration);
            g.text(font, elapsed, x + COLUMN_X, seekTextY, THEME.muted());
            g.text(font, total, right - font.width(total), seekTextY, THEME.muted());
        } else if (screen.hasTrack()) {
            String elapsed = TrackTime.format(screen.position());
            g.text(font, elapsed, x + COLUMN_X, seekTextY, THEME.muted());
        }

        if (volumeSlider != null && !volumeSlider.isHoveredOrFocused()
                && (int) volumeSlider.actualValue() != screen.volume) {
            volumeSlider.setActualValue(screen.volume);
        }
        UiDraw.icon(g, screen.volume == 0 ? UiIcon.VOLUME_OFF : UiIcon.VOLUME, x + AudioPlayerScreen.PADDING,
                top + VOLUME_Y, 12, THEME.muted());
        String percent = screen.volume + "%";
        g.text(font, percent, right - font.width(percent), top + VOLUME_Y + 2,
                screen.volume > IAudioReceiver.LAVAPLAYER_MAX_VOLUME ? THEME.warm() : THEME.text());

        Component viewQueue = viewQueueLabel();
        heading(g, Component.translatable("gui.soundstage.player.up_next"), x + AudioPlayerScreen.PADDING,
                top + UP_NEXT_Y, right - UiButton.widthFor(viewQueue, true, 8, 2) - 8);
        boolean anyHovered = false;
        if (screen.queue.isEmpty()) {
            g.text(font, Component.translatable("gui.soundstage.receiver.queue_empty_hint"),
                    x + AudioPlayerScreen.PADDING, top + ROWS_Y + 2, THEME.dim());
        }
        for (int i = 0; i < Math.min(ROWS, screen.queue.size()); i++) {
            int rowTop = top + ROWS_Y + i * ROW_HEIGHT;
            boolean hovered = inside(mouseX, mouseY, x + 10, rowTop, width - 20, ROW_HEIGHT - 1);
            boolean playable = i < screen.queueIds.size();
            if (hovered) UiDraw.roundRect(g, x + 10, rowTop, width - 20, ROW_HEIGHT - 1, 3, THEME.surfaceHover());
            String number = Integer.toString(i + 1);
            g.text(font, number, x + 24 - font.width(number), rowTop + 2, THEME.dim());
            String entry = screen.queue.get(i);
            int[] box = rowTextBox(i, hovered && playable);
            if (hovered) {
                anyHovered = true;
                hoveredRowLine.draw(g, font, entry, Component.literal(entry), box[0], box[1] + 1, box[2] - box[0],
                        THEME.text());
                if (playable) UiDraw.iconCentred(g, UiIcon.PLAY, right - 8, rowTop + 6, 9, THEME.accent());
            } else {
                g.text(font, UiText.ellipsize(font, entry, box[2] - box[0]), box[0], box[1] + 1, THEME.text());
            }
        }
        if (!anyHovered) hoveredRowLine.reset();
    }

    @Override
    void renderForeground(UiGraphics g, int mouseX, int mouseY, float partialTick) {
        int top = screen.panelY();
        for (int i = 0; i < Math.min(ROWS, screen.queue.size()); i++) {
            int rowTop = top + ROWS_Y + i * ROW_HEIGHT;
            if (inside(mouseX, mouseY, x + 10, rowTop, width - 20, ROW_HEIGHT - 1) && i < screen.queueIds.size()) {
                screen.tooltip(g, Component.translatable("gui.soundstage.receiver.play_now"), mouseX, mouseY);
            }
        }
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        int top = screen.panelY();
        for (int i = 0; i < Math.min(ROWS, screen.queue.size()); i++) {
            int rowTop = top + ROWS_Y + i * ROW_HEIGHT;
            if (inside(mouseX, mouseY, x + 10, rowTop, width - 20, ROW_HEIGHT - 1)) {
                if (i < screen.queueIds.size()) {
                    screen.sendQueueOp(QueueOpC2SPacket.Action.PLAY_NOW, screen.queueIds.get(i), 0);
                }
                return true;
            }
        }
        return false;
    }

    private final class SeekBar extends AbstractWidget {
        private double dragged = -1;

        SeekBar(int x, int y, int width, int height) {
            super(x, y, width, height, Component.translatable("gui.soundstage.player.seek"));
        }

        private double shown() {
            if (dragged >= 0) return dragged;
            return screen.trackDuration > 0 ? screen.position() / (double) screen.trackDuration : 0;
        }

        @Override
        //? if >=26.1 {
        /*protected void extractWidgetRenderState(net.minecraft.client.gui.GuiGraphicsExtractor raw, int mouseX,
                int mouseY, float partialTick) {
        *///?} else {
        protected void renderWidget(net.minecraft.client.gui.GuiGraphics raw, int mouseX, int mouseY,
                float partialTick) {
        //?}
            UiGraphics g = UiGraphics.of(raw);
            active = screen.trackDuration > 0;
            double centre = getY() + getHeight() / 2.0;
            double left = getX() + 3, right = getX() + getWidth() - 3;
            if (!active) {
                UiDraw.roundRect(g, left - 1.5, centre - 1.5, right - left + 3, 3, 1.5, THEME.surface());
                setMessage(Component.translatable("gui.soundstage.player.seek_unavailable"));
                return;
            }
            double at = left + (right - left) * shown();
            UiDraw.roundRect(g, left - 1.5, centre - 1.5, right - left + 3, 3, 1.5, THEME.border());
            UiDraw.roundRect(g, left - 1.5, centre - 1.5, at - left + 3, 3, 1.5, THEME.warm());
            if (isHoveredOrFocused() || dragged >= 0) UiDraw.circle(g, at, centre, 4, 0xFFFFFFFF);
            long millis = (long) (shown() * screen.trackDuration);
            setMessage(Component.translatable("gui.soundstage.player.seek_value",
                    TrackTime.format(millis, screen.trackDuration)));
            if (isHovered() && dragged < 0) {
                double hover = Math.max(0, Math.min(1, (mouseX - left) / (right - left)));
                screen.tooltip(g, Component.literal(TrackTime.format(
                        (long) (hover * screen.trackDuration), screen.trackDuration)), mouseX, mouseY);
            }
        }

        private double fraction(double mouseX) {
            double left = getX() + 3, right = getX() + getWidth() - 3;
            return Math.max(0, Math.min(1, (mouseX - left) / (right - left)));
        }

        //? if >=1.21.11 {
        /*@Override
        public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
            if (screen.trackDuration > 0) dragged = fraction(event.x());
        }

        @Override
        protected void onDrag(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
            if (dragged >= 0) dragged = fraction(event.x());
        }

        @Override
        public void onRelease(net.minecraft.client.input.MouseButtonEvent event) {
        *///?} else {
        @Override
        public void onClick(double mouseX, double mouseY) {
            if (screen.trackDuration > 0) dragged = fraction(mouseX);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            if (dragged >= 0) dragged = fraction(mouseX);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
        //?}
            if (dragged >= 0) {
                screen.sendSeek((long) (dragged * screen.trackDuration));
                dragged = -1;
            }
        }

        //? if >=1.21.11 {
        /*@Override
        public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
            int keyCode = event.key();
            if (screen.trackDuration <= 0 || !isFocused()
                    || (keyCode != com.mojang.blaze3d.platform.InputConstants.KEY_LEFT
                    && keyCode != com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT)) {
                return super.keyPressed(event);
            }
        *///?} else {
        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (screen.trackDuration <= 0 || !isFocused() || (keyCode != InputConstants.KEY_LEFT
                    && keyCode != InputConstants.KEY_RIGHT)) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        //?}
            long step = keyCode == InputConstants.KEY_RIGHT ? 5_000 : -5_000;
            screen.sendSeek(Math.max(0, Math.min(screen.trackDuration, screen.position() + step)));
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
