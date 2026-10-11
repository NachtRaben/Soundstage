package com.nachtraben.soundstage.core.video;

import java.util.HashSet;
import java.util.Set;

public final class PictureSlots {

    private final Set<Object> holders = new HashSet<>();

    public synchronized boolean take(Object holder, int limit) {
        if (holders.contains(holder)) return true;
        if (limit > 0 && holders.size() >= limit) return false;
        holders.add(holder);
        return true;
    }

    public synchronized PictureAdmission.Answer take(Object holder, int ceiling, PictureAdmission admission,
                                                     long nowNanos, PictureAdmission.Room room) {
        if (holders.contains(holder)) return new PictureAdmission.Answer(PictureAdmission.Kind.ADMITTED, null);
        PictureAdmission.Answer answer = admission.decide(holders.size(), ceiling, nowNanos, room);
        if (answer.admitted()) holders.add(holder);
        return answer;
    }

    public synchronized boolean holds(Object holder) {
        return holders.contains(holder);
    }

    public synchronized void release(Object holder) {
        holders.remove(holder);
    }

    public synchronized int taken() {
        return holders.size();
    }
}
