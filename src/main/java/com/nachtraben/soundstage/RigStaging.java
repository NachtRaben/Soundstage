package com.nachtraben.soundstage;

import java.lang.reflect.Field;
import java.util.List;

public final class RigStaging {
    private static final String SESSION = "dev.armadeus.armalib.rig.impl.Session";

    public static final boolean ACTIVE = System.getProperty("armalib.rig.session") != null;

    private static final java.util.Set<String> FAULTS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static {
        if (ACTIVE) com.nachtraben.soundstage.core.RigFaults.install(RigStaging::fault);
    }

    private RigStaging() {
    }

    public static void stage(String fault, boolean on) {
        if (!ACTIVE) throw new IllegalStateException("a fault is staged only in a rig session: " + fault);
        if (on && FAULTS.add(fault)) {
            org.slf4j.LoggerFactory.getLogger("Soundstage").warn("Soundstage: red control — fault '{}' staged", fault);
        } else if (!on && FAULTS.remove(fault)) {
            org.slf4j.LoggerFactory.getLogger("Soundstage").info("Soundstage: fault '{}' cleared", fault);
        }
    }

    public static boolean fault(String fault) {
        return ACTIVE && !FAULTS.isEmpty() && FAULTS.contains(fault);
    }

    public static java.util.List<String> drainFaults() {
        java.util.List<String> left = new java.util.ArrayList<>(FAULTS);
        FAULTS.removeAll(left);
        return left;
    }

    public static boolean serverStages(String rigId, String control) {
        if (System.getProperty("armalib.rig.session") == null) return false;
        try {
            Object session = session();
            if (session == null) return false;
            Object run = session.getClass().getMethod("serverRun").invoke(session);
            return run != null && stages(run, rigId, control);
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    public static boolean sessionStages(String rigId, String control) {
        if (System.getProperty("armalib.rig.session") == null) return false;
        try {
            Object session = session();
            if (session == null) return false;
            Field runs = session.getClass().getField("runs");
            for (Object run : (List<?>) runs.get(session)) {
                if (stages(run, rigId, control)) return true;
            }
            return false;
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    public static boolean sessionRuns(String rigId) {
        if (System.getProperty("armalib.rig.session") == null) return false;
        try {
            Object session = session();
            if (session == null) return false;
            Field runs = session.getClass().getField("runs");
            for (Object run : (List<?>) runs.get(session)) {
                if (rigId.equals(run.getClass().getField("rig").get(run))) return true;
            }
            return false;
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    private static Object session() throws ReflectiveOperationException {
        return Class.forName(SESSION).getMethod("get").invoke(null);
    }

    private static boolean stages(Object run, String rigId, String control) throws ReflectiveOperationException {
        Class<?> type = run.getClass();
        return rigId.equals(type.getField("rig").get(run)) && control.equals(type.getField("redControl").get(run));
    }
}
