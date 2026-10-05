package com.nachtraben.soundstage.client.gui;

import dev.armadeus.armalib.core.ui.Theme;
import dev.armadeus.armalib.ui.UiButton;
import dev.armadeus.armalib.ui.UiDraw;
import dev.armadeus.armalib.ui.UiGraphics;
import dev.armadeus.armalib.ui.UiIcon;
import dev.armadeus.armalib.ui.UiScreen;
import dev.armadeus.armalib.ui.UiText;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class OAuthScreen extends UiScreen {
    static final Theme THEME = ScreenTheme.THEME;

    private static final int WINDOW_WIDTH = 340;
    private static final int WINDOW_HEIGHT = 180;
    private final String url;
    private final String code;
    private Component status = Component.empty();

    public OAuthScreen(String url, String code) {
        super(Component.translatable("gui.soundstage.oauth.title"), UiIcon.LOCK, THEME, null, WINDOW_WIDTH, WINDOW_HEIGHT);
        this.url = url;
        this.code = code;
    }

    @Override
    protected void build() {
        int buttonY = panelY() + WINDOW_HEIGHT - 28;
        Component open = Component.translatable("gui.soundstage.oauth.open_browser");
        int openWidth = UiButton.widthFor(open, false, 0, 10);
        addRenderableWidget(new UiButton(THEME, right() - openWidth, buttonY, openWidth, 18, UiButton.Look.PRIMARY, () -> {
            //? if >=26.3 {
            /*com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(this.url));
            *///?} else {
            Util.getPlatform().openUri(this.url);
            //?}
            status = Component.translatable("gui.soundstage.oauth.opened");
        }).label(open));
        Component copy = Component.translatable("gui.soundstage.oauth.copy_code");
        int copyWidth = UiButton.widthFor(copy, false, 0, 10);
        addRenderableWidget(new UiButton(THEME, right() - openWidth - 6 - copyWidth, buttonY, copyWidth, 18, UiButton.Look.PILL, () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(this.code);
            status = Component.translatable("gui.soundstage.oauth.copied");
        }).label(copy));
    }

    @Override
    protected void drawContent(UiGraphics g, int mouseX, int mouseY, float partialTick) {
        int y = bodyY();
        g.text(this.font, Component.translatable("gui.soundstage.oauth.instructions"), left(), y, THEME.muted());

        g.text(this.font, Component.translatable("gui.soundstage.oauth.url"), left(), y + 18, THEME.text());
        UiDraw.roundRect(g, left(), y + 29, right() - left(), 18, 4, THEME.surface());
        g.text(this.font, UiText.ellipsize(this.font, this.url, right() - left() - 12), left() + 6, y + 34, THEME.accent());

        g.text(this.font, Component.translatable("gui.soundstage.oauth.code"), left(), y + 56, THEME.text());
        UiDraw.roundRect(g, left(), y + 67, right() - left(), 26, 5, THEME.warm());
        UiDraw.roundRect(g, left() + 1, y + 68, right() - left() - 2, 24, 4, THEME.surface());
        g.pushPose();
        g.translate((left() + right()) / 2.0, y + 72, 0);
        g.scale(2f, 2f);
        g.text(this.font, this.code, -this.font.width(this.code) / 2, 0, THEME.text());
        g.popPose();

        if (!status.getString().isEmpty()) {
            g.text(this.font, status, left(), panelY() + WINDOW_HEIGHT - 23, THEME.success());
        }
    }
}
