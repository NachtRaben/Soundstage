package com.nachtraben.soundstage.core;

import java.util.function.Predicate;

public final class RigFaults {
    private static volatile Predicate<String> staged;

    private RigFaults() {
    }

    public static void install(Predicate<String> faults) {
        staged = faults;
    }

    public static boolean on(String fault) {
        Predicate<String> faults = staged;
        return faults != null && faults.test(fault);
    }
}
