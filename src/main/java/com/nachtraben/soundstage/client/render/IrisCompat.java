package com.nachtraben.soundstage.client.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class IrisCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger(IrisCompat.class);

    private static final String API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";

    private static final Method IN_USE = resolveInUse();
    private static final Object API = resolveApi();

    private static volatile boolean failed = false;

    private IrisCompat() {
    }

    public static boolean isShaderPackInUse() {
        if (IN_USE == null || API == null || failed) return false;
        try {
            boolean inUse = (Boolean) IN_USE.invoke(API);
            if (inUse && !copiesApplied) applyCopies();
            return inUse;
        } catch (Throwable t) {
            failed = true;
            LOGGER.warn("Iris shader-pack query failed; assuming no pack for the rest of the session", t);
            return false;
        }
    }

    private static final List<Object[]> COPIES = new ArrayList<>();
    private static boolean copiesApplied;

    public static synchronized void drawLike(Object vanilla, Object ours) {
        COPIES.add(new Object[]{vanilla, ours});
        copiesApplied = false;
    }

    private static synchronized void applyCopies() {
        copiesApplied = true;
        if (COPIES.isEmpty()) return;
        try {
            Method copy = null;
            for (Method method : Class.forName("net.irisshaders.iris.pipeline.IrisPipelines").getMethods()) {
                if (method.getName().equals("copyPipeline") && method.getParameterCount() == 2) copy = method;
            }
            if (copy == null) {
                LOGGER.warn("Iris has no IrisPipelines.copyPipeline; Soundstage's own pipelines draw with vanilla's "
                        + "programs under a pack");
                return;
            }
            for (Object[] pair : COPIES) copy.invoke(null, pair[0], pair[1]);
            LOGGER.info("Soundstage: Iris draws {} of Soundstage's pipelines as the vanilla pipelines they copy",
                    COPIES.size());
        } catch (Throwable t) {
            LOGGER.warn("Iris pipeline mapping failed; Soundstage's own pipelines draw with vanilla's programs under "
                    + "a pack", t);
        }
    }

    private static final Method SHADOWS_ACTIVE = resolveShadowsActive();

    private static volatile boolean shadowQueryFailed = false;

    static boolean isRenderingShadows() {
        if (SHADOWS_ACTIVE == null || API == null || shadowQueryFailed) return false;
        try {
            return (Boolean) SHADOWS_ACTIVE.invoke(API);
        } catch (Throwable t) {
            shadowQueryFailed = true;
            LOGGER.warn("Iris shadow-pass query failed; assuming no shadow pass for the rest of the session", t);
            return false;
        }
    }

    static Object enterBlockEntityPhase() {
        if (BLOCK_ENTITY_PHASE == null || GET_PHASE == null || SET_PHASE == null || phaseFailed) return null;
        try {
            Object pipeline = currentPipeline();
            if (pipeline == null) return null;
            Object previous = GET_PHASE.invoke(pipeline);
            if (previous == BLOCK_ENTITY_PHASE) return null;
            SET_PHASE.invoke(pipeline, BLOCK_ENTITY_PHASE);
            return previous;
        } catch (Throwable t) {
            phaseFailed = true;
            LOGGER.warn(
                    "Iris phase override failed; cached geometry will shade as an entity for the rest of the session",
                    t);
            return null;
        }
    }

    static void restorePhase(Object previous) {
        if (previous == null || SET_PHASE == null || phaseFailed) return;
        try {
            Object pipeline = currentPipeline();
            if (pipeline != null) SET_PHASE.invoke(pipeline, previous);
        } catch (Throwable t) {
            phaseFailed = true;
            LOGGER.warn("Iris phase restore failed; leaving the phase alone for the rest of the session", t);
        }
    }

    private static Object currentPipeline() throws Exception {
        if (GET_PIPELINE_MANAGER == null || GET_PIPELINE == null) return null;
        Object manager = GET_PIPELINE_MANAGER.invoke(null);
        return manager == null ? null : GET_PIPELINE.invoke(manager);
    }

    private static volatile boolean phaseFailed = false;

    private static final Method GET_PIPELINE_MANAGER = resolve("net.irisshaders.iris.Iris", "getPipelineManager");
    private static final Method GET_PIPELINE =
            resolve("net.irisshaders.iris.pipeline.PipelineManager", "getPipelineNullable");
    private static final Method GET_PHASE =
            resolve("net.irisshaders.iris.pipeline.WorldRenderingPipeline", "getPhase");
    private static final Method SET_PHASE = resolveSetPhase();
    private static final Object BLOCK_ENTITY_PHASE = resolveBlockEntityPhase();

    private static Method resolve(String className, String method) {
        try {
            return Class.forName(className).getMethod(method);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method resolveSetPhase() {
        try {
            Class<?> phase = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPhase");
            return Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPipeline")
                    .getMethod("setPhase", phase);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object resolveBlockEntityPhase() {
        try {
            Class<?> phase = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPhase");
            for (Object constant : phase.getEnumConstants()) {
                if ("BLOCK_ENTITIES".equals(((Enum<?>) constant).name())) return constant;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Method resolveShadowsActive() {
        try {
            return Class.forName(API_CLASS).getMethod("isRenderingShadowPass");
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method resolveInUse() {
        try {
            return Class.forName(API_CLASS).getMethod("isShaderPackInUse");
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object resolveApi() {
        try {
            return Class.forName(API_CLASS).getMethod("getInstance").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
