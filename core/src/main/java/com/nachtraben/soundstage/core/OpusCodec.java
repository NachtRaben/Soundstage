package com.nachtraben.soundstage.core;

import com.sedmelluq.discord.lavaplayer.natives.opus.OpusDecoder;
import com.sedmelluq.discord.lavaplayer.natives.opus.OpusEncoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public final class OpusCodec {

    public static final int SAMPLE_RATE = 48000;
    public static final int CHANNELS = 2;
    public static final int FRAME_SIZE = 960;
    public static final int MAX_PACKET_SIZE = 4000;

    private static final int ENCODER_QUALITY = 10;

    public static final int LOOKAHEAD_SAMPLES = SAMPLE_RATE / 400 + SAMPLE_RATE / 250;

    private static Boolean available;
    private static Throwable unavailableCause;

    private OpusCodec() {
    }

    public static synchronized boolean available() {
        if (available == null) {
            try {
                new OpusDecoder(SAMPLE_RATE, CHANNELS).close();
                available = true;
            } catch (Throwable t) {
                available = false;
                unavailableCause = t;
            }
        }
        return available;
    }

    public static synchronized Throwable unavailableCause() {
        return unavailableCause;
    }

    private static void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException("The Opus native library is not available on this platform",
                    unavailableCause());
        }
    }

    private static ShortBuffer directShorts(int count) {
        return ByteBuffer.allocateDirect(count * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
    }

    public static final class Encoder implements AutoCloseable {
        private OpusEncoder encoder;
        private final ShortBuffer pcm = directShorts(FRAME_SIZE * CHANNELS);
        private final ByteBuffer out = ByteBuffer.allocateDirect(MAX_PACKET_SIZE);

        public Encoder() {
            requireAvailable();
            encoder = new OpusEncoder(SAMPLE_RATE, CHANNELS, ENCODER_QUALITY);
        }

        public synchronized byte[] encode(byte[] bigEndianStereoPcm) {
            if (encoder == null) return null;
            if (bigEndianStereoPcm.length < FRAME_SIZE * CHANNELS * 2) return null;

            ByteBuffer in = ByteBuffer.wrap(bigEndianStereoPcm).order(ByteOrder.BIG_ENDIAN);
            pcm.clear();
            for (int i = 0; i < FRAME_SIZE * CHANNELS; i++) {
                pcm.put(in.getShort());
            }
            pcm.flip();
            return encodeLoaded();
        }

        public synchronized byte[] encode(short[] interleavedStereo) {
            if (encoder == null) return null;
            if (interleavedStereo.length < FRAME_SIZE * CHANNELS) return null;
            pcm.clear();
            pcm.put(interleavedStereo, 0, FRAME_SIZE * CHANNELS);
            pcm.flip();
            return encodeLoaded();
        }

        private byte[] encodeLoaded() {
            int length;
            try {
                length = encoder.encode(pcm, FRAME_SIZE, out);
            } catch (IllegalStateException e) {
                return null;
            }
            if (length <= 0) return null;

            byte[] packet = new byte[length];
            out.get(packet);
            return packet;
        }

        @Override
        public synchronized void close() {
            if (encoder != null) {
                encoder.close();
                encoder = null;
            }
        }
    }

    public static final class Decoder implements AutoCloseable {
        private OpusDecoder decoder;
        private final ByteBuffer in = ByteBuffer.allocateDirect(MAX_PACKET_SIZE);
        private final ShortBuffer pcm = directShorts(FRAME_SIZE * CHANNELS);

        public Decoder() {
            requireAvailable();
            decoder = new OpusDecoder(SAMPLE_RATE, CHANNELS);
        }

        public synchronized byte[] decode(byte[] opusPacket) {
            if (!decodeLoaded(opusPacket)) return null;
            byte[] raw = new byte[pcm.remaining() * 2];
            ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm);
            return raw;
        }

        public synchronized short[] decodeSamples(byte[] opusPacket) {
            if (!decodeLoaded(opusPacket)) return null;
            short[] samples = new short[pcm.remaining()];
            pcm.get(samples);
            return samples;
        }

        private boolean decodeLoaded(byte[] opusPacket) {
            if (decoder == null || opusPacket == null || opusPacket.length == 0
                    || opusPacket.length > MAX_PACKET_SIZE) {
                return false;
            }
            in.clear();
            in.put(opusPacket);
            in.flip();

            int samples;
            try {
                samples = decoder.decode(in, pcm);
            } catch (IllegalStateException e) {
                return false;
            }
            return samples > 0;
        }

        @Override
        public synchronized void close() {
            if (decoder != null) {
                decoder.close();
                decoder = null;
            }
        }
    }
}
