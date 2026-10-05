package com.nachtraben.soundstage.core;

public final class QueueLoopPolicy {
    private QueueLoopPolicy() {}

    public enum Transition {
        FINISHED,
        SKIPPED,
        PLAY_NOW,
        FAILED,
        STOPPED,
        CLEANUP
    }

    public static boolean shouldRequeue(boolean queueLoop, Transition transition) {
        if (!queueLoop || transition == null) return false;
        return transition == Transition.FINISHED
                || transition == Transition.SKIPPED
                || transition == Transition.PLAY_NOW;
    }
}
