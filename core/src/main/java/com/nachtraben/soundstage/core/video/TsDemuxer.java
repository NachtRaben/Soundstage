package com.nachtraben.soundstage.core.video;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public final class TsDemuxer {

    public interface Listener {
        void onVideo(VideoAccessUnit unit);

        void onAudio(long pts90k, int adtsFrameCount);

        default void onAudio(long pts90k, int adtsFrameCount, int sampleRate, int configuration) {
            onAudio(pts90k, adtsFrameCount);
        }

        default void onSecondAudio(long pts90k, byte[] adts) {
        }
    }

    private static final int[] ADTS_SAMPLE_RATES = {
            96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350};

    private static final int PACKET_SIZE = 188;
    private static final int SYNC_BYTE = 0x47;
    private static final int PID_PAT = 0x0000;
    private static final int TABLE_ID_PAT = 0x00;
    private static final int TABLE_ID_PMT = 0x02;
    private static final int STREAM_TYPE_H264 = 0x1B;
    private static final int STREAM_TYPE_AAC_ADTS = 0x0F;
    private static final int NAL_TYPE_IDR = 5;

    private final Listener listener;

    private final byte[] packet = new byte[PACKET_SIZE];
    private int packetLength;

    private int pmtPid = -1;
    private int videoPid = -1;
    private int audioPid = -1;
    private int secondAudioPid = -1;

    private final PesAccumulator videoPes = new PesAccumulator();
    private final PesAccumulator audioPes = new PesAccumulator();
    private final PesAccumulator secondAudioPes = new PesAccumulator();

    public TsDemuxer(Listener listener) {
        this.listener = listener;
    }

    public void accept(byte[] buffer, int offset, int length) {
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            byte b = buffer[i];
            if (packetLength == 0 && (b & 0xff) != SYNC_BYTE) {
                continue;
            }
            packet[packetLength++] = b;
            if (packetLength == PACKET_SIZE) {
                processPacket();
                packetLength = 0;
            }
        }
    }

    public void flush() {
        if (videoPes.active()) finishVideoPes(videoPes.takeAndReset());
        if (audioPes.active()) finishAudioPes(audioPes.takeAndReset());
        if (secondAudioPes.active()) finishSecondAudioPes(secondAudioPes.takeAndReset());
    }

    private void processPacket() {
        int b1 = packet[1] & 0xff;
        int b2 = packet[2] & 0xff;
        int b3 = packet[3] & 0xff;
        boolean pusi = (b1 & 0x40) != 0;
        int pid = ((b1 & 0x1f) << 8) | b2;
        int afc = (b3 >> 4) & 3;
        if (afc == 2) return;

        int payloadStart = (afc == 2 || afc == 3) ? 5 + (packet[4] & 0xff) : 4;
        if (payloadStart >= PACKET_SIZE) return;

        if (pid == PID_PAT) {
            handlePat(payloadStart, pusi);
        } else if (pid == pmtPid) {
            handlePmt(payloadStart, pusi);
        } else if (pid == videoPid) {
            handlePes(videoPes, payloadStart, pusi, true);
        } else if (pid == audioPid) {
            handlePes(audioPes, payloadStart, pusi, false);
        } else if (pid == secondAudioPid) {
            if (pusi && secondAudioPes.active()) finishSecondAudioPes(secondAudioPes.takeAndReset());
            if (pusi) secondAudioPes.start();
            if (secondAudioPes.active()) secondAudioPes.append(packet, payloadStart, PACKET_SIZE - payloadStart);
        }
    }

    private void handlePat(int payloadStart, boolean pusi) {
        if (!pusi) return;
        int i = payloadStart + 1 + (packet[payloadStart] & 0xff);
        if (i + 8 > PACKET_SIZE) return;
        if ((packet[i] & 0xff) != TABLE_ID_PAT) return;
        int sectionLength = ((packet[i + 1] & 0x0f) << 8) | (packet[i + 2] & 0xff);
        int entriesEnd = Math.min(i + 3 + sectionLength - 4, PACKET_SIZE);
        for (int e = i + 8; e + 4 <= entriesEnd; e += 4) {
            int programNumber = ((packet[e] & 0xff) << 8) | (packet[e + 1] & 0xff);
            if (programNumber != 0) {
                pmtPid = ((packet[e + 2] & 0x1f) << 8) | (packet[e + 3] & 0xff);
                return;
            }
        }
    }

    private void handlePmt(int payloadStart, boolean pusi) {
        if (!pusi) return;
        int i = payloadStart + 1 + (packet[payloadStart] & 0xff);
        if (i + 12 > PACKET_SIZE) return;
        if ((packet[i] & 0xff) != TABLE_ID_PMT) return;
        int sectionLength = ((packet[i + 1] & 0x0f) << 8) | (packet[i + 2] & 0xff);
        int entriesEnd = Math.min(i + 3 + sectionLength - 4, PACKET_SIZE);
        int programInfoLength = ((packet[i + 10] & 0x0f) << 8) | (packet[i + 11] & 0xff);
        int pos = i + 12 + programInfoLength;
        while (pos + 5 <= entriesEnd) {
            int streamType = packet[pos] & 0xff;
            int elementaryPid = ((packet[pos + 1] & 0x1f) << 8) | (packet[pos + 2] & 0xff);
            int esInfoLength = ((packet[pos + 3] & 0x0f) << 8) | (packet[pos + 4] & 0xff);
            if (streamType == STREAM_TYPE_H264 && videoPid < 0) {
                videoPid = elementaryPid;
            } else if (streamType == STREAM_TYPE_AAC_ADTS && audioPid < 0) {
                audioPid = elementaryPid;
            } else if (streamType == STREAM_TYPE_AAC_ADTS && secondAudioPid < 0 && elementaryPid != audioPid) {
                secondAudioPid = elementaryPid;
            }
            pos += 5 + esInfoLength;
        }
    }

    private void handlePes(PesAccumulator pes, int payloadStart, boolean pusi, boolean isVideo) {
        if (pusi) {
            if (pes.active()) {
                byte[] complete = pes.takeAndReset();
                if (isVideo) {
                    finishVideoPes(complete);
                } else {
                    finishAudioPes(complete);
                }
            }
            pes.start();
        }
        if (pes.active()) {
            pes.append(packet, payloadStart, PACKET_SIZE - payloadStart);
        }
    }

    private void finishVideoPes(byte[] pes) {
        Timestamps ts = readTimestamps(pes);
        if (ts == null) return;
        int esStart = Math.min(9 + (pes[8] & 0xff), pes.length);
        byte[] annexB = Arrays.copyOfRange(pes, esStart, pes.length);
        listener.onVideo(new VideoAccessUnit(ts.pts, ts.dts, containsNal(annexB, NAL_TYPE_IDR), annexB));
    }

    private void finishAudioPes(byte[] pes) {
        Timestamps ts = readTimestamps(pes);
        if (ts == null) return;
        int esStart = Math.min(9 + (pes[8] & 0xff), pes.length);
        int configuration = -1;
        int sampleRate = 0;
        if (esStart + 4 <= pes.length && (pes[esStart] & 0xff) == 0xFF && (pes[esStart + 1] & 0xF0) == 0xF0) {
            int profile = (pes[esStart + 2] & 0xff) >> 6;
            int frequencyIndex = (pes[esStart + 2] >> 2) & 0x0f;
            int channels = ((pes[esStart + 2] & 1) << 2) | ((pes[esStart + 3] & 0xff) >> 6);
            configuration = profile << 8 | frequencyIndex << 4 | channels;
            if (frequencyIndex < ADTS_SAMPLE_RATES.length) sampleRate = ADTS_SAMPLE_RATES[frequencyIndex];
        }
        listener.onAudio(ts.pts, countAdtsFrames(pes, esStart, pes.length), sampleRate, configuration);
    }

    private void finishSecondAudioPes(byte[] pes) {
        Timestamps ts = readTimestamps(pes);
        if (ts == null) return;
        int esStart = Math.min(9 + (pes[8] & 0xff), pes.length);
        listener.onSecondAudio(ts.pts, Arrays.copyOfRange(pes, esStart, pes.length));
    }

    private record Timestamps(long pts, long dts) {
    }

    private static Timestamps readTimestamps(byte[] pes) {
        if (pes.length < 9) return null;
        if (pes[0] != 0 || pes[1] != 0 || pes[2] != 1) return null;
        int flags2 = pes[7] & 0xff;
        boolean hasPts = (flags2 & 0x80) != 0;
        boolean hasDts = (flags2 & 0xC0) == 0xC0;
        long pts = 0;
        long dts = 0;
        if (hasPts && pes.length >= 14) {
            pts = readTimestamp(pes, 9);
            dts = pts;
            if (hasDts && pes.length >= 19) {
                dts = readTimestamp(pes, 14);
            }
        }
        return new Timestamps(pts, dts);
    }

    private static long readTimestamp(byte[] b, int off) {
        long b0 = b[off] & 0xff;
        long b1 = b[off + 1] & 0xff;
        long b2 = b[off + 2] & 0xff;
        long b3 = b[off + 3] & 0xff;
        long b4 = b[off + 4] & 0xff;
        return ((b0 >> 1) & 7) << 30 | b1 << 22 | (b2 >> 1) << 15 | b3 << 7 | b4 >> 1;
    }

    private static boolean containsNal(byte[] annexB, int nalType) {
        for (int i = 0; i + 3 < annexB.length; i++) {
            if (annexB[i] == 0 && annexB[i + 1] == 0 && annexB[i + 2] == 1) {
                if ((annexB[i + 3] & 0x1f) == nalType) return true;
                i += 2;
            }
        }
        return false;
    }

    private static int countAdtsFrames(byte[] es, int start, int end) {
        int count = 0;
        int i = start;
        while (i + 6 <= end) {
            if ((es[i] & 0xff) != 0xFF || (es[i + 1] & 0xF0) != 0xF0) break;
            int length = ((es[i + 3] & 3) << 11) | ((es[i + 4] & 0xff) << 3) | ((es[i + 5] & 0xff) >> 5);
            if (length < 7) break;
            count++;
            i += length;
        }
        return count;
    }

    private static final class PesAccumulator {
        private ByteArrayOutputStream data;

        boolean active() {
            return data != null;
        }

        void start() {
            data = new ByteArrayOutputStream();
        }

        void append(byte[] src, int offset, int length) {
            data.write(src, offset, length);
        }

        byte[] takeAndReset() {
            byte[] result = data.toByteArray();
            data = null;
            return result;
        }
    }
}
