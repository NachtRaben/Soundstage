package com.nachtraben.soundstage.core.video;

public final class PictureAdmission {

    public static final long SETTLE_NANOS = 3_000_000_000L;

    public static final int WITHOUT_MEASURE = 4;

    public enum Kind {
        ADMITTED,
        CEILING,
        SETTLING,
        COPY_BEHIND,
        BUSY,
        NO_MEASURE
    }

    public record Answer(Kind kind, CpuPressure.Reading reading) {
        public boolean admitted() {
            return kind == Kind.ADMITTED;
        }
    }

    public interface Room {
        boolean copiesHeldBack();

        CpuPressure.Reading processors();
    }

    private boolean admittedBefore;
    private long lastAdmittedNanos;

    public synchronized Answer decide(int taken, int ceiling, long nowNanos, Room room) {
        if (ceiling > 0 && taken >= ceiling) return new Answer(Kind.CEILING, null);
        if (taken == 0) return admit(nowNanos, null);
        if (admittedBefore && nowNanos - lastAdmittedNanos < SETTLE_NANOS) return new Answer(Kind.SETTLING, null);
        if (!room.copiesHeldBack()) return new Answer(Kind.COPY_BEHIND, null);
        CpuPressure.Reading reading = room.processors();
        if (!reading.measured()) {
            return taken >= WITHOUT_MEASURE ? new Answer(Kind.NO_MEASURE, reading) : admit(nowNanos, reading);
        }
        if (reading.busy()) return new Answer(Kind.BUSY, reading);
        return admit(nowNanos, reading);
    }

    private Answer admit(long nowNanos, CpuPressure.Reading reading) {
        admittedBefore = true;
        lastAdmittedNanos = nowNanos;
        return new Answer(Kind.ADMITTED, reading);
    }
}
