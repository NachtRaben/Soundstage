package com.nachtraben.soundstage.audio;

import com.nachtraben.soundstage.core.LiveInputBuffer;
import com.nachtraben.soundstage.core.OpusCodec;
import com.sedmelluq.discord.lavaplayer.filter.AudioPipeline;
import com.sedmelluq.discord.lavaplayer.filter.AudioPipelineFactory;
import com.sedmelluq.discord.lavaplayer.filter.PcmFormat;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BaseAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrameBuffer;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

public final class LiveInputTrack extends BaseAudioTrack {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Soundstage");

    private static final int MAX_FRAMES_AHEAD = 3;

    private final LiveInputSession session;

    LiveInputTrack(LiveInputSession session) {
        this(new AudioTrackInfo("Live audio", session.sharerName(), Long.MAX_VALUE,
                "soundstage-live:" + session.id(), true, null, null, null), session);
    }

    private LiveInputTrack(AudioTrackInfo info, LiveInputSession session) {
        super(info);
        this.session = session;
    }

    public LiveInputSession session() {
        return session;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        LiveInputBuffer buffer = session.buffer();
        buffer.rejoinEdge();
        short[] silence = new short[OpusCodec.FRAME_SIZE * OpusCodec.CHANNELS];
        OpusCodec.Decoder decoder = new OpusCodec.Decoder();
        AudioPipeline pipeline = AudioPipelineFactory.create(executor.getProcessingContext(),
                new PcmFormat(OpusCodec.CHANNELS, OpusCodec.SAMPLE_RATE));
        try {
            executor.executeProcessingLoop(() -> {
                AudioFrameBuffer ahead = executor.getAudioBuffer();
                while (!session.isEnded()) {
                    if (buffer.idle(System.nanoTime())
                            && !com.nachtraben.soundstage.RigStaging.fault("quiet-share-kept")) {
                        session.end(LiveInputSession.End.WENT_QUIET);
                        break;
                    }
                    if (ahead.getFullCapacity() - ahead.getRemainingCapacity() >= MAX_FRAMES_AHEAD) {
                        Thread.sleep(10);
                        continue;
                    }
                    byte[] packet = buffer.poll();
                    short[] pcm = packet == null ? null : decoder.decodeSamples(packet);
                    if (pcm == null || pcm.length != silence.length) pcm = silence;
                    pipeline.process(pcm, 0, pcm.length);
                }
            }, null);
        } catch (Exception e) {
            if (!session.isEnded()) {
                LOGGER.warn("Share from {} on receiver {} could not be played", session.sharerName(),
                        session.pos(), e);
                session.end(LiveInputSession.End.FAILED);
            }
            throw e;
        } finally {
            pipeline.close();
            decoder.close();
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new LiveInputTrack(trackInfo, session);
    }
}
