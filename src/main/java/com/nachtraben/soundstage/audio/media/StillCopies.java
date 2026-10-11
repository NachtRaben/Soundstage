package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.core.video.PictureSize;
import com.nachtraben.soundstage.core.video.StillImage;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import org.slf4j.LoggerFactory;

import java.io.IOException;

final class StillCopies implements FfmpegRuns.Source {

    @FunctionalInterface
    interface Picture {
        Ffmpeg.Transcode start(PictureSize.Copy copy) throws IOException;
    }

    @FunctionalInterface
    interface Silence {
        Ffmpeg.Transcode start() throws IOException;
    }

    private final StillImage.Header header;
    private final Picture picture;
    private final Silence silence;

    StillCopies(StillImage.Header header, Picture picture, Silence silence) {
        this.header = header;
        this.picture = picture;
        this.silence = silence;
    }

    @Override
    public Ffmpeg.Transcode start(long startMillis, FfmpegRuns.Sizing sizing, boolean surround, boolean withPicture)
            throws IOException {
        try {
            return withPicture ? picture.start(sizing.copy(header.width(), header.height())) : silence.start();
        } catch (IOException e) {
            Ffmpeg.forget();
            throw e;
        }
    }

    @Override
    public boolean pictureOptional() {
        return true;
    }

    @Override
    public boolean hasVideo() {
        return true;
    }

    @Override
    public FfmpegRuns.Next ended(FfmpegRuns.RunEnd end) {
        if (end.exit() != 0 && end.exit() != -1) {
            LoggerFactory.getLogger("Soundstage").warn("Soundstage: ffmpeg stopped with exit {} showing an image.",
                    end.exit());
            throw new FriendlyException("That image stopped with an error.", FriendlyException.Severity.SUSPICIOUS,
                    null);
        }
        return FfmpegRuns.Next.END;
    }
}
