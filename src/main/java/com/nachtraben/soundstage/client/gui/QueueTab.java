package com.nachtraben.soundstage.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.nachtraben.soundstage.network.QueueOpC2SPacket;
import com.nachtraben.soundstage.network.SpeakerControlC2SPacket;
import dev.armadeus.armalib.ui.ScrollingText;
import dev.armadeus.armalib.ui.UiButton;
import dev.armadeus.armalib.ui.UiDraw;
import dev.armadeus.armalib.ui.UiGraphics;
import dev.armadeus.armalib.ui.UiIcon;
import dev.armadeus.armalib.ui.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class QueueTab extends ReceiverTab {

    private static final int ADD_Y = 32, ADD_HEIGHT = 18;
    private static final int LIST_HEADING_Y = 58;
    private static final int LIST_TOP = 72;
    private static final int ROW_HEIGHT = 15;
    private static final int ACTION_WIDTH = 16;
    private static final long CONFIRM_MILLIS = 3000;

    private EditBox sourceField;
    private double scroll;
    private int maxScroll;
    private long clearArmedAt;
    private final ScrollingText hoveredRowLine = new ScrollingText();

    QueueTab(AudioPlayerScreen screen) {
        super(screen);
    }

    private int listBottom() {
        return y + height - 6;
    }

    private int listTop() {
        return screen.panelY() + LIST_TOP;
    }

    private int right() {
        return x + width - AudioPlayerScreen.PADDING;
    }

    @Override
    void init(int x, int y, int width, int height) {
        super.init(x, y, width, height);
        int top = screen.panelY();
        int right = right();
        var font = font();

        int buttonRight = right;
        if (screen.mediaFolderEnabled) {
            Component browse = Component.translatable("gui.soundstage.player.browse");
            int w = UiButton.widthFor(browse, true, 10, 7);
            screen.addTabWidget(new UiButton(THEME, buttonRight - w, top + ADD_Y, w, ADD_HEIGHT, UiButton.Look.PILL,
                    () -> Minecraft.getInstance().setScreen(new MediaBrowseScreen(screen.pos, screen)))
                    .icon(UiIcon.FOLDER).iconSize(10).label(browse));
            buttonRight -= w + 4;
        }
        Component add = Component.translatable("gui.soundstage.player.add");
        int addWidth = UiButton.widthFor(add, true, 9, 8);
        screen.addTabWidget(
                new UiButton(THEME, buttonRight - addWidth, top + ADD_Y, addWidth, ADD_HEIGHT, UiButton.Look.PRIMARY,
                this::queueTyped).icon(UiIcon.PLUS).iconSize(9).label(add)
                .enabled(() -> sourceField != null && !sourceField.getValue().isBlank()));
        int fieldLeft = x + AudioPlayerScreen.PADDING;
        int fieldRight = buttonRight - addWidth - 4;
        sourceField = new EditBox(font, fieldLeft + 6, top + ADD_Y + 5, fieldRight - fieldLeft - 12, 10,
                Component.translatable("gui.soundstage.player.source"));
        sourceField.setBordered(false);
        sourceField.setMaxLength(512);
        sourceField.setTextColor(THEME.text());
        sourceField.setHint(Component.translatable("gui.soundstage.player.source_hint"));
        sourceField.setValue(screen.draftSource);
        sourceField.setResponder(value -> screen.draftSource = value);
        screen.addTabWidget(sourceField);

        int clearWidth =
                UiButton.widthFor(Component.translatable("gui.soundstage.receiver.clear_confirm"), false, 0, 3);
        screen.addTabWidget(
                new UiButton(THEME, right - clearWidth, top + LIST_HEADING_Y - 3, clearWidth, 14, UiButton.Look.LINK,
                this::clearPressed).label(this::clearLabel).accent(THEME.danger())
                .enabled(() -> !screen.queue.isEmpty()));
        screen.addTabWidget(screen.loopButton(right - clearWidth - 4 - 16, top + LIST_HEADING_Y - 4, 16));
        screen.addTabWidget(screen.shuffleButton(right - clearWidth - 4 - 16 - 2 - 16, top + LIST_HEADING_Y - 4, 16));
    }

    private Component clearLabel() {
        return Component.translatable(armed() ? "gui.soundstage.receiver.clear_confirm"
                : "gui.soundstage.receiver.clear");
    }

    private boolean armed() {
        return System.currentTimeMillis() - clearArmedAt < CONFIRM_MILLIS;
    }

    private void clearPressed() {
        if (armed()) {
            clearArmedAt = 0;
            screen.sendQueueOp(QueueOpC2SPacket.Action.CLEAR, 0, 0);
        } else {
            clearArmedAt = System.currentTimeMillis();
        }
    }

    private void queueTyped() {
        String source = sourceField.getValue();
        if (source.isBlank()) return;
        screen.sendControl(SpeakerControlC2SPacket.Action.QUEUE, source.trim(), 0);
        sourceField.setValue("");
    }

    @Override
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (sourceField != null && sourceField.isFocused()
                && (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)) {
            queueTyped();
            return true;
        }
        return false;
    }

    @Override
    void metadataChanged(boolean folderChanged) {
        if (folderChanged) screen.rebuild();
    }

    @Override
    void renderBackground(UiGraphics g, int mouseX, int mouseY, float partialTick) {
        int top = screen.panelY();
        var font = font();
        int right = right();

        if (sourceField != null) {
            int left = x + AudioPlayerScreen.PADDING;
            int boxRight = sourceField.getX() + sourceField.getWidth() + 6;
            UiDraw.roundRect(g, left, top + ADD_Y, boxRight - left, ADD_HEIGHT, 4,
                    sourceField.isFocused() ? THEME.accent() : THEME.border());
            UiDraw.roundRect(g, left + 1, top + ADD_Y + 1, boxRight - left - 2, ADD_HEIGHT - 2, 3, THEME.surface());
        }

        Component count = Component.translatable("gui.soundstage.receiver.track_count", screen.queue.size());
        Component heading = Component.translatable("gui.soundstage.player.up_next");
        int headingLeft = x + AudioPlayerScreen.PADDING;
        g.text(font, heading, headingLeft, top + LIST_HEADING_Y, THEME.warm());
        g.text(font, count, headingLeft + font.width(heading) + 6, top + LIST_HEADING_Y, THEME.dim());

        int listTop = listTop(), listBottom = listBottom();
        maxScroll = Math.max(0, screen.queue.size() * ROW_HEIGHT - (listBottom - listTop));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        g.enableScissor(x + 8, listTop, x + width - 8, listBottom);
        boolean anyHovered = false;
        if (screen.queue.isEmpty()) {
            g.text(font, Component.translatable("gui.soundstage.player.queue_empty"), headingLeft, listTop + 3,
                    THEME.dim());
        }
        for (int i = 0; i < screen.queue.size(); i++) {
            int rowTop = listTop - (int) scroll + i * ROW_HEIGHT;
            if (rowTop + ROW_HEIGHT < listTop || rowTop > listBottom) continue;
            boolean hovered = inside(mouseX, mouseY, x + 10, rowTop, width - 20, ROW_HEIGHT - 1)
                    && mouseY >= listTop && mouseY < listBottom;
            boolean controllable = i < screen.queueIds.size();
            if (hovered) UiDraw.roundRect(g, x + 10, rowTop, width - 20, ROW_HEIGHT - 1, 3, THEME.surfaceHover());
            String number = Integer.toString(i + 1);
            g.text(font, number, x + 30 - font.width(number), rowTop + 4, THEME.dim());
            String entry = screen.queue.get(i);
            int textLeft = x + 36;
            int textRight = hovered && controllable ? actionX(3) - 4 : right - 2;
            if (hovered) {
                anyHovered = true;
                hoveredRowLine.draw(g, font, entry, Component.literal(entry), textLeft, rowTop + 4,
                        textRight - textLeft, THEME.text());
            } else {
                g.text(font, UiText.ellipsize(font, entry, textRight - textLeft), textLeft, rowTop + 4, THEME.text());
            }
            if (hovered && controllable) {
                drawAction(g, UiIcon.PLAY, 3, rowTop, mouseX, mouseY, true, THEME.accent());
                drawAction(g, UiIcon.UP, 2, rowTop, mouseX, mouseY, i > 0, THEME.text());
                drawAction(g, UiIcon.DOWN, 1, rowTop, mouseX, mouseY, i < screen.queue.size() - 1, THEME.text());
                drawAction(g, UiIcon.CLOSE, 0, rowTop, mouseX, mouseY, true, THEME.danger());
            }
        }
        g.disableScissor();
        if (!anyHovered) hoveredRowLine.reset();

        if (maxScroll > 0) {
            int track = listBottom - listTop;
            double thumb = Math.max(12, track * track / (double) (track + maxScroll));
            double thumbTop = listTop + (track - thumb) * (scroll / maxScroll);
            UiDraw.roundRect(g, x + width - 7, thumbTop, 2.5, thumb, 1.25, THEME.border());
        }
    }

    private int actionX(int slot) {
        return right() - (slot + 1) * ACTION_WIDTH;
    }

    private void drawAction(UiGraphics g, UiIcon icon, int slot, int rowTop, int mouseX, int mouseY,
                            boolean enabled, int hotColour) {
        int left = actionX(slot);
        boolean hot = enabled && inside(mouseX, mouseY, left, rowTop, ACTION_WIDTH, ROW_HEIGHT - 1);
        if (hot) UiDraw.roundRect(g, left + 1, rowTop + 1, ACTION_WIDTH - 2, ROW_HEIGHT - 3, 2.5, 0x33FFFFFF);
        UiDraw.iconCentred(g, icon, left + ACTION_WIDTH / 2.0, rowTop + (ROW_HEIGHT - 1) / 2.0, 9,
                !enabled ? THEME.dim() : hot ? hotColour : THEME.muted());
    }

    @Override
    void renderForeground(UiGraphics g, int mouseX, int mouseY, float partialTick) {
        int row = rowAt(mouseX, mouseY);
        if (row < 0 || row >= screen.queueIds.size()) return;
        int rowTop = listTop() - (int) scroll + row * ROW_HEIGHT;
        String key = null;
        for (int slot = 0; slot < 4; slot++) {
            if (inside(mouseX, mouseY, actionX(slot), rowTop, ACTION_WIDTH, ROW_HEIGHT - 1)) {
                key = switch (slot) {
                    case 0 -> "gui.soundstage.receiver.remove";
                    case 1 -> "gui.soundstage.receiver.move_down";
                    case 2 -> "gui.soundstage.receiver.move_up";
                    default -> "gui.soundstage.receiver.play_now";
                };
            }
        }
        if (key != null) screen.tooltip(g, Component.translatable(key), mouseX, mouseY);
    }

    private int rowAt(double mouseX, double mouseY) {
        int listTop = listTop();
        if (mouseY < listTop || mouseY >= listBottom() || mouseX < x + 10 || mouseX >= x + width - 10) return -1;
        int row = (int) ((mouseY - listTop + scroll) / ROW_HEIGHT);
        return row >= 0 && row < screen.queue.size() ? row : -1;
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = rowAt(mouseX, mouseY);
        if (row < 0) return false;
        if (row >= screen.queueIds.size()) return true;
        long id = screen.queueIds.get(row);
        int rowTop = listTop() - (int) scroll + row * ROW_HEIGHT;
        if (inside(mouseX, mouseY, actionX(0), rowTop, ACTION_WIDTH, ROW_HEIGHT)) {
            screen.sendQueueOp(QueueOpC2SPacket.Action.REMOVE, id, 0);
        } else if (inside(mouseX, mouseY, actionX(1), rowTop, ACTION_WIDTH, ROW_HEIGHT)) {
            if (row < screen.queue.size() - 1) screen.sendQueueOp(QueueOpC2SPacket.Action.MOVE, id, row + 1);
        } else if (inside(mouseX, mouseY, actionX(2), rowTop, ACTION_WIDTH, ROW_HEIGHT)) {
            if (row > 0) screen.sendQueueOp(QueueOpC2SPacket.Action.MOVE, id, row - 1);
        } else {
            screen.sendQueueOp(QueueOpC2SPacket.Action.PLAY_NOW, id, 0);
        }
        return true;
    }

    @Override
    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (mouseY < listTop() || mouseY > listBottom()) return false;
        scroll = Math.max(0, Math.min(maxScroll, scroll - scrollY * ROW_HEIGHT));
        return true;
    }
}
