package com.nachtraben.soundstage.core;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public final class TrackQueue {

    public record Entry(long id, AudioTrack track, String encoded) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private final Function<AudioTrack, String> encoder;
    private final Random random;

    public TrackQueue(Function<AudioTrack, String> encoder) {
        this(encoder, new Random());
    }

    public TrackQueue(Function<AudioTrack, String> encoder, Random random) {
        this.encoder = encoder;
        this.random = random;
    }

    public Entry add(AudioTrack track) {
        Entry entry = new Entry(nextId.getAndIncrement(), track, encoder.apply(track));
        synchronized (entries) {
            entries.add(entry);
        }
        return entry;
    }

    public Entry pollNext(boolean shuffle) {
        synchronized (entries) {
            if (entries.isEmpty()) return null;
            int index = shuffle ? random.nextInt(entries.size()) : 0;
            return entries.remove(index);
        }
    }

    public boolean move(long id, int targetIndex) {
        synchronized (entries) {
            int current = indexOf(id);
            if (current < 0) return false;
            int clamped = Math.max(0, Math.min(targetIndex, entries.size() - 1));
            if (clamped == current) return false;
            entries.add(clamped, entries.remove(current));
            return true;
        }
    }

    public boolean remove(long id) {
        synchronized (entries) {
            int current = indexOf(id);
            if (current < 0) return false;
            entries.remove(current);
            return true;
        }
    }

    public Entry take(long id) {
        synchronized (entries) {
            int current = indexOf(id);
            return current < 0 ? null : entries.remove(current);
        }
    }

    public void clear() {
        synchronized (entries) {
            entries.clear();
        }
    }

    public int size() {
        synchronized (entries) {
            return entries.size();
        }
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public List<Entry> snapshot() {
        synchronized (entries) {
            return new ArrayList<>(entries);
        }
    }

    private int indexOf(long id) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id() == id) return i;
        }
        return -1;
    }
}
