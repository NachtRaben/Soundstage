package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.core.video.MediaListing;
import com.nachtraben.soundstage.network.MediaBrowseReplyS2CPacket;
import dev.armadeus.armalib.platform.network.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class MediaBrowseRequests {

    private MediaBrowseRequests() {}

    public static void handle(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!MediaBrowseThrottle.tryAcquire(player.getUUID())) return;
        MediaBrowseIo.execute(() -> {
            MediaListing.Result result = MediaFolder.listNames();
            level.getServer().execute(() ->
                    Network.sendToPlayer(player, new MediaBrowseReplyS2CPacket(pos, result.names(), result.truncated())));
        });
    }
}
