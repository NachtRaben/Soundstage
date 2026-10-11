package com.nachtraben.soundstage.config;

import dev.armadeus.armalib.config.Configs;
import dev.armadeus.armalib.core.config.BooleanSetting;
import dev.armadeus.armalib.core.config.ConfigSpec;
import dev.armadeus.armalib.core.config.DoubleSetting;
import dev.armadeus.armalib.core.config.EnumSetting;
import dev.armadeus.armalib.core.config.Scope;
import dev.armadeus.armalib.core.config.StringSetting;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class AppearanceConfigSpec implements AppearanceSettings {

    private static final String REGISTRATION_ID = "soundstage_config_internal";

    public static final ConfigSpec SPEC;
    private static final Values VALUES;

    private static final AppearanceSettings D = new AppearanceSettings() {};

    static {
        ConfigSpec.Builder b = ConfigSpec.builder("soundstage-client", Scope.CLIENT);
        VALUES = new Values(b);
        SPEC = Configs.register(REGISTRATION_ID, b.build());
    }

    private static final class Values {
        final DoubleSetting driverExcursionGain;
        final BooleanSetting trackChangeToast;
        final BooleanSetting trackChangeToastArtwork;
        final BooleanSetting showSubtitles;
        final EnumSetting<AppearanceSettings.SubtitleSize> subtitleSize;
        final StringSetting shareDevice;

        Values(ConfigSpec.Builder b) {
            b.push("appearance", "How this client draws Soundstage's speakers. Affects nothing else.");
            driverExcursionGain = b.defineInRange("driverExcursionGain", D.driverExcursionGain(), 0.25, 8.0,
                    "How readily a speaker cone reaches its full travel.",
                    "1.0 moves the cones by exactly the signal the speaker is playing. Higher values",
                    "make ordinary music read on the smaller drivers, whose travel is otherwise a",
                    "fraction of a pixel; lower values calm them down. The mechanical limit does not",
                    "change: a cone still stops where the cabinet's recess and the driver's own",
                    "suspension say it does, so this cannot push one through its grille.");
            trackChangeToast = b.define("trackChangeToast", D.trackChangeToast(),
                    "Show a toast naming the track when playback changes on a receiver nearby.",
                    "Turning it off means no announcement of a track change.");
            trackChangeToastArtwork = b.define("trackChangeToastArtwork", D.trackChangeToastArtwork(),
                    "Show the track's cover art on that toast, and on a display's now-playing card.",
                    "Fetching it means this client connects to whoever hosts the image, which is a",
                    "separate decision from whether to show the toast at all. With this off the",
                    "toast and the card still name the track and nothing is downloaded.");
            showSubtitles = b.define("showSubtitles", D.showSubtitles(),
                    "Show a screen's subtitle captions, when the video playing on it has any.",
                    "Off draws no captions; nothing else about the screen's picture changes.");
            subtitleSize = b.defineEnum("subtitleSize", D.subtitleSize(),
                    "How large a screen draws its subtitle captions: SMALL, MEDIUM or LARGE.",
                    "Set from the display menu; there is rarely a reason to edit it here.");
            b.pop();

            b.push("sharing", "Sharing this computer's audio through a receiver.");
            shareDevice = b.define("shareDevice", D.shareDevice(),
                    "The input device to share from, as the share screen lists it. Empty for the",
                    "system's default input. Set from that screen; there is rarely a reason to edit it here.");
            b.pop();
        }
    }

    @Override
    public String shareDevice() {
        return VALUES.shareDevice.get();
    }

    @Override
    public void setShareDevice(String device) {
        VALUES.shareDevice.set(device == null ? "" : device);
        save();
    }

    @Override
    public float driverExcursionGain() {
        return (float) (double) VALUES.driverExcursionGain.get();
    }

    @Override
    public boolean trackChangeToast() {
        return VALUES.trackChangeToast.get();
    }

    @Override
    public boolean trackChangeToastArtwork() {
        return VALUES.trackChangeToastArtwork.get();
    }

    @Override
    public boolean showSubtitles() {
        return VALUES.showSubtitles.get();
    }

    @Override
    public void setShowSubtitles(boolean showSubtitles) {
        VALUES.showSubtitles.set(showSubtitles);
        save();
    }

    @Override
    public SubtitleSize subtitleSize() {
        return VALUES.subtitleSize.get();
    }

    @Override
    public void setSubtitleSize(SubtitleSize subtitleSize) {
        VALUES.subtitleSize.set(subtitleSize);
        save();
    }

    private static void save() {
        try {
            Configs.save(SPEC);
        } catch (IOException e) {
            throw new UncheckedIOException("soundstage-client.aon could not be saved", e);
        }
    }
}
