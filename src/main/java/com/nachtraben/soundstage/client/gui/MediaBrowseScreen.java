package com.nachtraben.soundstage.client.gui;

import com.nachtraben.soundstage.core.video.MediaListing;
import com.nachtraben.soundstage.network.MediaBrowseReplyS2CPacket;
import com.nachtraben.soundstage.network.MediaBrowseRequestC2SPacket;
import com.nachtraben.soundstage.network.SpeakerControlC2SPacket;
import dev.armadeus.armalib.core.ui.Theme;
import dev.armadeus.armalib.platform.client.ClientNetwork;
import dev.armadeus.armalib.ui.ScrollingText;
import dev.armadeus.armalib.ui.UiDraw;
import dev.armadeus.armalib.ui.UiGraphics;
import dev.armadeus.armalib.ui.UiIcon;
import dev.armadeus.armalib.ui.UiScreen;
import dev.armadeus.armalib.ui.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

public class MediaBrowseScreen extends UiScreen {
    static final Theme THEME = ScreenTheme.THEME;

    private static final int WINDOW_WIDTH = 320;
    private static final int WINDOW_HEIGHT = 220;
    private static final int SEARCH_Y = 32;
    private static final int LIST_TOP = 58;
    private static final int ROW_HEIGHT = 13;
    private static final int COUNT_WIDTH = 84;

    private final BlockPos pos;
    private final Screen parentScreen;

    private EditBox searchField;

    private List<String> allNames = List.of();
    private List<String> filtered = List.of();
    private boolean truncated = false;
    private boolean received = false;

    private double scrollOffset = 0;
    private int maxScroll = 0;

    private final ScrollingText hoveredRowLine = new ScrollingText();

    public MediaBrowseScreen(BlockPos pos, Screen parentScreen) {
        super(Component.translatable("gui.soundstage.browse.title"), UiIcon.FOLDER, THEME, null, WINDOW_WIDTH,
                WINDOW_HEIGHT);
        this.pos = pos;
        this.parentScreen = parentScreen;
    }

    private int listTop() {
        return panelY() + LIST_TOP;
    }

    private int listBottom() {
        return panelY() + WINDOW_HEIGHT - 10;
    }

    @Override
    protected void build() {
        this.searchField = field(left(), panelY() + SEARCH_Y, WINDOW_WIDTH - 2 * PADDING - COUNT_WIDTH - 6,
                Component.translatable("gui.soundstage.browse.search"));
        this.searchField.setMaxLength(200);
        this.searchField.setHint(Component.translatable("gui.soundstage.browse.search_hint"));
        this.searchField.setResponder(query -> {
            this.filtered = MediaListing.filter(this.allNames, query);
            this.scrollOffset = 0;
        });
        this.addRenderableWidget(this.searchField);
        this.setInitialFocus(this.searchField);

        ClientNetwork.sendToServer(new MediaBrowseRequestC2SPacket(pos));
    }

    public void updateResults(MediaBrowseReplyS2CPacket packet) {
        if (!packet.pos.equals(this.pos)) return;
        this.received = true;
        this.allNames = packet.names;
        this.truncated = packet.truncated;
        this.filtered = MediaListing.filter(this.allNames, this.searchField != null ? this.searchField.getValue() : "");
        this.scrollOffset = 0;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parentScreen);
    }

    @Override
    protected void drawContent(UiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = panelX();
        int y = panelY();
        fieldBox(g, THEME, this.searchField);

        if (this.received) {
            Component hint = this.truncated
                    ? Component.translatable("gui.soundstage.browse.truncated", MediaListing.MAX_ENTRIES)
                    : Component.translatable("gui.soundstage.browse.count", this.filtered.size());
            g.text(this.font, hint, right() - this.font.width(hint), y + SEARCH_Y + 5, THEME.dim());
        }

        int listY = listTop();
        int listBottom = listBottom();
        UiDraw.roundRect(g, x + 10, listY - 2, WINDOW_WIDTH - 20, listBottom - listY + 4, 4, THEME.surface());
        g.enableScissor(x + 10, listY, x + WINDOW_WIDTH - 10, listBottom);

        boolean anyRowHovered = false;
        int rowLeft = x + 12, rowRight = x + WINDOW_WIDTH - 12;
        if (!this.received) {
            g.text(this.font, Component.translatable("gui.soundstage.browse.loading"), rowLeft + 4, listY + 3,
                    THEME.dim());
            this.maxScroll = 0;
        } else if (this.filtered.isEmpty()) {
            g.text(this.font, Component.translatable("gui.soundstage.browse.empty"), rowLeft + 4, listY + 3,
                    THEME.dim());
            this.maxScroll = 0;
        } else {
            int maxVisible = listBottom - listY;
            this.maxScroll = Math.max(0, this.filtered.size() * ROW_HEIGHT - maxVisible);
            this.scrollOffset = Mth.clamp(this.scrollOffset, 0, this.maxScroll);

            int yOffset = listY - (int) this.scrollOffset;
            for (String name : this.filtered) {
                if (yOffset + ROW_HEIGHT >= listY && yOffset <= listBottom) {
                    boolean rowHovered = mouseX >= rowLeft && mouseX < rowRight
                            && mouseY >= yOffset && mouseY < yOffset + (ROW_HEIGHT - 1)
                            && mouseY >= listY && mouseY < listBottom;
                    if (rowHovered) {
                        UiDraw.roundRect(g, rowLeft, yOffset, rowRight - rowLeft, ROW_HEIGHT - 1, 3,
                                THEME.surfaceHover());
                        anyRowHovered = true;
                        hoveredRowLine.draw(g, this.font, name, Component.literal(name),
                                rowLeft + 4, yOffset + 2, rowRight - rowLeft - 24, THEME.text());
                        UiDraw.iconCentred(g, UiIcon.PLUS, rowRight - 9, yOffset + (ROW_HEIGHT - 1) / 2.0, 8,
                                THEME.accent());
                    } else {
                        g.text(this.font, UiText.ellipsize(this.font, name, rowRight - rowLeft - 8),
                                rowLeft + 4, yOffset + 2, THEME.text());
                    }
                }
                yOffset += ROW_HEIGHT;
            }
        }
        if (!anyRowHovered) {
            hoveredRowLine.reset();
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY < listTop() || mouseY > listBottom()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        this.scrollOffset = Mth.clamp(this.scrollOffset - scrollY * ROW_HEIGHT, 0, this.maxScroll);
        return true;
    }

    //? if >=1.21.11 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        double mouseX = event.x(), mouseY = event.y();
    *///?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
    //?}
        int listY = listTop();
        int listBottom = listBottom();
        int rowLeft = panelX() + 12, rowRight = panelX() + WINDOW_WIDTH - 12;
        if (!this.filtered.isEmpty() && mouseY >= listY && mouseY < listBottom && mouseX >= rowLeft
                && mouseX < rowRight) {
            int yOffset = listY - (int) this.scrollOffset;
            for (String name : this.filtered) {
                if (mouseY >= yOffset && mouseY < yOffset + (ROW_HEIGHT - 1)) {
                    ClientNetwork.sendToServer(new SpeakerControlC2SPacket(
                            pos, SpeakerControlC2SPacket.Action.QUEUE, "media:" + name, 0));
                    Minecraft.getInstance().setScreen(parentScreen);
                    return true;
                }
                yOffset += ROW_HEIGHT;
            }
        }
        return false;
    }
}
