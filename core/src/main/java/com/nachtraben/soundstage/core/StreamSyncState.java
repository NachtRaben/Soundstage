package com.nachtraben.soundstage.core;

public final class StreamSyncState {

    public static final long DESYNC_THRESHOLD_MILLIS = 600;

    public static final long PUMP_GRANULARITY_FRAMES = 5;

    public static final long SIBLING_DRIFT_TOLERANCE_FRAMES = 8;

    public static final long DRIFT_CONFIRM_MILLIS = 1000;

    public static final int PREROLL_FRAMES = 25;

    public enum Gap {
        END_OF_STREAM,
        PAD
    }

    private long nextSequence = -1;
    private long lastRealDataMillis;
    private long behindSinceMillis = -1;

    private int appliedDelayFrames = 0;

    private int pendingSilenceFrames = 0;

    public boolean consumeSilenceFrame() {
        if (pendingSilenceFrames <= 0) return false;
        pendingSilenceFrames--;
        return true;
    }

    public int pendingSilenceFrames() {
        return pendingSilenceFrames;
    }

    public int realisedDelayFrames() {
        return appliedDelayFrames - pendingSilenceFrames;
    }

    public void beginPump(long referenceHead, long lowestSequence, long highestSequence,
                          int delayFrames, long nowMillis) {
        beginPump(referenceHead, -1, lowestSequence, highestSequence, delayFrames, nowMillis);
    }

    public void beginPump(long referenceHead, long groupStart, long lowestSequence,
                          long highestSequence, int delayFrames, long nowMillis) {
        beginPump(referenceHead, groupStart, lowestSequence, highestSequence, delayFrames, 0, nowMillis);
    }

    public void beginPump(long referenceHead, long groupStart, long lowestSequence,
                          long highestSequence, int delayFrames, int latenessFrames, long nowMillis) {
        long entrySequence = nextSequence;
        if (nextSequence == -1) {
            appliedDelayFrames = delayFrames;
            long anchor = groupStart != -1 ? groupStart : referenceHead;
            if (anchor != -1) {
                nextSequence = anchor - delayFrames;
                anchorSeq = nextSequence;
                lastRealDataMillis = nowMillis;
            }
        } else if (delayFrames > appliedDelayFrames) {
            pendingSilenceFrames += (delayFrames - appliedDelayFrames);
            appliedDelayFrames = delayFrames;
            behindSinceMillis = -1;
        } else if (delayFrames < appliedDelayFrames) {
            int reduction = appliedDelayFrames - delayFrames;
            int cancelled = Math.min(pendingSilenceFrames, reduction);
            pendingSilenceFrames -= cancelled;
            delayStepDelta += (reduction - cancelled);
            nextSequence += (reduction - cancelled);
            appliedDelayFrames = delayFrames;
            behindSinceMillis = -1;
        }

        if (lowestSequence != -1 && nextSequence < lowestSequence) {
            floorClampDelta += lowestSequence - nextSequence;
            if (entrySequence != -1) {
                fellOffRingCount++;
            }
            nextSequence = lowestSequence;
        }

        if (referenceHead == -1) {
            behindSinceMillis = -1;
            return;
        }

        if (highestSequence != -1 && referenceHead > highestSequence) {
            referenceHead = highestSequence;
        }

        long target = referenceHead - delayFrames;
        if (nextSequence < target - SIBLING_DRIFT_TOLERANCE_FRAMES - Math.max(0, latenessFrames)) {
            if (behindSinceMillis == -1) {
                behindSinceMillis = nowMillis;
            } else if (nowMillis - behindSinceMillis > DRIFT_CONFIRM_MILLIS) {
                recordReposition(nextSequence, target);
                nextSequence = target;
                behindSinceMillis = -1;
            }
        } else {
            behindSinceMillis = -1;
        }
    }

    private long repositionCount;
    private long lastRepositionFrom, lastRepositionTo;

    private long fellOffRingCount;

    public long fellOffRingCount() { return fellOffRingCount; }

    private void recordReposition(long from, long to) {
        repositionCount++;
        repositionDelta += to - from;
        lastRepositionFrom = from;
        lastRepositionTo = to;
    }

    private long realConsumed;
    private long padsConsumed;
    private long anchorSeq = Long.MIN_VALUE;
    private long repositionDelta;
    private long delayStepDelta;
    private long floorClampDelta;

    public long realConsumed() { return realConsumed; }
    public long padsConsumed() { return padsConsumed; }
    public long anchorSeq() { return anchorSeq; }

    public long unaccounted() {
        if (anchorSeq == Long.MIN_VALUE) return 0;
        return nextSequence - (anchorSeq + realConsumed + padsConsumed
                + repositionDelta + delayStepDelta + floorClampDelta);
    }

    public long repositionCount() { return repositionCount; }
    public long lastRepositionFrom() { return lastRepositionFrom; }
    public long lastRepositionTo() { return lastRepositionTo; }

    public long nextSequence() {
        return nextSequence;
    }

    public void frameConsumed(long nowMillis) {
        nextSequence++;
        realConsumed++;
        lastRealDataMillis = nowMillis;
    }

    public static final long PAD_SLOT_FRESH_MILLIS = 250;

    public boolean padConsumesSlot(long nowMillis, long lastPacketMillis) {
        if (nowMillis - lastPacketMillis <= PAD_SLOT_FRESH_MILLIS) {
            nextSequence++;
            padsConsumed++;
            return true;
        }
        return false;
    }

    public static boolean notYetProduced(long wanted, long highestSequence) {
        return highestSequence != -1 && wanted > highestSequence;
    }

    public Gap frameMissing(long nowMillis, long lastPacketMillis, long resyncTarget) {
        return frameMissing(nowMillis, lastPacketMillis, resyncTarget, END_OF_STREAM_MILLIS);
    }

    public static final long END_OF_STREAM_MILLIS = 1500;

    public Gap frameMissing(long nowMillis, long lastPacketMillis, long resyncTarget,
                            long endOfStreamMillis) {
        if (nowMillis - lastPacketMillis > endOfStreamMillis) {
            return Gap.END_OF_STREAM;
        }

        boolean packetsStillArriving = nowMillis - lastPacketMillis <= DESYNC_THRESHOLD_MILLIS;
        if (packetsStillArriving && nowMillis - lastRealDataMillis > DESYNC_THRESHOLD_MILLIS) {
            if (resyncTarget != -1 && resyncTarget != nextSequence) {
                recordReposition(nextSequence, resyncTarget);
                nextSequence = resyncTarget;
            }
            lastRealDataMillis = nowMillis;
        }
        return Gap.PAD;
    }

}
