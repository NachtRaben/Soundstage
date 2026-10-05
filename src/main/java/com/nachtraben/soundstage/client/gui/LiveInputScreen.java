package com.nachtraben.soundstage.client.gui;

import dev.armadeus.armalib.core.ui.Theme;
import dev.armadeus.armalib.ui.ScrollingText;
import dev.armadeus.armalib.ui.UiButton;
import dev.armadeus.armalib.ui.UiDraw;
import dev.armadeus.armalib.ui.UiGraphics;
import dev.armadeus.armalib.ui.UiIcon;
import dev.armadeus.armalib.ui.UiScreen;
import dev.armadeus.armalib.ui.UiText;
import com.nachtraben.soundstage.client.audio.LinuxShareOutput;
import com.nachtraben.soundstage.client.audio.LiveInputCapture;
import com.nachtraben.soundstage.client.audio.LiveInputClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LiveInputScreen extends UiScreen {
    static final Theme THEME = ScreenTheme.THEME;

    private static final int WINDOW_WIDTH = 300;
    private static final int WINDOW_HEIGHT = 232;
    private static final int LIST_TOP = 44;
    private static final int LIST_BOTTOM = 110;
    private static final int ROW_HEIGHT = 13;
    private static final float METER_FLOOR_DB = -60f;
    private static final long OUTPUT_STATUS_MILLIS = 8000;

    private final BlockPos receiverPos;
    private final Screen parent;
    private final List<String> devices = new ArrayList<>();
    private final List<String> present = new ArrayList<>();
    private int refreshTicks;
    private final ScrollingText statusLine = new ScrollingText();
    private final ScrollingText hoveredDeviceLine = new ScrollingText();
    private double scrollOffset;
    private int maxScroll;
    private UiButton shareButton;
    private UiButton outputButton;
    private java.util.concurrent.CompletableFuture<LinuxShareOutput.Result> outputPending;
    private int awaitingMonitorTicks;
    private Component outputStatus;
    private int outputStatusColour;
    private long outputStatusMillis;
    private long lastSoundMillis;

    public LiveInputScreen(BlockPos receiverPos, Screen parent) {
        super(Component.translatable("gui.soundstage.share.title"), UiIcon.SHARE, THEME, null, WINDOW_WIDTH, WINDOW_HEIGHT);
        this.receiverPos = receiverPos;
        this.parent = parent;
    }

    @Override
    protected void build() {
        int y = panelY();
        boolean linux = LinuxShareOutput.applies();
        int buttonY = y + WINDOW_HEIGHT - 26;
        int left = left();
        Component refresh = Component.translatable("gui.soundstage.share.refresh");
        int refreshWidth = UiButton.widthFor(refresh, false, 0, 8);
        this.addRenderableWidget(new UiButton(THEME, left, buttonY, refreshWidth, 17, UiButton.Look.PILL, () -> {
            loadDevices();
            LiveInputClient.retry();
        }).label(refresh));
        left += refreshWidth + 4;
        if (linux) {
            int outputWidth = UiButton.widthFor(Component.translatable("gui.soundstage.share.output_create"), false, 0, 8);
            outputButton = new UiButton(THEME, left, buttonY, outputWidth, 17, UiButton.Look.PILL, () -> {
                outputPending = LinuxShareOutput.create();
                showOutputStatus(Component.translatable("gui.soundstage.share.output_creating"), THEME.muted());
            }).label(this::outputLabel).enabled(() -> outputPending == null && awaitingMonitorTicks == 0 && !outputListed());
            this.addRenderableWidget(outputButton);
        }
        int shareWidth = Math.max(UiButton.widthFor(Component.translatable("gui.soundstage.share.start"), true, 10, 8),
                UiButton.widthFor(Component.translatable("gui.soundstage.share.stop"), true, 10, 8));
        shareButton = new UiButton(THEME, right() - shareWidth, buttonY, shareWidth, 17, UiButton.Look.PRIMARY, () -> {
            if (LiveInputClient.isSharingTo(receiverPos)) {
                LiveInputClient.requestStop();
            } else if (LiveInputClient.state() != LiveInputClient.State.STARTING) {
                LiveInputClient.requestStart(receiverPos);
            }
        }).icon(UiIcon.SHARE).iconSize(10).label(this::shareLabel)
                .enabled(() -> LiveInputClient.state() != LiveInputClient.State.STARTING
                        && (LiveInputClient.isSharingTo(receiverPos) || available(LiveInputClient.device())));
        this.addRenderableWidget(shareButton);
        loadDevices();
        int chosenRow = devices.indexOf(LiveInputClient.device());
        if (chosenRow >= 0) {
            int visibleRows = (LIST_BOTTOM - LIST_TOP) / ROW_HEIGHT;
            scrollOffset = Math.min(maxScroll, Math.max(0, (chosenRow - visibleRows + 1) * ROW_HEIGHT));
        }
        lastSoundMillis = System.currentTimeMillis();
    }

    private void loadDevices() {
        present.clear();
        try {
            present.addAll(LiveInputCapture.devices());
        } catch (RuntimeException e) {
        }
        devices.clear();
        devices.add(null);
        devices.addAll(present);
        String chosen = LiveInputClient.device();
        if (chosen != null && !present.contains(chosen)) {
            devices.add(chosen);
        }
        maxScroll = Math.max(0, devices.size() * ROW_HEIGHT - (LIST_BOTTOM - LIST_TOP));
        scrollOffset = Math.min(scrollOffset, maxScroll);
        LiveInputClient.setPreviewing(available(chosen));
    }

    private boolean available(String device) {
        return device == null || present.contains(device);
    }

    private Component shareLabel() {
        if (LiveInputClient.isSharingTo(receiverPos)) return Component.translatable("gui.soundstage.share.stop");
        if (LiveInputClient.state() == LiveInputClient.State.STARTING) return Component.translatable("gui.soundstage.share.waiting");
        return Component.translatable("gui.soundstage.share.start");
    }

    private boolean outputListed() {
        return present.contains(LinuxShareOutput.monitorName());
    }

    private Component outputLabel() {
        if (outputPending != null || awaitingMonitorTicks > 0) return Component.translatable("gui.soundstage.share.output_working");
        return Component.translatable(outputListed() ? "gui.soundstage.share.output_listed" : "gui.soundstage.share.output_create");
    }

    private void showOutputStatus(Component status, int colour) {
        outputStatus = status;
        outputStatusColour = colour;
        outputStatusMillis = System.currentTimeMillis();
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTicks % 20 == 0 && outputPending == null && awaitingMonitorTicks == 0) {
            loadDevices();
        }
        if (outputPending != null && outputPending.isDone()) {
            LinuxShareOutput.Result result = outputPending.getNow(LinuxShareOutput.Result.FAILED);
            outputPending = null;
            if (result == LinuxShareOutput.Result.CREATED || result == LinuxShareOutput.Result.ALREADY_THERE) {
                awaitingMonitorTicks = 60;
            } else {
                Minecraft.getInstance().keyboardHandler.setClipboard(LinuxShareOutput.COMMAND);
                showOutputStatus(Component.translatable("gui.soundstage.share.output_manual"), THEME.warm());
            }
        }
        if (awaitingMonitorTicks > 0) {
            if (awaitingMonitorTicks % 5 == 0) loadDevices();
            if (outputListed()) {
                awaitingMonitorTicks = 0;
                LiveInputClient.selectDevice(LinuxShareOutput.monitorName());
                showOutputStatus(Component.translatable("gui.soundstage.share.output_ready"), THEME.success());
            } else if (--awaitingMonitorTicks == 0) {
                showOutputStatus(Component.translatable("gui.soundstage.share.output_unlisted"), THEME.warm());
            }
        }
    }

    @Override
    protected void drawContent(UiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = panelX();
        int y = panelY();
        heading(g, Component.translatable("gui.soundstage.share.devices"), left(), y + 31, right());

        UiDraw.roundRect(g, x + 10, y + LIST_TOP - 2, WINDOW_WIDTH - 20, LIST_BOTTOM - LIST_TOP + 4, 4, THEME.surface());
        String chosen = LiveInputClient.device();
        g.enableScissor(x + 10, y + LIST_TOP, x + WINDOW_WIDTH - 10, y + LIST_BOTTOM);
        int rowY = y + LIST_TOP - (int) scrollOffset;
        boolean anyHovered = false;
        for (String device : devices) {
            boolean selected = java.util.Objects.equals(device, chosen);
            boolean hovered = mouseX >= x + 10 && mouseX < x + WINDOW_WIDTH - 10
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 1
                    && mouseY >= y + LIST_TOP && mouseY < y + LIST_BOTTOM;
            if (selected || hovered) {
                UiDraw.roundRect(g, x + 12, rowY, WINDOW_WIDTH - 24, ROW_HEIGHT - 1, 3,
                        selected ? THEME.surfaceRaised() : THEME.surfaceHover());
            }
            if (selected) UiDraw.roundRect(g, x + 12, rowY + 2, 2, ROW_HEIGHT - 5, 1, THEME.accent());
            String label = device == null ? Component.translatable("gui.soundstage.share.default_device").getString()
                    : available(device) ? device
                    : Component.translatable("gui.soundstage.share.device_missing", device).getString();
            int colour = !available(device) ? THEME.dim() : selected ? THEME.text() : THEME.muted();
            if (hovered) {
                anyHovered = true;
                hoveredDeviceLine.draw(g, this.font, label, Component.literal(label), x + 16, rowY + 2,
                        WINDOW_WIDTH - 36, colour);
            } else {
                g.text(this.font, UiText.ellipsize(this.font, label, WINDOW_WIDTH - 36),
                        x + 18, rowY + 2, colour);
            }
            rowY += ROW_HEIGHT;
        }
        if (!anyHovered) hoveredDeviceLine.reset();
        g.disableScissor();

        float level = LiveInputClient.level();
        float db = level <= 0 ? METER_FLOOR_DB : Math.max(METER_FLOOR_DB, (float) (20 * Math.log10(level)));
        if (db > -50f) lastSoundMillis = System.currentTimeMillis();
        int meterX = x + 50;
        int meterRight = x + WINDOW_WIDTH - 12;
        int meterY = y + 118;
        g.text(this.font, Component.translatable("gui.soundstage.share.level"), x + 12, meterY - 1, THEME.muted());
        UiDraw.roundRect(g, meterX, meterY, meterRight - meterX, 6, 3, THEME.surface());
        double filled = (meterRight - meterX) * (db - METER_FLOOR_DB) / -METER_FLOOR_DB;
        if (filled > 1) {
            int colour = db > -1f ? THEME.danger() : db > -6f ? THEME.warm() : THEME.success();
            UiDraw.roundRect(g, meterX, meterY, filled, 6, 3, colour);
        }

        Component status = null;
        int statusColour = THEME.muted();
        if (LiveInputClient.lastFailure() != null) {
            status = LiveInputClient.lastFailure();
            statusColour = 0xFFFF5555;
        } else if (outputStatus != null && (outputPending != null || awaitingMonitorTicks > 0
                || System.currentTimeMillis() - outputStatusMillis < OUTPUT_STATUS_MILLIS)) {
            status = outputStatus;
            statusColour = outputStatusColour;
        } else if (!available(chosen) && !LiveInputClient.isSharingTo(receiverPos)) {
            status = Component.translatable("gui.soundstage.share.chosen_missing", chosen);
            statusColour = THEME.warm();
        } else if (LiveInputClient.isSharingTo(receiverPos)) {
            status = Component.translatable("gui.soundstage.share.sharing_here");
            statusColour = THEME.success();
        } else if (LiveInputClient.state() == LiveInputClient.State.SHARING && LiveInputClient.target() != null) {
            status = Component.translatable("gui.soundstage.share.sharing_elsewhere", LiveInputClient.target().toShortString());
            statusColour = THEME.warm();
        } else if (LiveInputClient.state() == LiveInputClient.State.STARTING) {
            status = Component.translatable("gui.soundstage.share.waiting");
        } else if (System.currentTimeMillis() - lastSoundMillis > 1500) {
            status = Component.translatable("gui.soundstage.share.no_sound");
            statusColour = THEME.dim();
        }
        if (status != null) {
            statusLine.draw(g, this.font, status.getString(), status, x + 12, y + 131, WINDOW_WIDTH - 24, statusColour);
        } else {
            statusLine.reset();
        }

        paragraph(g, hintText(), x + 12, y + 146, WINDOW_WIDTH - 24, 5, THEME.dim());
    }

    private static Component hintText() {
        net.minecraft.network.chat.MutableComponent hint = Component.translatable("gui.soundstage.share.hint");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String tip = LinuxShareOutput.applies() ? "gui.soundstage.share.tip.linux"
                : os.contains("win") ? "gui.soundstage.share.tip.windows"
                : os.contains("mac") ? "gui.soundstage.share.tip.mac"
                : null;
        return tip == null ? hint : hint.append(" ").append(Component.translatable(tip));
    }

    //? if >=1.21.11 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mouseX = event.x(), mouseY = event.y();
    *///?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
    //?}
        int x = panelX();
        int y = panelY();
        if (mouseX < x + 10 || mouseX >= x + WINDOW_WIDTH - 10
                || mouseY < y + LIST_TOP || mouseY >= y + LIST_BOTTOM) {
            return false;
        }
        int index = (int) ((mouseY - (y + LIST_TOP) + scrollOffset) / ROW_HEIGHT);
        if (index >= 0 && index < devices.size()) {
            String device = devices.get(index);
            if (!available(device)) LiveInputClient.setPreviewing(false);
            LiveInputClient.selectDevice(device);
            LiveInputClient.setPreviewing(available(device));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int y = panelY();
        if (mouseY < y + LIST_TOP || mouseY > y + LIST_BOTTOM) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scrollOffset = net.minecraft.util.Mth.clamp(scrollOffset - scrollY * ROW_HEIGHT, 0, maxScroll);
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void removed() {
        super.removed();
        LiveInputClient.setPreviewing(false);
    }

}
