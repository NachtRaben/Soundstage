package com.nachtraben.soundstage.client.gui;

import dev.armadeus.armalib.ui.UiToast;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class NowPlayingToast {
    private NowPlayingToast() {
    }

    public static void show(BlockPos receiverPos, String title, String artist, String artworkUrl) {
        UiToast.builder(receiverPos.immutable())
                .heading(Component.translatable("soundstage.toast.now_playing"))
                .title(title)
                .subtitle(artist)
                .artwork(artworkUrl)
                .show();
    }
}
