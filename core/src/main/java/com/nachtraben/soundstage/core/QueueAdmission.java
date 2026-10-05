package com.nachtraben.soundstage.core;

public record QueueAdmission(int accepted, int available) {

    public static QueueAdmission of(int queueSize, int maxQueueSize, int maxPerRequest, int available) {
        int room = Math.max(0, maxQueueSize - queueSize);
        int allowed = Math.min(maxPerRequest, room);
        int offered = Math.max(0, available);
        return new QueueAdmission(Math.max(0, Math.min(allowed, offered)), offered);
    }

    public boolean full() {
        return accepted == 0 && available > 0;
    }

    public boolean truncated() {
        return accepted > 0 && accepted < available;
    }
}
