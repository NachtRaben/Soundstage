package com.nachtraben.soundstage.core.video;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Fmp4ToTs {

    static final int MAX_BOX_BYTES = 16 << 20;

    static final int PACKET_SIZE = 188;
    static final int PMT_PID = 0x1000;
    static final int VIDEO_PID = 0x100;
    static final int AUDIO_PID = 0x101;

    private static final byte[] AUD = {0, 0, 0, 1, 0x09, (byte) 0xF0};
    private static final byte[] START_CODE = {0, 0, 0, 1};
    private static final int NAL_IDR = 5;
    private static final int NAL_SPS = 7;

    private int patCounter;
    private int pmtCounter;
    private int videoCounter;
    private int audioCounter;

    public InputStream convert(Fmp4Init init, InputStream segment) {
        return new Converted(init, segment);
    }

    private record Sample(boolean video, int offset, int size, long dts90, long pts90) {
    }

    private final class Converted extends InputStream {
        private final Fmp4Init init;
        private final InputStream in;
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
        private byte[] buffer = new byte[0];
        private int position;
        private boolean tablesWritten;
        private boolean ended;
        private byte[] moof;
        private long afterMoof;

        Converted(Fmp4Init init, InputStream in) {
            this.init = init;
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            if (!fill()) return -1;
            return buffer[position++] & 0xFF;
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            if (length == 0) return 0;
            if (!fill()) return -1;
            int n = Math.min(length, buffer.length - position);
            System.arraycopy(buffer, position, target, offset, n);
            position += n;
            return n;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }

        private boolean fill() throws IOException {
            while (position >= buffer.length) {
                if (ended) return false;
                pending.reset();
                if (!tablesWritten) {
                    tablesWritten = true;
                    writeTables(pending);
                }
                step();
                buffer = pending.toByteArray();
                position = 0;
            }
            return true;
        }

        private void step() throws IOException {
            byte[] header = in.readNBytes(8);
            if (header.length == 0) {
                ended = true;
                return;
            }
            if (header.length < 8) throw new EOFException("fragmented MP4 cut short in a box header");
            long size = Integer.toUnsignedLong(ByteBuffer.wrap(header).getInt());
            String type = new String(header, 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            int headerSize = 8;
            if (size == 1) {
                byte[] large = in.readNBytes(8);
                if (large.length < 8) throw new EOFException("fragmented MP4 cut short in a box header");
                size = ByteBuffer.wrap(large).getLong();
                headerSize = 16;
            }
            if (size == 0 || size < headerSize) throw new IOException("box " + type + " has no usable size");
            long contentSize = size - headerSize;
            boolean keep = type.equals("moof") || type.equals("mdat");
            if (!keep) {
                skipBytes(contentSize);
                if (moof != null) afterMoof += size;
                return;
            }
            if (size > MAX_BOX_BYTES) throw new IOException("box " + type + " is " + size + " bytes");
            if (type.equals("moof") && headerSize == 16) throw new IOException("a moof with a 64-bit size");
            byte[] content = in.readNBytes((int) contentSize);
            if (content.length < contentSize) throw new EOFException("fragmented MP4 cut short in " + type);
            if (type.equals("moof")) {
                moof = new byte[(int) size];
                System.arraycopy(header, 0, moof, 0, 8);
                System.arraycopy(content, 0, moof, 8, content.length);
                afterMoof = 0;
            } else if (moof != null) {
                try {
                    convertChunk(moof, content, (int) (afterMoof + headerSize));
                } catch (BufferUnderflowException | IndexOutOfBoundsException e) {
                    throw new IOException("a malformed fragment", e);
                }
                moof = null;
            }
        }

        private void skipBytes(long count) throws IOException {
            while (count > 0) {
                long n = in.skip(count);
                if (n <= 0) {
                    if (in.read() < 0) throw new EOFException("fragmented MP4 cut short");
                    n = 1;
                }
                count -= n;
            }
        }

        private void convertChunk(byte[] moof, byte[] mdat, int mdatHeader) throws IOException {
            int mdatStart = moof.length + mdatHeader;
            List<Sample> samples = new ArrayList<>();
            int previousEnd = -1;
            for (Fmp4Init.Box traf : Fmp4Init.Box.children(moof, 8, moof.length)) {
                if (!traf.is("traf")) continue;
                previousEnd = trackFragment(moof, traf, samples, previousEnd);
            }
            samples.sort(Comparator.comparingLong(Sample::dts90));
            ByteArrayOutputStream audio = new ByteArrayOutputStream();
            long audioPts = -1;
            List<Sample> pictures = new ArrayList<>();
            for (Sample sample : samples) {
                int at = sample.offset() - mdatStart;
                if (at < 0 || at + sample.size() > mdat.length) throw new IOException("a sample outside its mdat");
                if (sample.video()) {
                    pictures.add(sample);
                } else {
                    if (audioPts < 0) audioPts = sample.pts90();
                    Fmp4Init.Audio a = init.audio();
                    int length = sample.size() + 7;
                    audio.write(0xFF);
                    audio.write(0xF1);
                    audio.write((a.profile() << 6) | (a.frequencyIndex() << 2) | ((a.channels() >> 2) & 1));
                    audio.write(((a.channels() & 3) << 6) | ((length >> 11) & 3));
                    audio.write((length >> 3) & 0xFF);
                    audio.write(((length & 7) << 5) | 0x1F);
                    audio.write(0xFC);
                    audio.write(mdat, at, sample.size());
                }
            }
            if (audio.size() > 0) {
                writePes(AUDIO_PID, 0xC0, audioPts, -1, audio.toByteArray(), init.video() == null, false);
            }
            for (Sample picture : pictures) writeVideo(mdat, picture.offset() - mdatStart, picture);
        }

        private int trackFragment(byte[] moof, Fmp4Init.Box traf, List<Sample> samples, int previousEnd)
                throws IOException {
            int trackId = -1;
            int tfhdFlags = 0;
            long baseOffset = -1;
            long defaultDuration = -1;
            long defaultSize = -1;
            int defaultFlags = 0;
            long decodeTime = 0;
            List<Fmp4Init.Box> runs = new ArrayList<>();
            for (Fmp4Init.Box box : Fmp4Init.Box.children(moof, traf.contentStart(), traf.end())) {
                ByteBuffer b = box.content(moof);
                if (box.is("tfhd")) {
                    tfhdFlags = b.getInt() & 0xFFFFFF;
                    trackId = b.getInt();
                    if ((tfhdFlags & 0x01) != 0) baseOffset = b.getLong();
                    if ((tfhdFlags & 0x02) != 0) b.getInt();
                    if ((tfhdFlags & 0x08) != 0) defaultDuration = Integer.toUnsignedLong(b.getInt());
                    if ((tfhdFlags & 0x10) != 0) defaultSize = Integer.toUnsignedLong(b.getInt());
                    if ((tfhdFlags & 0x20) != 0) defaultFlags = b.getInt();
                } else if (box.is("tfdt")) {
                    int version = b.get() & 0xFF;
                    b.position(4);
                    decodeTime = version == 1 ? b.getLong() : Integer.toUnsignedLong(b.getInt());
                } else if (box.is("trun")) {
                    runs.add(box);
                }
            }
            Fmp4Init.Defaults trex = init.defaults().get(trackId);
            if (defaultDuration < 0) defaultDuration = trex == null ? 0 : trex.duration();
            if (defaultSize < 0) defaultSize = trex == null ? 0 : trex.size();
            if ((tfhdFlags & 0x20) == 0 && trex != null) defaultFlags = trex.flags();
            boolean video = init.video() != null && init.video().trackId() == trackId;
            boolean audio = init.audio() != null && init.audio().trackId() == trackId;
            long timescale = video ? init.video().timescale() : audio ? init.audio().timescale() : 1;
            int base;
            if (baseOffset >= 0) {
                throw new IOException("a fragment gives an absolute data offset");
            } else if ((tfhdFlags & 0x020000) != 0 || previousEnd < 0) {
                base = 0;
            } else {
                base = previousEnd;
            }
            int dataEnd = base;
            for (Fmp4Init.Box trun : runs) {
                ByteBuffer b = trun.content(moof);
                int versionAndFlags = b.getInt();
                int version = versionAndFlags >>> 24;
                int flags = versionAndFlags & 0xFFFFFF;
                int count = b.getInt();
                if (count < 0 || count > 100_000) throw new IOException("a run of " + count + " samples");
                int offset = (flags & 0x01) != 0 ? base + b.getInt() : dataEnd;
                if ((flags & 0x04) != 0) b.getInt();
                for (int i = 0; i < count; i++) {
                    long duration = (flags & 0x100) != 0 ? Integer.toUnsignedLong(b.getInt()) : defaultDuration;
                    long size = (flags & 0x200) != 0 ? Integer.toUnsignedLong(b.getInt()) : defaultSize;
                    if ((flags & 0x400) != 0) b.getInt();
                    long composition = 0;
                    if ((flags & 0x800) != 0) composition = version == 0 ? Integer.toUnsignedLong(b.getInt()) : b.getInt();
                    if (size > MAX_BOX_BYTES) throw new IOException("a sample of " + size + " bytes");
                    if (video || audio) {
                        long dts90 = ticks90(decodeTime, timescale);
                        long pts90 = ticks90(decodeTime + composition, timescale);
                        samples.add(new Sample(video, offset, (int) size, dts90, pts90));
                    }
                    decodeTime += duration;
                    offset += (int) size;
                }
                dataEnd = offset;
            }
            return dataEnd;
        }

        private void writeVideo(byte[] mdat, int at, Sample sample) throws IOException {
            Fmp4Init.Video v = init.video();
            ByteArrayOutputStream unit = new ByteArrayOutputStream(sample.size() + 64);
            unit.write(AUD);
            boolean keyframe = false;
            boolean hasSps = false;
            List<int[]> nals = new ArrayList<>();
            int end = at + sample.size();
            for (int p = at; p < end; ) {
                if (p + v.nalLengthSize() > end) throw new IOException("a NAL size runs past its sample");
                int length = 0;
                for (int i = 0; i < v.nalLengthSize(); i++) length = (length << 8) | (mdat[p + i] & 0xFF);
                p += v.nalLengthSize();
                if (length < 0 || p + length > end) throw new IOException("a NAL runs past its sample");
                if (length > 0) {
                    int type = mdat[p] & 0x1F;
                    if (type == NAL_IDR) keyframe = true;
                    if (type == NAL_SPS) hasSps = true;
                    nals.add(new int[] {p, length});
                }
                p += length;
            }
            if (keyframe && !hasSps) {
                for (byte[] sps : v.sps()) {
                    unit.write(START_CODE);
                    unit.write(sps);
                }
                for (byte[] pps : v.pps()) {
                    unit.write(START_CODE);
                    unit.write(pps);
                }
            }
            for (int[] nal : nals) {
                int type = mdat[nal[0]] & 0x1F;
                if (type == 9) continue;
                unit.write(START_CODE);
                unit.write(mdat, nal[0], nal[1]);
            }
            long dts = sample.dts90() == sample.pts90() ? -1 : sample.dts90();
            writePes(VIDEO_PID, 0xE0, sample.pts90(), dts, unit.toByteArray(), true, keyframe);
        }

        private void writeTables(ByteArrayOutputStream out) {
            byte[] pat = {0x00, (byte) 0xB0, 0x0D, 0x00, 0x01, (byte) 0xC1, 0x00, 0x00,
                    0x00, 0x01, (byte) (0xE0 | (PMT_PID >> 8)), (byte) PMT_PID};
            writeSection(out, 0, pat, patCounter++);
            ByteArrayOutputStream streams = new ByteArrayOutputStream();
            if (init.video() != null) streamEntry(streams, 0x1B, VIDEO_PID);
            if (init.audio() != null) streamEntry(streams, 0x0F, AUDIO_PID);
            int pcrPid = init.video() != null ? VIDEO_PID : AUDIO_PID;
            byte[] entries = streams.toByteArray();
            int sectionLength = 9 + entries.length + 4;
            ByteArrayOutputStream pmt = new ByteArrayOutputStream();
            pmt.write(0x02);
            pmt.write(0xB0 | (sectionLength >> 8));
            pmt.write(sectionLength & 0xFF);
            pmt.write(0x00);
            pmt.write(0x01);
            pmt.write(0xC1);
            pmt.write(0x00);
            pmt.write(0x00);
            pmt.write(0xE0 | (pcrPid >> 8));
            pmt.write(pcrPid & 0xFF);
            pmt.write(0xF0);
            pmt.write(0x00);
            pmt.write(entries, 0, entries.length);
            writeSection(out, PMT_PID, pmt.toByteArray(), pmtCounter++);
            patCounter &= 0x0F;
            pmtCounter &= 0x0F;
        }

        private void streamEntry(ByteArrayOutputStream out, int type, int pid) {
            out.write(type);
            out.write(0xE0 | (pid >> 8));
            out.write(pid & 0xFF);
            out.write(0xF0);
            out.write(0x00);
        }

        private void writeSection(ByteArrayOutputStream out, int pid, byte[] section, int counter) {
            byte[] packet = new byte[PACKET_SIZE];
            java.util.Arrays.fill(packet, (byte) 0xFF);
            packet[0] = 0x47;
            packet[1] = (byte) (0x40 | (pid >> 8));
            packet[2] = (byte) pid;
            packet[3] = (byte) (0x10 | (counter & 0x0F));
            packet[4] = 0x00;
            System.arraycopy(section, 0, packet, 5, section.length);
            int crc = crc32(section);
            int at = 5 + section.length;
            packet[at] = (byte) (crc >>> 24);
            packet[at + 1] = (byte) (crc >>> 16);
            packet[at + 2] = (byte) (crc >>> 8);
            packet[at + 3] = (byte) crc;
            out.write(packet, 0, PACKET_SIZE);
        }

        private void writePes(int pid, int streamId, long pts, long dts, byte[] payload, boolean clock,
                              boolean randomAccess) {
            int headerData = dts >= 0 ? 10 : 5;
            ByteArrayOutputStream pes = new ByteArrayOutputStream(payload.length + 19);
            pes.write(0);
            pes.write(0);
            pes.write(1);
            pes.write(streamId);
            int length = 3 + headerData + payload.length;
            int field = pid == VIDEO_PID || length > 0xFFFF ? 0 : length;
            pes.write(field >> 8);
            pes.write(field & 0xFF);
            pes.write(0x84);
            pes.write(dts >= 0 ? 0xC0 : 0x80);
            pes.write(headerData);
            timestamp(pes, dts >= 0 ? 0x3 : 0x2, pts);
            if (dts >= 0) timestamp(pes, 0x1, dts);
            pes.write(payload, 0, payload.length);
            byte[] bytes = pes.toByteArray();

            int at = 0;
            boolean first = true;
            while (at < bytes.length) {
                byte[] packet = new byte[PACKET_SIZE];
                packet[0] = 0x47;
                packet[1] = (byte) ((first ? 0x40 : 0) | (pid >> 8));
                packet[2] = (byte) pid;
                int counter = pid == VIDEO_PID ? videoCounter++ : audioCounter++;
                videoCounter &= 0x0F;
                audioCounter &= 0x0F;
                boolean pcr = first && clock;
                int adaptation = pcr ? 8 : (first && randomAccess ? 2 : 0);
                int room = PACKET_SIZE - 4 - adaptation;
                int take = Math.min(room, bytes.length - at);
                int stuffing = room - take;
                if (stuffing > 0 && adaptation == 0) {
                    adaptation = 1;
                    stuffing -= 1;
                    if (stuffing > 0) {
                        adaptation = 2;
                        stuffing -= 1;
                    }
                }
                int p = 4;
                if (adaptation > 0) {
                    packet[3] = (byte) (0x30 | (counter & 0x0F));
                    int fieldLength = adaptation - 1 + stuffing;
                    packet[p++] = (byte) fieldLength;
                    if (fieldLength > 0) {
                        int flags = (first && randomAccess ? 0x40 : 0) | (pcr ? 0x10 : 0);
                        packet[p++] = (byte) flags;
                        if (pcr) {
                            long base = (dts >= 0 ? dts : pts) & 0x1FFFFFFFFL;
                            packet[p++] = (byte) (base >>> 25);
                            packet[p++] = (byte) (base >>> 17);
                            packet[p++] = (byte) (base >>> 9);
                            packet[p++] = (byte) (base >>> 1);
                            packet[p++] = (byte) (((base & 1) << 7) | 0x7E);
                            packet[p++] = 0x00;
                        }
                        for (int i = 0; i < stuffing; i++) packet[p++] = (byte) 0xFF;
                    }
                } else {
                    packet[3] = (byte) (0x10 | (counter & 0x0F));
                }
                System.arraycopy(bytes, at, packet, p, take);
                at += take;
                pending.write(packet, 0, PACKET_SIZE);
                first = false;
            }
        }
    }

    static long ticks90(long time, long timescale) {
        long ticks;
        if (timescale == 90_000) {
            ticks = time;
        } else if (Math.abs(time) < Long.MAX_VALUE / 90_000) {
            ticks = Math.floorDiv(time * 90_000, timescale);
        } else {
            ticks = (long) Math.floor((double) time * 90_000 / timescale);
        }
        return ticks & 0x1FFFFFFFFL;
    }

    private static void timestamp(ByteArrayOutputStream out, int prefix, long ticks) {
        long t = ticks & 0x1FFFFFFFFL;
        out.write((int) ((prefix << 4) | ((t >>> 29) & 0x0E) | 1));
        out.write((int) (t >>> 22) & 0xFF);
        out.write((int) (((t >>> 14) & 0xFE) | 1));
        out.write((int) (t >>> 7) & 0xFF);
        out.write((int) (((t << 1) & 0xFE) | 1));
    }

    static int crc32(byte[] data) {
        int crc = 0xFFFFFFFF;
        for (byte b : data) {
            crc ^= (b & 0xFF) << 24;
            for (int i = 0; i < 8; i++) crc = (crc & 0x80000000) != 0 ? (crc << 1) ^ 0x04C11DB7 : crc << 1;
        }
        return crc;
    }
}
