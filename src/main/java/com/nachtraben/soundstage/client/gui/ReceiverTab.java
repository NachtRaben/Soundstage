package com.nachtraben.soundstage.client.gui;

import dev.armadeus.armalib.core.ui.Theme;
import dev.armadeus.armalib.ui.UiDraw;
import dev.armadeus.armalib.ui.UiGraphics;
import dev.armadeus.armalib.ui.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

abstract class ReceiverTab {
    static final Theme THEME = ScreenTheme.THEME;

    protected final AudioPlayerScreen screen;
    protected int x, y, width, height;

    ReceiverTab(AudioPlayerScreen screen) {
        this.screen = screen;
    }

    void init(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    void renderBackground(UiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    void renderForeground(UiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    void metadataChanged(boolean folderChanged) {
    }

    void tick() {
    }

    void leaving() {
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        return false;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    protected Font font() {
        return Minecraft.getInstance().font;
    }

    protected void heading(UiGraphics g, Component label, int left, int top, int right) {
        UiText.heading(g, font(), THEME, label, left, top, right);
    }

    protected int chip(UiGraphics g, Component label, int left, int top, int colour) {
        int w = font().width(label) + 8;
        UiDraw.roundRect(g, left, top, w, 11, 3, (colour & 0x00FFFFFF) | 0x30000000);
        g.text(font(), label, left + 4, top + 2, colour);
        return w;
    }

    protected int paragraph(UiGraphics g, Component text, int left, int top, int maxWidth, int maxLines, int colour) {
        return UiText.paragraph(g, font(), text, left, top, maxWidth, maxLines, colour);
    }

    protected static boolean inside(double mouseX, double mouseY, double left, double top, double w, double h) {
        return mouseX >= left && mouseX < left + w && mouseY >= top && mouseY < top + h;
    }
}
