package com.nachtraben.soundstage.core.video;

import java.util.ArrayList;
import java.util.function.Consumer;

public final class DecodedFrames<T> {

    private record Held<T>(long ptsMillis, T frame) {}

    private final ArrayList<Held<T>> held = new ArrayList<>();
    private long shownPtsMillis = Long.MIN_VALUE;

    public boolean add(long ptsMillis, T frame) {
        if (ptsMillis <= shownPtsMillis) return false;
        int at = held.size();
        while (at > 0 && held.get(at - 1).ptsMillis() > ptsMillis) at--;
        held.add(at, new Held<>(ptsMillis, frame));
        return true;
    }

    public T frameFor(double clockMillis, Consumer<? super T> recycle) {
        int due = newestDue(clockMillis);
        if (due < 0) return null;
        for (int i = 0; i < due; i++) recycle.accept(held.get(i).frame());
        Held<T> chosen = held.get(due);
        held.subList(0, due + 1).clear();
        shownPtsMillis = chosen.ptsMillis();
        return chosen.frame();
    }

    public void discardUnreachable(double clockMillis, Consumer<? super T> recycle) {
        int due = newestDue(clockMillis);
        if (due <= 0) return;
        for (int i = 0; i < due; i++) recycle.accept(held.get(i).frame());
        held.subList(0, due).clear();
    }

    public long shownPtsMillis() {
        return shownPtsMillis;
    }

    public int size() {
        return held.size();
    }

    public void clear(Consumer<? super T> recycle) {
        for (Held<T> h : held) recycle.accept(h.frame());
        held.clear();
        shownPtsMillis = Long.MIN_VALUE;
    }

    private int newestDue(double clockMillis) {
        if (Double.isNaN(clockMillis)) return -1;
        int due = -1;
        for (int i = 0; i < held.size() && held.get(i).ptsMillis() <= clockMillis; i++) due = i;
        return due;
    }
}
