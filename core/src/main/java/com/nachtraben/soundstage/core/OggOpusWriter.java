package com.nachtraben.soundstage.core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class OggOpusWriter {

    private OggOpusWriter() {}

    private static final int[] CRC_TABLE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int r = i << 24;
            for (int j = 0; j < 8; j++) {
                r = (r & 0x80000000) != 0 ? (r << 1) ^ 0x04C11DB7 : r << 1;
            }
            CRC_TABLE[i] = r;
        }
    }

    private static int crc(byte[] data, int length) {
        int r = 0;
        for (int i = 0; i < length; i++) {
            r = (r << 8) ^ CRC_TABLE[((r >>> 24) & 0xFF) ^ (data[i] & 0xFF)];
        }
        return r;
    }

    public static byte[] write(List<byte[]> packets, int channels, int serial) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int page = 0;

        byte[] head = new byte[19];
        System.arraycopy("OpusHead".getBytes(StandardCharsets.US_ASCII), 0, head, 0, 8);
        head[8] = 1;
        head[9] = (byte) channels;
        writeShortLE(head, 10, 3840);
        writeIntLE(head, 12, OpusCodec.SAMPLE_RATE);
        writeShortLE(head, 16, 0);
        head[18] = 0;
        out.writeBytes(pageOf(new byte[][]{head}, 0L, 0x02, serial, page++));

        byte[] vendor = "Soundstage".getBytes(StandardCharsets.UTF_8);
        byte[] tags = new byte[8 + 4 + vendor.length + 4];
        System.arraycopy("OpusTags".getBytes(StandardCharsets.US_ASCII), 0, tags, 0, 8);
        writeIntLE(tags, 8, vendor.length);
        System.arraycopy(vendor, 0, tags, 12, vendor.length);
        writeIntLE(tags, 12 + vendor.length, 0);
        out.writeBytes(pageOf(new byte[][]{tags}, 0L, 0x00, serial, page++));

        long granule = 0;
        for (int i = 0; i < packets.size(); i++) {
            granule += OpusCodec.FRAME_SIZE;
            int flags = i == packets.size() - 1 ? 0x04 : 0x00;
            out.writeBytes(pageOf(new byte[][]{packets.get(i)}, granule, flags, serial, page++));
        }
        return out.toByteArray();
    }

    private static byte[] pageOf(byte[][] packets, long granule, int flags, int serial, int sequence) {
        List<Integer> segments = new ArrayList<>();
        int payload = 0;
        for (byte[] packet : packets) {
            int remaining = packet.length;
            while (remaining >= 255) {
                segments.add(255);
                remaining -= 255;
            }
            segments.add(remaining);
            payload += packet.length;
        }
        if (segments.size() > 255) {
            throw new IllegalArgumentException("page needs " + segments.size() + " segments, max 255");
        }

        byte[] out = new byte[27 + segments.size() + payload];
        System.arraycopy("OggS".getBytes(StandardCharsets.US_ASCII), 0, out, 0, 4);
        out[4] = 0;
        out[5] = (byte) flags;
        writeLongLE(out, 6, granule);
        writeIntLE(out, 14, serial);
        writeIntLE(out, 18, sequence);
        writeIntLE(out, 22, 0);
        out[26] = (byte) segments.size();
        int at = 27;
        for (int s : segments) out[at++] = (byte) s;
        for (byte[] packet : packets) {
            System.arraycopy(packet, 0, out, at, packet.length);
            at += packet.length;
        }
        writeIntLE(out, 22, crc(out, out.length));
        return out;
    }

    private static void writeShortLE(byte[] b, int at, int v) {
        b[at] = (byte) v;
        b[at + 1] = (byte) (v >>> 8);
    }

    private static void writeIntLE(byte[] b, int at, int v) {
        b[at] = (byte) v;
        b[at + 1] = (byte) (v >>> 8);
        b[at + 2] = (byte) (v >>> 16);
        b[at + 3] = (byte) (v >>> 24);
    }

    private static void writeLongLE(byte[] b, int at, long v) {
        for (int i = 0; i < 8; i++) b[at + i] = (byte) (v >>> (8 * i));
    }
}
