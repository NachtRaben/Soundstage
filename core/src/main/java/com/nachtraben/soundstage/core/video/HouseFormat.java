package com.nachtraben.soundstage.core.video;

public final class HouseFormat {

    public static final int MAIN_PROFILE = 77;

    public static final long MAX_KEYFRAME_GAP_TICKS = 180_000;

    public static final long MIN_PICTURE_GAP_TICKS = (long) Math.ceil(90_000 / 30.5);

    private static final int NAL_SLICE = 1;
    private static final int NAL_IDR = 5;
    private static final int NAL_SPS = 7;

    private HouseFormat() {}

    public record Contents(int profile, boolean idr, boolean bSlice) {
        public static Contents of(byte[] annexB) {
            int[] found = {-1, 0, 0};
            int start = -1;
            int i = 0;
            while (i + 2 < annexB.length) {
                if (annexB[i] == 0 && annexB[i + 1] == 0 && annexB[i + 2] == 1) {
                    if (start >= 0) read(annexB, start, i, found);
                    start = i + 3;
                    i += 3;
                } else {
                    i++;
                }
            }
            if (start >= 0) read(annexB, start, annexB.length, found);
            return new Contents(found[0], found[1] != 0, found[2] != 0);
        }

        private static void read(byte[] annexB, int start, int end, int[] found) {
            if (start >= end) return;
            int type = annexB[start] & 0x1F;
            if (type == NAL_SPS && start + 1 < end) found[0] = annexB[start + 1] & 0xFF;
            if (type == NAL_IDR) found[1] = 1;
            if ((type == NAL_SLICE || type == NAL_IDR) && sliceType(annexB, start + 1, end) % 5 == 1) found[2] = 1;
        }
    }

    public static final class Judge {

        public static final long MAX_SOUND_WITHOUT_KEYFRAME_TICKS = 225_000;

        public static final long BREAK_TICKS = 90_000;

        public enum Verdict { PENDING, HOUSE, NOT_HOUSE }

        private static final long NONE = Long.MIN_VALUE;

        private int profile = -1;
        private long lastKeyframe = NONE;
        private long lastPts = NONE;
        private boolean groupClean;
        private long soundSinceKeyframe;
        private boolean anyKeyframe;
        private Verdict verdict = Verdict.PENDING;
        private String reason = "";

        public boolean conforms(VideoAccessUnit unit) {
            Contents contents = Contents.of(unit.annexB());
            if (contents.profile() >= 0) profile = contents.profile();
            long pts = unit.pts90k();
            if (lastPts != NONE && Math.abs(signed33(pts - lastPts)) > BREAK_TICKS) restart();
            if (lastKeyframe == NONE && !contents.idr()) return false;
            String broken = null;
            boolean late = false;
            if (profile != MAIN_PROFILE) {
                broken = profile < 0 ? "no SPS before its first picture" : "H.264 profile " + profile + ", not Main";
            } else if (contents.bSlice()) {
                broken = "B-frames";
            } else if (lastPts != NONE && signed33(pts - lastPts) < MIN_PICTURE_GAP_TICKS) {
                broken = signed33(pts - lastPts) < 0 ? "pictures out of order" : "over 30 pictures a second";
            } else if (lastKeyframe != NONE && signed33(pts - lastKeyframe) > MAX_KEYFRAME_GAP_TICKS) {
                broken = "keyframes over 2 s apart";
                late = contents.idr();
            }
            lastPts = pts;
            if (broken != null) fail(broken);
            if (contents.idr()) {
                if (broken == null && groupClean) pass();
                lastKeyframe = pts;
                soundSinceKeyframe = 0;
                anyKeyframe = true;
                groupClean = broken == null || late;
                return broken == null || late;
            }
            if (broken != null) groupClean = false;
            return broken == null;
        }

        public void heard(long durationTicks) {
            soundSinceKeyframe += Math.max(0, durationTicks);
            if (soundSinceKeyframe > MAX_SOUND_WITHOUT_KEYFRAME_TICKS) {
                fail(anyKeyframe ? "keyframes over 2 s apart" : "no H.264 picture with its sound");
            }
        }

        public void restart() {
            if (lastKeyframe != NONE && groupClean) pass();
            lastKeyframe = NONE;
            lastPts = NONE;
            groupClean = false;
        }

        public Verdict verdict() {
            return verdict;
        }

        public String reason() {
            return reason;
        }

        private void pass() {
            if (verdict == Verdict.PENDING) verdict = Verdict.HOUSE;
        }

        private void fail(String why) {
            if (verdict == Verdict.PENDING) {
                verdict = Verdict.NOT_HOUSE;
                reason = why;
            }
        }
    }

    static long signed33(long delta) {
        long wrap = 1L << 33;
        long d = delta & (wrap - 1);
        return d >= wrap / 2 ? d - wrap : d;
    }

    static int sliceType(byte[] data, int from, int to) {
        byte[] raw = new byte[Math.min(16, Math.max(0, to - from))];
        int length = 0;
        int zeros = 0;
        for (int i = from; i < to && length < raw.length; i++) {
            int b = data[i] & 0xFF;
            if (zeros >= 2 && b == 3) {
                zeros = 0;
                continue;
            }
            raw[length++] = (byte) b;
            zeros = b == 0 ? zeros + 1 : 0;
        }
        int[] bit = {0};
        if (readExpGolomb(raw, length, bit) < 0) return -1;
        return readExpGolomb(raw, length, bit);
    }

    private static int readExpGolomb(byte[] raw, int length, int[] bit) {
        int zeros = 0;
        while (true) {
            if (bit[0] >= length * 8) return -1;
            int b = (raw[bit[0] >> 3] >> (7 - (bit[0] & 7))) & 1;
            bit[0]++;
            if (b == 1) break;
            if (++zeros > 30) return -1;
        }
        int value = 0;
        for (int i = 0; i < zeros; i++) {
            if (bit[0] >= length * 8) return -1;
            value = value << 1 | ((raw[bit[0] >> 3] >> (7 - (bit[0] & 7))) & 1);
            bit[0]++;
        }
        return (1 << zeros) - 1 + value;
    }
}
