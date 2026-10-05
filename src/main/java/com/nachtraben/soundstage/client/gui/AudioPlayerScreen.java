package com.nachtraben.soundstage.client.gui;

import dev.armadeus.armalib.core.ui.Theme;
import dev.armadeus.armalib.ui.ArtworkCache;
import dev.armadeus.armalib.ui.ScrollingText;
import dev.armadeus.armalib.ui.UiButton;
import dev.armadeus.armalib.ui.UiDraw;
import dev.armadeus.armalib.ui.UiDropdown;
import dev.armadeus.armalib.ui.UiGraphics;
import dev.armadeus.armalib.ui.UiIcon;
import dev.armadeus.armalib.ui.UiScreen;
import dev.armadeus.armalib.ui.UiSegmented;
import dev.armadeus.armalib.ui.UiSlider;
import dev.armadeus.armalib.ui.UiSwitch;
import com.nachtraben.soundstage.RigStaging;
import com.nachtraben.soundstage.block.IAudioReceiver;
import com.nachtraben.soundstage.block.ReceiverBlockEntity;
import com.nachtraben.soundstage.block.speaker.SpeakerBlockEntity;
import com.nachtraben.soundstage.network.QueueOpC2SPacket;
import com.nachtraben.soundstage.network.SpeakerControlC2SPacket;
import com.nachtraben.soundstage.network.SpeakerMetadataS2CPacket;
import dev.armadeus.armalib.platform.client.ClientNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class AudioPlayerScreen extends Screen {
    static final Theme THEME = ScreenTheme.THEME;

    static final int WINDOW_WIDTH = 400;
    static final int WINDOW_HEIGHT = 232;
    static final int HEADER_HEIGHT = 24;
    static final int FOOTER_HEIGHT = 22;
    static final int PADDING = 12;
    private static final double CORNER = 5;

    public enum Tab {
        NOW_PLAYING("now_playing", UiIcon.PLAY),
        QUEUE("queue", UiIcon.QUEUE),
        DISPLAYS("displays", UiIcon.DISPLAY),
        SPEAKERS("speakers", UiIcon.SPEAKER),
        SETTINGS("settings", UiIcon.SETTINGS);

        final String key;
        final UiIcon icon;

        Tab(String key, UiIcon icon) {
            this.key = key;
            this.icon = icon;
        }

        Component label() {
            return Component.translatable("gui.soundstage.receiver.tab." + key);
        }
    }

    private static Tab lastTab = Tab.NOW_PLAYING;

    final BlockPos pos;
    private final boolean partyBox;

    String trackTitle = "";
    String artist = "";
    boolean playing = false;
    int volume = IAudioReceiver.DEFAULT_VOLUME;
    List<String> queue = new ArrayList<>();
    List<Long> queueIds = new ArrayList<>();
    int loopMode = 0;
    boolean shuffle = false;
    boolean locked = false;
    boolean mediaFolderEnabled = false;
    boolean pictureEnabled = true;
    boolean levelling = false;
    long trackDuration = 0;
    long currentPosition = 0;
    long lastSyncTime = 0;
    String currentThumbnailUrl = "";
    boolean synced = false;

    String draftSource = "";

    private Tab tab;
    private final Map<Tab, ReceiverTab> tabs = new EnumMap<>(Tab.class);
    private boolean subscribed;
    private final ScrollingText footerLine = new ScrollingText();

    public AudioPlayerScreen(ReceiverBlockEntity sbe) {
        super(Component.translatable("block.soundstage.receiver"));
        this.pos = sbe.getBlockPos();
        this.partyBox = false;
    }

    public AudioPlayerScreen(SpeakerBlockEntity sbe) {
        super(Component.translatable(sbe instanceof com.nachtraben.soundstage.block.PartyBoxBlockEntity
                ? "block.soundstage.party_box" : "block.soundstage.receiver"));
        BlockPos receiverPos = sbe.getReceiverPos();
        this.partyBox = sbe instanceof com.nachtraben.soundstage.block.PartyBoxBlockEntity;
        this.pos = (partyBox || receiverPos == null) ? sbe.getBlockPos() : receiverPos;
    }

    boolean isReceiver() {
        return Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(pos) instanceof ReceiverBlockEntity;
    }

    IAudioReceiver receiverBlock() {
        return Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(pos) instanceof IAudioReceiver receiver ? receiver : null;
    }

    List<Tab> availableTabs() {
        return isReceiver() ? List.of(Tab.values()) : List.of(Tab.NOW_PLAYING, Tab.QUEUE, Tab.SETTINGS);
    }

    public void showTab(Tab next) {
        if (!availableTabs().contains(next)) next = Tab.NOW_PLAYING;
        if (next == tab) return;
        if (tab != null) content().leaving();
        tab = next;
        lastTab = next;
        rebuildWidgets();
    }

    Tab tab() {
        return tab;
    }

    private ReceiverTab content() {
        return tabs.computeIfAbsent(tab, t -> switch (t) {
            case NOW_PLAYING -> new NowPlayingTab(this);
            case QUEUE -> new QueueTab(this);
            case DISPLAYS -> new DisplaysTab(this);
            case SPEAKERS -> new SpeakersTab(this);
            case SETTINGS -> new SettingsTab(this);
        });
    }

    boolean hasFooter() {
        return tab != Tab.NOW_PLAYING;
    }

    @Override
    protected void init() {
        super.init();
        UiDropdown.screenInit(this);
        if (tab == null) {
            tab = availableTabs().contains(lastTab) ? lastTab : Tab.NOW_PLAYING;
        } else if (!availableTabs().contains(tab)) {
            tab = Tab.NOW_PLAYING;
        }
        if (!subscribed) {
            subscribed = true;
            sendControl(SpeakerControlC2SPacket.Action.REQUEST_SYNC, "", 0);
        }
        int x = panelX(), y = panelY();

        int tabX = x + 26;
        for (Tab each : availableTabs()) {
            int w = UiButton.widthFor(each.label(), true, 9, 7);
            addRenderableWidget(new UiButton(THEME, tabX, y, w, HEADER_HEIGHT, UiButton.Look.TAB, () -> showTab(each))
                    .icon(each.icon).iconSize(9).label(each.label()).selected(() -> tab == each));
            tabX += w;
        }
        addRenderableWidget(new UiButton(THEME, x + WINDOW_WIDTH - 22, y + 4, 16, 16, UiButton.Look.ICON, this::onClose)
                .icon(UiIcon.CLOSE).iconSize(9).tooltip(Component.translatable("gui.soundstage.receiver.close")));

        if (hasFooter()) {
            int footerY = footerY();
            addRenderableWidget(new UiButton(THEME, x + 6, footerY + 3, 17, 17, UiButton.Look.ICON,
                    () -> sendControl(SpeakerControlC2SPacket.Action.PLAY_PAUSE, "", 0))
                    .icon(() -> playing ? UiIcon.PAUSE : UiIcon.PLAY).iconSize(11)
                    .tooltip(() -> Component.translatable(playing ? "gui.soundstage.player.pause" : "gui.soundstage.player.play")));
            addRenderableWidget(new UiButton(THEME, x + 24, footerY + 3, 17, 17, UiButton.Look.ICON,
                    () -> sendControl(SpeakerControlC2SPacket.Action.SKIP, "", 0))
                    .icon(UiIcon.NEXT).iconSize(11).tooltip(Component.translatable("gui.soundstage.player.skip")));
        }

        content().init(x, y + HEADER_HEIGHT, WINDOW_WIDTH, bodyHeight());
    }

    UiButton shuffleButton(int left, int top, int size) {
        return new UiButton(THEME, left, top, size, size, UiButton.Look.ICON,
                () -> sendControl(SpeakerControlC2SPacket.Action.TOGGLE_SHUFFLE, "", 0))
                .icon(UiIcon.SHUFFLE).iconSize(size * 0.6).selected(() -> shuffle)
                .tooltip(() -> Component.translatable(shuffle
                        ? "gui.soundstage.player.shuffle_on" : "gui.soundstage.player.shuffle_off"));
    }

    UiButton loopButton(int left, int top, int size) {
        return new UiButton(THEME, left, top, size, size, UiButton.Look.ICON,
                () -> sendControl(SpeakerControlC2SPacket.Action.TOGGLE_LOOP, "", 0))
                .icon(() -> loopMode == 1 ? UiIcon.REPEAT_ONE : UiIcon.REPEAT).iconSize(size * 0.6)
                .selected(() -> loopMode != 0)
                .tooltip(() -> Component.translatable("gui.soundstage.player.loop."
                        + (loopMode == 1 ? "track" : loopMode == 2 ? "queue" : "off")));
    }

    <T extends AbstractWidget> T addTabWidget(T widget) {
        return addRenderableWidget(widget);
    }

    void rebuild() {
        rebuildWidgets();
    }

    int panelX() { return (this.width - WINDOW_WIDTH) / 2; }
    int panelY() { return Math.max(4, (this.height - WINDOW_HEIGHT) / 2); }
    int bodyHeight() { return WINDOW_HEIGHT - HEADER_HEIGHT - (hasFooter() ? FOOTER_HEIGHT : 0); }
    int footerY() { return panelY() + WINDOW_HEIGHT - FOOTER_HEIGHT; }

    void sendControl(SpeakerControlC2SPacket.Action action, String stringData, int intData) {
        ClientNetwork.sendToServer(new SpeakerControlC2SPacket(pos, action, stringData, intData));
    }

    void sendSeek(long millis) {
        ClientNetwork.sendToServer(new SpeakerControlC2SPacket(pos, SpeakerControlC2SPacket.Action.SEEK, "", 0, millis));
        currentPosition = millis;
        lastSyncTime = System.currentTimeMillis();
    }

    void sendQueueOp(QueueOpC2SPacket.Action action, long entryId, int targetIndex) {
        ClientNetwork.sendToServer(new QueueOpC2SPacket(this.pos, action, entryId, targetIndex));
    }

    long position() {
        long at = currentPosition + (playing ? System.currentTimeMillis() - lastSyncTime : 0);
        return trackDuration > 0 ? Math.min(at, trackDuration) : at;
    }

    boolean hasTrack() {
        return !trackTitle.isBlank();
    }

    boolean isLive() {
        return trackDuration <= 0 && hasTrack();
    }

    Component timeText() {
        if (trackDuration > 0) {
            return Component.literal(com.nachtraben.soundstage.core.TrackTime.format(position(), trackDuration) + " / "
                    + com.nachtraben.soundstage.core.TrackTime.format(trackDuration, trackDuration));
        }
        if (hasTrack()) {
            return Component.translatable("gui.soundstage.player.live", com.nachtraben.soundstage.core.TrackTime.format(position()));
        }
        return Component.empty();
    }

    public void updateMetadata(SpeakerMetadataS2CPacket packet) {
        if (!packet.getPos().equals(this.pos)) return;
        boolean folderChanged = packet.isMediaFolderEnabled() != this.mediaFolderEnabled;

        this.trackTitle = packet.getTrackTitle();
        this.artist = packet.getArtist();
        this.playing = packet.isPlaying();
        this.volume = packet.getVolume();
        this.queue = packet.getQueue();
        this.queueIds = packet.getQueueIds();
        this.loopMode = packet.getLoopMode();
        this.shuffle = packet.isShuffle();
        this.locked = packet.isLocked();
        this.pictureEnabled = packet.isPictureEnabled();
        this.levelling = packet.isLevelling();
        this.mediaFolderEnabled = packet.isMediaFolderEnabled();
        this.trackDuration = packet.getDuration();
        this.currentPosition = packet.getPosition();
        this.lastSyncTime = System.currentTimeMillis();
        this.synced = true;
        String newThumb = packet.getThumbnailUrl();
        if (newThumb != null) this.currentThumbnailUrl = newThumb;

        if (tab != null) content().metadataChanged(folderChanged);
    }

    @Override
    public void tick() {
        super.tick();
        if (tab != null) content().tick();
    }

    //? if >=1.20.2 {
    @Override
    //?}
    //? if >=26.1 {
    /*public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor raw, int mouseX, int mouseY, float partialTick) {
    *///?} else {
    public void renderBackground(net.minecraft.client.gui.GuiGraphics raw, int mouseX, int mouseY, float partialTick) {
    //?}
        UiGraphics g = UiGraphics.of(raw);
        g.fill(0, 0, this.width, this.height, THEME.backdrop());
        int x = panelX(), y = panelY();
        UiScreen.drawFrame(g, THEME, x, y, WINDOW_WIDTH, WINDOW_HEIGHT);
        UiDraw.icon(g, partyBox ? UiIcon.SPEAKER : UiIcon.MUSIC, x + 8, y + 6, 12, THEME.accent());
        if (locked) {
            int lockX = x + WINDOW_WIDTH - 36;
            UiDraw.icon(g, UiIcon.LOCK, lockX, y + 7, 10, THEME.warm());
            if (mouseX >= lockX - 2 && mouseX < lockX + 12 && mouseY >= y + 4 && mouseY < y + 20) {
                tooltip(g, Component.translatable("gui.soundstage.receiver.locked_hint"), mouseX, mouseY);
            }
        }
        if (tab != null) content().renderBackground(g, mouseX, mouseY, partialTick);
        if (hasFooter()) renderFooter(g);
    }

    @Override
    //? if >=26.1 {
    /*public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor raw, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(raw, mouseX, mouseY, partialTick);
    *///?} else if >=1.20.2 {
    public void render(net.minecraft.client.gui.GuiGraphics raw, int mouseX, int mouseY, float partialTick) {
        super.render(raw, mouseX, mouseY, partialTick);
    //?} else {
    /*public void render(net.minecraft.client.gui.GuiGraphics raw, int mouseX, int mouseY, float partialTick) {
        renderBackground(raw, mouseX, mouseY, partialTick);
        super.render(raw, mouseX, mouseY, partialTick);
    *///?}
        if (tab != null) content().renderForeground(UiGraphics.of(raw), mouseX, mouseY, partialTick);
        if (!dropdownUnfed()) UiDropdown.drawList(this, UiGraphics.of(raw), mouseX, mouseY);
    }

    private static boolean dropdownUnfed() {
        return RigStaging.fault("tab-dropdown-unfed");
    }

    private void renderFooter(UiGraphics g) {
        int x = panelX(), y = footerY();
        UiDraw.roundRect(g, x, y, WINDOW_WIDTH, FOOTER_HEIGHT, CORNER, THEME.header());
        g.fill(x, y, x + WINDOW_WIDTH, y + (int) CORNER, THEME.header());
        g.fill(x, y, x + WINDOW_WIDTH, y + 1, THEME.border());
        if (trackDuration > 0) {
            double progress = Math.min(1.0, position() / (double) trackDuration);
            UiDraw.roundRect(g, x, y, WINDOW_WIDTH * progress, 1.5, 0, THEME.warm());
        }
        drawArtwork(g, x + 45, y + 3, 16, 16, 2, THEME.header());
        var font = this.font;
        Component time = timeText();
        int timeWidth = font.width(time);
        g.text(font, time, x + WINDOW_WIDTH - PADDING - timeWidth, y + 8, THEME.muted());
        int textX = x + 67;
        int room = x + WINDOW_WIDTH - PADDING - timeWidth - 10 - textX;
        net.minecraft.network.chat.MutableComponent line = Component.literal(
                hasTrack() ? trackTitle : Component.translatable("gui.soundstage.player.no_track").getString());
        if (hasTrack() && !artist.isBlank()) {
            line.append(Component.literal("  ·  " + artist).withStyle(style -> style.withColor(THEME.muted() & 0xFFFFFF)));
        }
        footerLine.draw(g, font, trackTitle + "\u0000" + artist, line, textX, y + 8, room,
                hasTrack() ? THEME.text() : THEME.dim());
    }

    void drawArtwork(UiGraphics g, double x, double y, double maxWidth, double maxHeight, double radius, int behind) {
        if (UiDraw.artwork(g, currentThumbnailUrl, x, y, maxWidth, maxHeight, radius, behind) != null) return;
        double size = Math.min(maxWidth, maxHeight);
        double left = x + (maxWidth - size) / 2, top = y + (maxHeight - size) / 2;
        UiDraw.roundRect(g, left, top, size, size, radius, THEME.surface());
        UiDraw.iconCentred(g, UiIcon.MUSIC, left + size / 2, top + size / 2, size * 0.45, THEME.dim());
    }

    void tooltip(UiGraphics g, Component text, int mouseX, int mouseY) {
        //? if >=1.21.11 {
        /*g.raw().setTooltipForNextFrame(font, text, mouseX, mouseY);
        *///?} else {
        setTooltipForNextRenderPass(text);
        //?}
    }

    //? if >=1.21.11 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (!dropdownUnfed() && UiDropdown.clickList(this, event.x(), event.y())) return true;
        if (super.mouseClicked(event, doubleClick)) return true;
        return tab != null && content().mouseClicked(event.x(), event.y(), event.button());
    }
    *///?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!dropdownUnfed() && UiDropdown.clickList(this, mouseX, mouseY)) return true;
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        return tab != null && content().mouseClicked(mouseX, mouseY, button);
    }
    //?}

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!dropdownUnfed() && UiDropdown.scrollList(this, scrollY)) return true;
        if (tab != null && content().mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    //? if >=1.21.11 {
    /*@Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (!dropdownUnfed() && UiDropdown.keyList(this, event.key())) return true;
        if (tab != null && content().keyPressed(event.key(), event.scancode(), event.modifiers())) return true;
        return super.keyPressed(event);
    }
    *///?} else {
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!dropdownUnfed() && UiDropdown.keyList(this, keyCode)) return true;
        if (tab != null && content().keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    //?}

    @Override
    public void removed() {
        UiDropdown.closeAny();
        super.removed();
        if (tab != null) content().leaving();
        sendControl(SpeakerControlC2SPacket.Action.CLOSE_SYNC, "", 0);
        subscribed = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
