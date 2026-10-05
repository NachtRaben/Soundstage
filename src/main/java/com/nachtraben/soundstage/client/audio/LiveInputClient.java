package com.nachtraben.soundstage.client.audio;

import com.nachtraben.soundstage.config.SoundstageAppearance;
import com.nachtraben.soundstage.core.OpusCodec;
import com.nachtraben.soundstage.network.LiveInputStateS2CPacket;
import com.nachtraben.soundstage.network.SpeakerControlC2SPacket;
import dev.armadeus.armalib.platform.client.ClientNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class LiveInputClient {

    public enum State { IDLE, STARTING, SHARING }

    public static final String RIG_DEVICE = "Soundstage rig tone";

    public static volatile java.util.function.Function<String, LiveInputCapture.Source> sourceOverride;

    private static final long START_TIMEOUT_MILLIS = 5_000;

    private static State state = State.IDLE;
    private static BlockPos sharingTo;
    private static long shareId = LiveInputStateS2CPacket.NO_SHARE;
    private static BlockPos askedFor;
    private static long askedAt;
    private static LiveInputCapture capture;
    private static boolean previewing;
    private static Component lastFailure;

    private LiveInputClient() {
    }

    public static State state() {
        return state;
    }

    public static BlockPos target() {
        return state == State.STARTING ? askedFor : sharingTo;
    }

    public static boolean isSharingTo(BlockPos pos) {
        return sharingTo != null && sharingTo.equals(pos);
    }

    public static String device() {
        String saved = SoundstageAppearance.shareDevice();
        return saved == null || saved.isEmpty() ? null : saved;
    }

    public static float level() {
        LiveInputCapture c = capture;
        return c == null ? 0 : c.level();
    }

    public static Component lastFailure() {
        return lastFailure;
    }

    public static void requestStart(BlockPos pos) {
        if (!OpusCodec.available()) {
            tell(Component.translatable("chat.soundstage.audio_unavailable"));
            return;
        }
        state = State.STARTING;
        askedFor = pos.immutable();
        askedAt = System.currentTimeMillis();
        lastFailure = null;
        ensureCapture(sharingTo != null);
        ClientNetwork.sendToServer(new SpeakerControlC2SPacket(pos, SpeakerControlC2SPacket.Action.SHARE_START));
    }

    public static void requestStop() {
        if (state == State.IDLE) return;
        BlockPos at = sharingTo != null ? sharingTo : askedFor;
        endLocally(true);
        ClientNetwork.sendToServer(new SpeakerControlC2SPacket(at == null ? BlockPos.ZERO : at,
                SpeakerControlC2SPacket.Action.SHARE_STOP));
    }

    public static void selectDevice(String device) {
        SoundstageAppearance.setShareDevice(device);
        lastFailure = null;
        releaseCapture();
        if (previewing || sharingTo != null) ensureCapture(sharingTo != null);
    }

    public static void setPreviewing(boolean wanted) {
        if (wanted == previewing) return;
        previewing = wanted;
        if (wanted) {
            lastFailure = null;
            ensureCapture(sharingTo != null);
        } else if (state == State.IDLE) {
            releaseCapture();
        }
    }

    public static void retry() {
        lastFailure = null;
        if (previewing || sharingTo != null) ensureCapture(sharingTo != null);
    }

    public static void onServerState(LiveInputStateS2CPacket packet) {
        if (packet.sharing) {
            state = State.SHARING;
            sharingTo = packet.pos;
            shareId = packet.shareId;
            askedFor = null;
            ensureCapture(true);
        } else if (packet.shareId == LiveInputStateS2CPacket.NO_SHARE) {
            if (state == State.STARTING) {
                askedFor = null;
                if (sharingTo != null) {
                    state = State.SHARING;
                } else {
                    endLocally(true);
                }
            }
        } else if (packet.shareId == shareId) {
            endLocally(true);
        }
    }

    public static void tick() {
        if (state == State.STARTING && System.currentTimeMillis() - askedAt > START_TIMEOUT_MILLIS) {
            askedFor = null;
            if (sharingTo != null) {
                state = State.SHARING;
            } else {
                endLocally(true);
            }
            tell(Component.translatable("chat.soundstage.share.no_answer"));
        }
        LiveInputCapture c = capture;
        if (c != null && c.ended()) {
            capture = null;
            String failure = c.failure();
            if (failure != null) {
                String name = c.device() == null
                        ? Component.translatable("gui.soundstage.share.default_device").getString() : c.device();
                lastFailure = Component.translatable(failure.equals("open")
                        ? "chat.soundstage.share.device_failed" : "chat.soundstage.share.device_lost", name);
                if (state != State.IDLE) tell(lastFailure);
                if (state != State.IDLE) {
                    BlockPos at = sharingTo != null ? sharingTo : askedFor;
                    endLocally(false);
                    ClientNetwork.sendToServer(new SpeakerControlC2SPacket(at == null ? BlockPos.ZERO : at,
                            SpeakerControlC2SPacket.Action.SHARE_STOP));
                }
            }
        }
    }

    public static void shutdown() {
        previewing = false;
        lastFailure = null;
        state = State.IDLE;
        shareId = LiveInputStateS2CPacket.NO_SHARE;
        sharingTo = null;
        askedFor = null;
        releaseCapture();
    }

    private static void endLocally(boolean keepPreview) {
        state = State.IDLE;
        shareId = LiveInputStateS2CPacket.NO_SHARE;
        sharingTo = null;
        askedFor = null;
        if (keepPreview && previewing) {
            ensureCapture(false);
        } else {
            releaseCapture();
        }
    }

    private static void ensureCapture(boolean sending) {
        LiveInputCapture c = capture;
        if (c == null || c.ended()) {
            capture = new LiveInputCapture(device(), sending);
        } else {
            c.setSending(sending);
        }
    }

    private static void releaseCapture() {
        LiveInputCapture c = capture;
        capture = null;
        if (c != null) c.stop();
    }

    private static void tell(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            //? if >=26.1 {
            /*minecraft.player.sendSystemMessage(message.copy().withStyle(net.minecraft.ChatFormatting.YELLOW));
            *///?} else {
            minecraft.player.displayClientMessage(message.copy().withStyle(net.minecraft.ChatFormatting.YELLOW), false);
            //?}
        }
    }
}
