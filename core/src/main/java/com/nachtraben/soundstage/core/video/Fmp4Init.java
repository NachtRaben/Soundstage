package com.nachtraben.soundstage.core.video;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record Fmp4Init(Video video, Audio audio, Map<Integer, Defaults> defaults) {

    public record Video(int trackId, long timescale, boolean hevc, List<byte[]> parameterSets, int nalLengthSize) {
    }

    public record Audio(int trackId, long timescale, int profile, int frequencyIndex, int channels) {
    }

    public record Defaults(long duration, long size, int flags) {
    }

    static final int[] SAMPLE_RATES = {96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000,
            11025, 8000, 7350};

    public static Fmp4Init parse(byte[] init) throws IOException {
        Video video = null;
        Audio audio = null;
        Map<Integer, Defaults> defaults = new HashMap<>();
        Box moov = Box.find(init, 0, init.length, "moov");
        if (moov == null) throw new IOException("the init segment has no moov");
        for (Box box : Box.children(init, moov.contentStart(), moov.end())) {
            if (box.is("trak")) {
                Object track = track(init, box);
                if (track instanceof Video v && video == null) video = v;
                if (track instanceof Audio a && audio == null) audio = a;
            } else if (box.is("mvex")) {
                for (Box trex : Box.children(init, box.contentStart(), box.end())) {
                    if (!trex.is("trex")) continue;
                    ByteBuffer b = trex.content(init);
                    b.getInt();
                    int trackId = b.getInt();
                    b.getInt();
                    long duration = Integer.toUnsignedLong(b.getInt());
                    long size = Integer.toUnsignedLong(b.getInt());
                    int flags = b.getInt();
                    defaults.put(trackId, new Defaults(duration, size, flags));
                }
            }
        }
        if (video == null && audio == null) throw new IOException("the init segment has neither a picture nor AAC");
        return new Fmp4Init(video, audio, Map.copyOf(defaults));
    }

    private static Object track(byte[] data, Box trak) throws IOException {
        Box tkhd = Box.find(data, trak.contentStart(), trak.end(), "tkhd");
        Box mdia = Box.find(data, trak.contentStart(), trak.end(), "mdia");
        if (tkhd == null || mdia == null) return null;
        ByteBuffer t = tkhd.content(data);
        int version = t.get() & 0xFF;
        t.position(version == 1 ? 20 : 12);
        int trackId = t.getInt();
        Box mdhd = Box.find(data, mdia.contentStart(), mdia.end(), "mdhd");
        Box minf = Box.find(data, mdia.contentStart(), mdia.end(), "minf");
        if (mdhd == null || minf == null) return null;
        ByteBuffer m = mdhd.content(data);
        int mdhdVersion = m.get() & 0xFF;
        m.position(mdhdVersion == 1 ? 20 : 12);
        long timescale = Integer.toUnsignedLong(m.getInt());
        if (timescale == 0) throw new IOException("track " + trackId + " has no timescale");
        Box stbl = Box.find(data, minf.contentStart(), minf.end(), "stbl");
        Box stsd = stbl == null ? null : Box.find(data, stbl.contentStart(), stbl.end(), "stsd");
        if (stsd == null) return null;
        List<Box> entries = Box.children(data, stsd.contentStart() + 8, stsd.end());
        if (entries.isEmpty()) return null;
        Box entry = entries.get(0);
        if (entry.is("avc1") || entry.is("avc3")) {
            Box avcC = Box.find(data, entry.contentStart() + 78, entry.end(), "avcC");
            return avcC == null ? null : video(data, avcC, trackId, timescale);
        }
        if (entry.is("hvc1") || entry.is("hev1")) {
            Box hvcC = Box.find(data, entry.contentStart() + 78, entry.end(), "hvcC");
            return hvcC == null ? null : hevcVideo(data, hvcC, trackId, timescale);
        }
        if (entry.is("mp4a")) {
            Box esds = Box.find(data, entry.contentStart() + 28, entry.end(), "esds");
            return esds == null ? null : audio(data, esds, trackId, timescale);
        }
        return null;
    }

    private static Video video(byte[] data, Box avcC, int trackId, long timescale) throws IOException {
        ByteBuffer b = avcC.content(data);
        b.get();
        b.get();
        b.get();
        b.get();
        int nalLengthSize = (b.get() & 0x03) + 1;
        List<byte[]> sps = parameterSets(b, b.get() & 0x1F);
        List<byte[]> pps = parameterSets(b, b.get() & 0xFF);
        if (sps.isEmpty() || pps.isEmpty()) throw new IOException("the H.264 track has no SPS or PPS");
        List<byte[]> sets = new ArrayList<>(sps);
        sets.addAll(pps);
        return new Video(trackId, timescale, false, List.copyOf(sets), nalLengthSize);
    }

    private static Video hevcVideo(byte[] data, Box hvcC, int trackId, long timescale) throws IOException {
        ByteBuffer b = hvcC.content(data);
        if (b.remaining() < 23) throw new IOException("hvcC cut short");
        b.position(21);
        int nalLengthSize = (b.get() & 0x03) + 1;
        int arrays = b.get() & 0xFF;
        TreeMap<Integer, List<byte[]>> byType = new TreeMap<>();
        for (int i = 0; i < arrays; i++) {
            if (b.remaining() < 3) throw new IOException("hvcC cut short");
            int type = b.get() & 0x3F;
            int count = b.getShort() & 0xFFFF;
            List<byte[]> nals = parameterSets(b, count);
            if (type >= 32 && type <= 34) byType.computeIfAbsent(type, t -> new ArrayList<>()).addAll(nals);
        }
        if (byType.size() < 3) throw new IOException("the HEVC track lacks a VPS, SPS or PPS");
        List<byte[]> sets = new ArrayList<>();
        byType.values().forEach(sets::addAll);
        return new Video(trackId, timescale, true, List.copyOf(sets), nalLengthSize);
    }

    private static List<byte[]> parameterSets(ByteBuffer b, int count) throws IOException {
        List<byte[]> sets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (b.remaining() < 2) throw new IOException("a parameter set runs past its box");
            int length = b.getShort() & 0xFFFF;
            if (b.remaining() < length) throw new IOException("a parameter set runs past its box");
            byte[] set = new byte[length];
            b.get(set);
            sets.add(set);
        }
        return sets;
    }

    private static Audio audio(byte[] data, Box esds, int trackId, long timescale) throws IOException {
        ByteBuffer b = esds.content(data);
        b.getInt();
        byte[] config = null;
        while (b.hasRemaining() && config == null) {
            int tag = b.get() & 0xFF;
            int length = descriptorLength(b);
            switch (tag) {
                case 0x03 -> {
                    b.getShort();
                    int flags = b.get() & 0xFF;
                    if ((flags & 0x80) != 0) b.getShort();
                    if ((flags & 0x40) != 0) b.position(b.position() + (b.get() & 0xFF));
                    if ((flags & 0x20) != 0) b.getShort();
                }
                case 0x04 -> b.position(b.position() + 13);
                case 0x05 -> {
                    if (length > b.remaining()) throw new IOException("esds cut short");
                    config = new byte[length];
                    b.get(config);
                }
                default -> b.position(Math.min(b.limit(), b.position() + length));
            }
        }
        if (config == null || config.length < 2) throw new IOException("the AAC track has no decoder configuration");
        BitReader bits = new BitReader(config);
        int objectType = objectType(bits);
        int frequencyIndex = bits.read(4);
        int explicitRate = frequencyIndex == 15 ? bits.read(24) : 0;
        int channels = bits.read(4);
        if (objectType == 5 || objectType == 29) {
            int extension = bits.read(4);
            if (extension == 15) bits.read(24);
            objectType = objectType(bits);
        }
        if (frequencyIndex == 15) frequencyIndex = nearestIndex(explicitRate);
        if (objectType < 1 || objectType > 4) objectType = 2;
        return new Audio(trackId, timescale, objectType - 1, frequencyIndex, channels);
    }

    private static int objectType(BitReader bits) throws IOException {
        int type = bits.read(5);
        return type == 31 ? 32 + bits.read(6) : type;
    }

    private static int nearestIndex(int rate) {
        int best = 3;
        for (int i = 0; i < SAMPLE_RATES.length; i++) {
            if (Math.abs(SAMPLE_RATES[i] - rate) < Math.abs(SAMPLE_RATES[best] - rate)) best = i;
        }
        return best;
    }

    private static int descriptorLength(ByteBuffer b) {
        int length = 0;
        for (int i = 0; i < 4 && b.hasRemaining(); i++) {
            int next = b.get() & 0xFF;
            length = (length << 7) | (next & 0x7F);
            if ((next & 0x80) == 0) break;
        }
        return length;
    }

    private static final class BitReader {
        private final byte[] data;
        private int bit;

        BitReader(byte[] data) {
            this.data = data;
        }

        int read(int count) throws IOException {
            int value = 0;
            for (int i = 0; i < count; i++) {
                if (bit >= data.length * 8) throw new IOException("the AAC decoder configuration is cut short");
                value = (value << 1) | ((data[bit >> 3] >> (7 - (bit & 7))) & 1);
                bit++;
            }
            return value;
        }
    }

    record Box(String type, int start, int headerSize, long size) {

        int contentStart() {
            return start + headerSize;
        }

        int end() {
            return (int) (start + size);
        }

        boolean is(String name) {
            return type.equals(name);
        }

        ByteBuffer content(byte[] data) {
            return ByteBuffer.wrap(data, contentStart(), end() - contentStart()).slice();
        }

        static List<Box> children(byte[] data, int from, int to) throws IOException {
            List<Box> boxes = new ArrayList<>();
            int at = from;
            while (at + 8 <= to) {
                Box box = at(data, at, to);
                boxes.add(box);
                at = box.end();
            }
            return boxes;
        }

        static Box find(byte[] data, int from, int to, String type) throws IOException {
            for (Box box : children(data, from, to)) {
                if (box.is(type)) return box;
            }
            return null;
        }

        static Box at(byte[] data, int at, int to) throws IOException {
            long size = Integer.toUnsignedLong(ByteBuffer.wrap(data, at, 4).getInt());
            String type = new String(data, at + 4, 4, StandardCharsets.ISO_8859_1);
            int header = 8;
            if (size == 1) {
                if (at + 16 > to) throw new IOException("box " + type + " cut short");
                size = ByteBuffer.wrap(data, at + 8, 8).getLong();
                header = 16;
            } else if (size == 0) {
                size = to - at;
            }
            if (size < header || at + size > to) throw new IOException("box " + type + " runs past its container");
            return new Box(type, at, header, size);
        }
    }
}
