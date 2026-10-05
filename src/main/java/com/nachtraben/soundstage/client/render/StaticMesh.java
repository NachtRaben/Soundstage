package com.nachtraben.soundstage.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
//? if >=1.21 {
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
//?}
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
//? if >=1.21.11 {
/*import dev.armadeus.armalib.platform.client.render.RetainedMesh;
*///?} else {
import com.mojang.blaze3d.vertex.VertexBuffer;
//?}
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

public final class StaticMesh {

    static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final class NoOverlay {
        static final int U =
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY & 0xFFFF;
        static final int V =
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY >> 16;

        private NoOverlay() {}
    }

    private static final Matrix4f DRAW_MATRIX = new Matrix4f();
    //? if >=1.21.11 {
    /*private static final org.joml.Vector3f DISPLACEMENT = new org.joml.Vector3f();
    *///?}
    private static final org.joml.Vector3f REPLAY_NORMAL = new org.joml.Vector3f();

    private static final int BAKE_BUFFER_BYTES = 4 << 20;

    private static final class BakeBuilder implements AutoCloseable {
        //? if >=1.21 {
        private final ByteBufferBuilder allocation = new ByteBufferBuilder(BAKE_BUFFER_BYTES);
        private final BufferBuilder builder;

        BakeBuilder(RenderType type) {
            builder = new BufferBuilder(allocation, type.mode(), type.format());
        }

        VertexConsumer consumer() {
            return builder;
        }

        MeshData build() {
            return builder.build();
        }

        @Override
        public void close() {
            allocation.close();
        }
        //?} else {
        /*private static final java.util.ArrayDeque<BufferBuilder> POOL = new java.util.ArrayDeque<>();
        private final BufferBuilder builder;
        private final com.mojang.blaze3d.vertex.VertexConsumer.Adapter consumer;

        BakeBuilder(RenderType type) {
            BufferBuilder pooled = POOL.poll();
            builder = pooled != null ? pooled : new BufferBuilder(256 * 1024);
            builder.begin(type.mode(), type.format());
            consumer = com.mojang.blaze3d.vertex.VertexConsumer.of(builder);
        }

        VertexConsumer consumer() {
            return consumer;
        }

        MeshData build() {
            consumer.finish();
            return builder.endOrDiscardIfEmpty();
        }

        @Override
        public void close() {
            if (builder.building()) builder.discard();
            POOL.push(builder);
        }
        *///?}
    }

    private static void free(MeshData mesh) {
        //? if >=1.21 {
        mesh.close();
        //?} else {
        /*mesh.release();
        *///?}
    }

    private static final int MAX_ENTRIES = 4;

    private record CacheKey(Object key, Form form, boolean extended) {}

    private enum Form { BUFFERS, RECORDED, SPLIT }

    private final int capacity;

    public StaticMesh() {
        this(MAX_ENTRIES);
    }

    public StaticMesh(int capacity) {
        this.capacity = capacity;
    }

    private final LinkedHashMap<CacheKey, Baked> cache = new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, Baked> eldest) {
            if (size() <= capacity) return false;
            retire(eldest.getValue());
            return true;
        }
    };

    private static final List<Baked> RETIRED = new ArrayList<>();

    private static void retire(Baked baked) {
        if (!com.nachtraben.soundstage.RigStaging.fault("retire-at-once")
                && DEFERRED.stream().anyMatch(pending -> drawsFrom(pending, baked))) {
            RETIRED.add(baked);
            retiredCount++;
        } else {
            baked.close();
        }
    }

    private static long retiredCount;

    public static long retirements() {
        return retiredCount;
    }

    private static boolean drawsFrom(Pending pending, Baked baked) {
        return (pending.buffer() != null && baked.buffers.containsValue(pending.buffer()))
                || (pending.deformSource() != null && baked.deformSources.containsValue(pending.deformSource()));
    }

    private static final class Baked {
        //? if >=1.21.11 {
        /*final Map<RenderType, RetainedMesh> buffers = new LinkedHashMap<>();
        final Map<RenderType, RetainedMesh> restDeforming = new LinkedHashMap<>();
        final java.util.Set<RenderType> noDeformSource = new java.util.HashSet<>();
        *///?} else {
        final Map<RenderType, VertexBuffer> buffers = new LinkedHashMap<>();
        final Map<RenderType, VertexBuffer> restDeforming = new LinkedHashMap<>();
        final java.util.Set<RenderType> noDeformSource = new java.util.HashSet<>();
        //?}
        final Map<RenderType, List<Vertex>> recorded = new LinkedHashMap<>();
        final Map<RenderType, DisplacementPass.Source> deformSources = new LinkedHashMap<>();

        void close() {
            buffers.values().forEach(buffer -> buffer.close());
            restDeforming.values().forEach(buffer -> buffer.close());
            restDeforming.clear();
            buffers.clear();
            recorded.clear();
            deformSources.values().forEach(DisplacementPass.Source::close);
            deformSources.clear();
        }
    }

    private static final java.util.Set<StaticMesh> LIVE =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private boolean registered;

    public static void invalidateAll() {
        recycle(DEFERRED);
        recycle(OPAQUE_DEFERRED);
        FEEDBACK_SOURCES.clear();
        for (StaticMesh mesh : java.util.List.copyOf(LIVE)) {
            mesh.close();
        }
        LIVE.clear();
        RETIRED.forEach(Baked::close);
        RETIRED.clear();
    }

    private static int baking;

    private static boolean mustBuildLive() {
        return baking > 0 || RenderPreview.bypassGeometryCache;
    }

    private static boolean mustReplay() {
        return IrisCompat.isShaderPackInUse() || !deferralAvailable || RenderPreview.forceRecordedReplay;
    }

    //? if <1.21.11 {
    private static boolean levelSourceIsBuffered() {
        return net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource().getClass()
                != net.minecraft.client.renderer.MultiBufferSource.BufferSource.class;
    }
    //?}

    private static boolean mayDefer() {
        return deferralAvailable && !IrisCompat.isRenderingShadows();
    }

    private Baked cached(Object key, Form form, BiConsumer<PoseStack, GeometrySink> builder) {
        if (!registered) {
            LIVE.add(this);
            registered = true;
        }
        CacheKey entry = new CacheKey(key, form, IrisCompat.isShaderPackInUse());
        return cache.computeIfAbsent(entry, e -> bake(builder, e.form()));
    }

    private static void defer(Baked baked, PoseStack.Pose pose, float r, float g, float b) {
        for (var entry : baked.buffers.entrySet()) {
            DEFERRED.add(pending(entry.getKey(), entry.getValue(), pose, r, g, b));
        }
    }

    private static void deferTranslated(Baked baked, PoseStack.Pose pose, float displacement) {
        if (baked.buffers.isEmpty()) return;
        for (var entry : baked.buffers.entrySet()) {
            Pending queued = pending(SoundstageRenderTypes.undisplaced(entry.getKey()), entry.getValue(), pose, 1f, 1f, 1f);
            queued.modelView.translate(0f, 0f, displacement);
            DEFERRED.add(queued);
        }
    }

    public void render(Object key, PoseStack poseStack, GeometrySink liveSink,
                       BiConsumer<PoseStack, GeometrySink> builder) {
        if (mustBuildLive()) {
            //? if <1.21.11 {
            if (baking == 0 && !mustReplay() && !RenderPreview.forceRecordedReplay && levelSourceIsBuffered()) {
                Baked fresh = bake(builder, Form.BUFFERS);
                drawBuffers(fresh, poseStack.last());
                fresh.close();
                return;
            }
            //?}
            builder.accept(poseStack, liveSink);
            return;
        }
        if (RenderPreview.forceRecordedReplay) {
            replay(cached(key, Form.RECORDED, builder), poseStack.last(), liveSink);
            return;
        }
        if (IrisCompat.isShaderPackInUse()) {
            if (mayDefer()) {
                defer(cached(key, Form.BUFFERS, builder), poseStack.last(), 1f, 1f, 1f);
            } else {
                replay(cached(key, Form.RECORDED, builder), poseStack.last(), liveSink);
            }
            return;
        }
        drawBuffers(cached(key, Form.BUFFERS, builder), poseStack.last());
    }

    public void renderBlended(Object key, PoseStack poseStack, GeometrySink liveSink,
                              BiConsumer<PoseStack, GeometrySink> builder) {
        renderBlended(key, 1f, 1f, 1f, poseStack, liveSink, builder);
    }

    public void renderBlended(Object key, float tintR, float tintG, float tintB,
                              PoseStack poseStack, GeometrySink liveSink,
                              BiConsumer<PoseStack, GeometrySink> builder) {
        if (mustBuildLive()) {
            //? if <1.21.11 {
            if (baking == 0 && !mustReplay() && mayDefer() && levelSourceIsBuffered()) {
                Baked fresh = bake(builder, Form.BUFFERS);
                defer(fresh, poseStack.last(), tintR, tintG, tintB);
                retire(fresh);
                return;
            }
            //?}
            builder.accept(poseStack, tinted(liveSink, tintR, tintG, tintB));
            return;
        }
        //? if >=1.21.11 && <26.1 {
        /*if (IrisCompat.isShaderPackInUse() && deferralAvailable && !RenderPreview.forceRecordedReplay && mayDefer()) {
            defer(cached(key, Form.BUFFERS, builder), poseStack.last(), tintR, tintG, tintB);
            return;
        }
        *///?}
        if (mustReplay() || !mayDefer()) {
            replay(cached(key, Form.RECORDED, builder), poseStack.last(), tinted(liveSink, tintR, tintG, tintB));
            return;
        }
        defer(cached(key, Form.BUFFERS, builder), poseStack.last(), tintR, tintG, tintB);
    }

    private static GeometrySink tinted(GeometrySink sink, float r, float g, float b) {
        if (r == 1f && g == 1f && b == 1f) return sink;
        return new GeometrySink() {
            @Override
            public void emit(RenderType type, Emitter body) {
                sink.emit(type, (pose, consumer) -> body.accept(pose, new Tint(consumer, r, g, b)));
            }

            @Override
            public void emitBlockModel(RenderType type, BlockState state, int light, int overlay) {
                sink.emitBlockModel(type, state, light, overlay);
            }
        };
    }

    private record Tint(VertexConsumer delegate, float r, float g, float b) implements VertexConsumer {
        //? if >=1.21.11 {
        /*@Override
        public VertexConsumer setColor(int argb) {
            return setColor(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24);
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            delegate.setLineWidth(width);
            return this;
        }
        *///?}
        //? if >=26.3 {
        /*@Override
        public VertexConsumer setUv3(float u, float v) {
            delegate.setUv3(u, v);
            return this;
        }
        *///?}

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(Math.round(red * r), Math.round(green * g), Math.round(blue * b), alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }

    private static final class Pending {
        private RenderType type;
        //? if >=1.21.11 {
        /*private RetainedMesh buffer;
        *///?} else {
        private VertexBuffer buffer;
        //?}
        private final Matrix4f modelView = new Matrix4f();
        private final org.joml.Matrix3f normal = new org.joml.Matrix3f();
        private float r = 1f, g = 1f, b = 1f;
        private DisplacementPass.Source deformSource;
        private float displacement;
        private int slot;

        RenderType type() {
            return type;
        }

        //? if >=1.21.11 {
        /*RetainedMesh buffer() {
        *///?} else {
        VertexBuffer buffer() {
        //?}
            return buffer;
        }

        Matrix4f modelView() {
            return modelView;
        }

        org.joml.Matrix3f normal() {
            return normal;
        }

        float r() {
            return r;
        }

        float g() {
            return g;
        }

        float b() {
            return b;
        }

        DisplacementPass.Source deformSource() {
            return deformSource;
        }

        float displacement() {
            return displacement;
        }

        int slot() {
            return slot;
        }
    }

    private static final java.util.ArrayDeque<Pending> PENDING_POOL = new java.util.ArrayDeque<>();

    //? if >=1.21.11 {
    /*private static Pending pending(RenderType type, RetainedMesh buffer, PoseStack.Pose pose, float r, float g, float b) {
        Pending entry = PENDING_POOL.pollLast();
        if (entry == null) entry = new Pending();
        entry.type = type;
        entry.buffer = buffer;
        entry.modelView.set(RenderSystem.getModelViewStack()).mul(pose.pose());
    *///?} else {
    private static Pending pending(RenderType type, VertexBuffer buffer, PoseStack.Pose pose, float r, float g, float b) {
        Pending entry = PENDING_POOL.pollLast();
        if (entry == null) entry = new Pending();
        entry.type = type;
        entry.buffer = buffer;
        entry.modelView.set(RenderSystem.getModelViewMatrix()).mul(pose.pose());
    //?}
        entry.normal.set(pose.normal());
        entry.r = r;
        entry.g = g;
        entry.b = b;
        entry.deformSource = null;
        entry.displacement = 0f;
        entry.slot = 0;
        return entry;
    }

    private static void recycle(List<Pending> queue) {
        for (Pending entry : queue) {
            entry.type = null;
            entry.buffer = null;
            entry.deformSource = null;
            PENDING_POOL.add(entry);
        }
        queue.clear();
    }

    private static final List<Pending> DEFERRED = new ArrayList<>();

    private static final List<DisplacementPass.Source> FEEDBACK_SOURCES = new ArrayList<>();

    private static final List<Pending> OPAQUE_DEFERRED = new ArrayList<>();

    private static boolean deferralAvailable = true;

    public static void setDeferralAvailable(boolean available) {
        deferralAvailable = available;
    }

    //? if >=1.21.11 {
    /*private static void flushBatch(RenderType type, List<RetainedMesh.Entry> batch) {
        if (type != null && !batch.isEmpty()) RetainedMesh.drawBatch(type, batch);
        batch.clear();
    }

    private static org.joml.Matrix3f lightsTurnedBy(org.joml.Matrix3f normal) {
        return com.nachtraben.soundstage.RigStaging.fault("unturned-lights") ? null : normal;
    }
    *///?}

    public static void flushDeferred() {
        displacementFrames++;
        displacementValueTotal += FRAME_DISPLACEMENTS.size();
        FRAME_DISPLACEMENTS.clear();
        if (DEFERRED.isEmpty() && OPAQUE_DEFERRED.isEmpty()) return;
        long startedAt = System.nanoTime();
        Object previousPhase = IrisCompat.enterBlockEntityPhase();
        //? if >=1.21.11 {
        /*try {
            if (!FEEDBACK_SOURCES.isEmpty()) DisplacementPass.displaceAll(FEEDBACK_SOURCES);
            RenderType batchType = null;
            List<RetainedMesh.Entry> batch = new ArrayList<>();
            for (Pending pending : OPAQUE_DEFERRED) {
                drawCount++;
                if (batchType != pending.type()) {
                    flushBatch(batchType, batch);
                    batchType = pending.type();
                }
                batch.add(new RetainedMesh.Entry(pending.buffer(), pending.modelView(), pending.r(), pending.g(), pending.b(), 1f,
                        new org.joml.Vector3f(0f, 0f, pending.displacement()), 0, lightsTurnedBy(pending.normal())));
            }
            flushBatch(batchType, batch);
            batchType = null;
            for (Pending pending : DEFERRED) {
                drawCount++;
                DisplacementPass.Source source = pending.deformSource();
                if (source != null) {
                    if (source.isClosed()) continue;
                    if (probeRequested) probe(pending);
                    RetainedMesh mesh;
                    int copy;
                    if (source.displaced()) {
                        feedbackDraws++;
                        mesh = source.copies();
                        copy = pending.slot();
                    } else {
                        mesh = source.rest;
                        copy = 0;
                    }
                    if (batchType != pending.type()) {
                        flushBatch(batchType, batch);
                        batchType = pending.type();
                    }
                    batch.add(new RetainedMesh.Entry(mesh, pending.modelView(), 1f, 1f, 1f, 1f, null, copy, lightsTurnedBy(pending.normal())));
                    continue;
                }
                if (pending.buffer() != null) {
                    if (batchType != pending.type()) {
                        flushBatch(batchType, batch);
                        batchType = pending.type();
                    }
                    batch.add(new RetainedMesh.Entry(pending.buffer(), pending.modelView(), pending.r(), pending.g(), pending.b(), 1f, null, 0,
                            lightsTurnedBy(pending.normal())));
                }
            }
            flushBatch(batchType, batch);
        } finally {
            FEEDBACK_SOURCES.forEach(DisplacementPass.Source::endFrame);
            FEEDBACK_SOURCES.clear();
            recycle(OPAQUE_DEFERRED);
            IrisCompat.restorePhase(previousPhase);
            drawNanos += System.nanoTime() - startedAt;
            recycle(DEFERRED);
            RETIRED.forEach(Baked::close);
            RETIRED.clear();
        }
        *///?} else {
        if (!FEEDBACK_SOURCES.isEmpty()) DisplacementPass.displaceAll(FEEDBACK_SOURCES);
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        RenderType current = null;
        try {
            for (Pending pending : DEFERRED) {
                if (current != pending.type()) {
                    if (current != null) current.clearRenderState();
                    pending.type().setupRenderState();
                    current = pending.type();
                }
                drawCount++;
                RenderSystem.setShaderColor(pending.r(), pending.g(), pending.b(), 1.0f);
                if (pending.deformSource() != null) {
                    drawDisplaced(pending, projection);
                    continue;
                }
                pending.buffer().bind();
                drawLit(pending.buffer(), pending.type(), pending.modelView(), projection, RenderSystem.getShader(), pending.normal());
            }
        } finally {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            VertexBuffer.unbind();
            if (current != null) current.clearRenderState();
            IrisCompat.restorePhase(previousPhase);
            FEEDBACK_SOURCES.forEach(DisplacementPass.Source::endFrame);
            FEEDBACK_SOURCES.clear();
            drawNanos += System.nanoTime() - startedAt;
            recycle(DEFERRED);
            RETIRED.forEach(Baked::close);
            RETIRED.clear();
        }
        //?}
    }

    public void renderDisplaced(Object key, float displacement,
                                PoseStack poseStack, GeometrySink liveSink,
                                BiConsumer<PoseStack, GeometrySink> restBuilder,
                                BiConsumer<PoseStack, GeometrySink> liveBuilder) {
        displacedDrawCount++;
        FRAME_DISPLACEMENTS.add(displacement);
        if (mustBuildLive()) {
            liveBuilder.accept(poseStack, liveSink);
            return;
        }
        if (mustReplay() || !SoundstageRenderTypes.spiderShaderReady()) {
            if (mayDefer()) {
                Baked split = cached(key, Form.SPLIT, restBuilder);
                deferTranslated(split, poseStack.last(), displacement);
                deferDeformed(split, poseStack.last(), displacement);
            } else {
                replay(cached(key, Form.RECORDED, restBuilder), poseStack.last(), liveSink, displacement);
            }
            return;
        }
        Baked baked = cached(key, Form.BUFFERS, restBuilder);
        if (displacement != 0f) programDraws++;
        if (!loggedDisplacedOnGpu) {
            loggedDisplacedOnGpu = true;
            LOGGER.info("Soundstage: driver suspension drawn by the displacement program");
        }
        pendingDisplacement = com.nachtraben.soundstage.RigStaging.fault("no-travel") ? 0f : displacement;
        drawBuffers(baked, poseStack.last());
        pendingDisplacement = Float.NaN;
    }

    private static float pendingDisplacement = Float.NaN;

    private static boolean loggedDisplacedOnGpu;

    private static long programDraws;

    public static long suspensionProgramDraws() {
        return programDraws;
    }

    private static void deferDeformed(Baked baked, PoseStack.Pose pose, float displacement) {
        //? if >=1.21.11 {
        /*for (Map.Entry<RenderType, List<Vertex>> entry : baked.recorded.entrySet()) {
            List<Vertex> vertices = entry.getValue();
            if (vertices.isEmpty()) continue;
            RenderType type = SoundstageRenderTypes.undisplaced(entry.getKey());
            if (DisplacementPass.isUsable() && !baked.noDeformSource.contains(entry.getKey())) {
                DisplacementPass.Source source = baked.deformSources.get(entry.getKey());
                if (source == null) {
                    source = uploadDeformSource(type, vertices);
                    if (source == null) {
                        baked.noDeformSource.add(entry.getKey());
                    } else {
                        baked.deformSources.put(entry.getKey(), source);
                    }
                }
                if (source != null && !source.isClosed()) {
                    if (!source.hasTravels()) FEEDBACK_SOURCES.add(source);
                    Pending queued = pending(type, null, pose, 1f, 1f, 1f);
                    queued.deformSource = source;
                    queued.displacement = displacement;
                    queued.slot = source.slotFor(displacement);
                    DEFERRED.add(queued);
                    continue;
                }
            }
            RetainedMesh rest = baked.restDeforming.get(entry.getKey());
            if (rest == null) {
                rest = uploadRest(type, vertices);
                if (rest == null) continue;
                baked.restDeforming.put(entry.getKey(), rest);
            }
            DEFERRED.add(pending(type, rest, pose, 1f, 1f, 1f));
        }
    }

    private static RetainedMesh uploadRest(RenderType type, List<Vertex> vertices) {
        return uploadRest(type, vertices, "soundstage rest suspension", 1);
    }

    private static RetainedMesh uploadRest(RenderType type, List<Vertex> vertices, String label, int copies) {
        ByteBufferBuilder allocation = new ByteBufferBuilder(BAKE_BUFFER_BYTES);
        try {
            BufferBuilder builder = new BufferBuilder(allocation, type.mode(), type.format());
            for (Vertex v : vertices) {
                builder.addVertex(v.x, v.y, v.z)
                        .setColor(v.r, v.g, v.b, v.a)
                        .setUv(v.u, v.vCoord)
                        .setUv1(NoOverlay.U, NoOverlay.V)
                        .setUv2(v.lightU, v.lightV)
                        .setNormal(v.nx, v.ny, v.nz);
            }
            MeshData mesh = builder.build();
            return mesh == null ? null : RetainedMesh.upload(label, mesh, copies);
        } finally {
            allocation.close();
        }
    }

    private static DisplacementPass.Source uploadDeformSource(RenderType type, List<Vertex> vertices) {
        float[] weights = new float[vertices.size()];
        for (int i = 0; i < weights.length; i++) weights[i] = vertices.get(i).overlayU / 255f;
        com.mojang.blaze3d.vertex.VertexFormat format = type.format();
        //? if >=26.2 {
        /^int positionOffset = format.getElement("Position").offset();
        ^///?} else {
        int positionOffset = format.getOffset(com.mojang.blaze3d.vertex.VertexFormatElement.POSITION);
        //?}
        try {
            return DisplacementPass.create(copies -> {
                RetainedMesh mesh = uploadRest(type, vertices, copies == 1 ? "soundstage rest suspension"
                        : "soundstage displaced suspension", copies);
                if (mesh == null) throw new IllegalStateException("no deforming vertices to upload");
                return mesh;
            }, weights, format.getVertexSize(), positionOffset);
        } catch (Throwable t) {
            LOGGER.warn("Soundstage: could not hand a suspension to the displacement pass; it is drawn at rest", t);
            return null;
        }
        *///?} else {
        for (Map.Entry<RenderType, List<Vertex>> entry : baked.recorded.entrySet()) {
            List<Vertex> vertices = entry.getValue();
            if (vertices.isEmpty()) continue;
            RenderType type = SoundstageRenderTypes.undisplaced(entry.getKey());
            if (DisplacementPass.isUsable() && !baked.noDeformSource.contains(entry.getKey())) {
                DisplacementPass.Source source = baked.deformSources.get(entry.getKey());
                if (source == null) {
                    source = uploadDeformSource(type, vertices);
                    if (source == null) {
                        baked.noDeformSource.add(entry.getKey());
                    } else {
                        baked.deformSources.put(entry.getKey(), source);
                    }
                }
                if (source != null && !source.isClosed()) {
                    if (!source.hasTravels()) FEEDBACK_SOURCES.add(source);
                    Pending queued = pending(type, null, pose, 1f, 1f, 1f);
                    queued.deformSource = source;
                    queued.displacement = displacement;
                    queued.slot = source.slotFor(displacement);
                    DEFERRED.add(queued);
                    continue;
                }
            }
            VertexBuffer rest = baked.restDeforming.get(entry.getKey());
            if (rest == null) {
                rest = uploadRestBuffer(type, vertices);
                if (rest == null) continue;
                baked.restDeforming.put(entry.getKey(), rest);
            }
            DEFERRED.add(pending(type, rest, pose, 1f, 1f, 1f));
        }
        //?}
    }

    //? if <1.21.11 {
    private static void drawDisplaced(Pending pending, Matrix4f projection) {
        DisplacementPass.Source source = pending.deformSource();
        if (source.isClosed()) return;
        boolean displaced = source.displaced();
        if (displaced) feedbackDraws++;
        if (probeRequested) probe(pending);
        net.minecraft.client.renderer.ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            DisplacementPass.unbind();
            return;
        }
        int indexCount = source.vertexCount / 4 * 6;
        var sequential = RenderSystem.getSequentialBuffer(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS);
        DisplacementPass.bindForDraw(source, pending.slot(), displaced);
        sequential.bind(indexCount);
        //? if >=1.21 {
        shader.setDefaultUniforms(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                pending.modelView(), projection, net.minecraft.client.Minecraft.getInstance().getWindow());
        //?} else {
        /*setDefaultUniforms(shader, pending.modelView(), projection);
        *///?}
        lightInMeshFrame(shader, pending.normal());
        shader.apply();
        int baseVertex = DisplacementPass.baseVertex(source, pending.slot(), displaced);
        DisplacementPass.drawElementsBaseVertex(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS.asGLMode,
                indexCount, sequential.type().asGLType, baseVertex);
        shader.clear();
        DisplacementPass.unbind();
    }

    private static void probe(Pending pending) {
        probeRequested = false;
        try {
            probeResult = DisplacementPass.probe(pending.deformSource(), pending.slot(), pending.displacement());
        } catch (Throwable t) {
            LOGGER.warn("Soundstage: the suspension probe failed", t);
            probeResult = new float[]{pending.displacement(), Float.NaN, 0f, 0f};
        }
    }
    //?}

    //? if <1.21.11 {
    private static void drawLit(VertexBuffer buffer, RenderType type, Matrix4f modelView, Matrix4f projection,
                                net.minecraft.client.renderer.ShaderInstance shader, org.joml.Matrix3f normal) {
        if (shader == null) return;
        //? if >=1.21 {
        shader.setDefaultUniforms(type.mode(), modelView, projection, net.minecraft.client.Minecraft.getInstance().getWindow());
        //?} else {
        /*setDefaultUniforms(shader, modelView, projection);
        *///?}
        lightInMeshFrame(shader, normal);
        shader.apply();
        buffer.draw();
        shader.clear();
    }

    private static final org.joml.Vector3f LIGHT = new org.joml.Vector3f();

    private static void lightInMeshFrame(net.minecraft.client.renderer.ShaderInstance shader, org.joml.Matrix3f normal) {
        if (com.nachtraben.soundstage.RigStaging.fault("unturned-lights")) return;
        relight(shader.LIGHT0_DIRECTION, normal);
        relight(shader.LIGHT1_DIRECTION, normal);
    }

    private static void relight(com.mojang.blaze3d.shaders.Uniform uniform, org.joml.Matrix3f normal) {
        if (uniform == null) return;
        java.nio.FloatBuffer value = uniform.getFloatBuffer();
        normal.transformTranspose(LIGHT.set(value.get(0), value.get(1), value.get(2))).normalize();
        uniform.set(LIGHT.x(), LIGHT.y(), LIGHT.z());
    }
    //?}

    //? if <1.21 {
    /*private static void setDefaultUniforms(net.minecraft.client.renderer.ShaderInstance shader, Matrix4f modelView,
                                           Matrix4f projection) {
        for (int i = 0; i < 12; i++) {
            shader.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
        }
        if (shader.MODEL_VIEW_MATRIX != null) shader.MODEL_VIEW_MATRIX.set(modelView);
        if (shader.PROJECTION_MATRIX != null) shader.PROJECTION_MATRIX.set(projection);
        if (shader.INVERSE_VIEW_ROTATION_MATRIX != null) {
            shader.INVERSE_VIEW_ROTATION_MATRIX.set(RenderSystem.getInverseViewRotationMatrix());
        }
        if (shader.COLOR_MODULATOR != null) shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        if (shader.GLINT_ALPHA != null) shader.GLINT_ALPHA.set(RenderSystem.getShaderGlintAlpha());
        if (shader.FOG_START != null) shader.FOG_START.set(RenderSystem.getShaderFogStart());
        if (shader.FOG_END != null) shader.FOG_END.set(RenderSystem.getShaderFogEnd());
        if (shader.FOG_COLOR != null) shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        if (shader.FOG_SHAPE != null) shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        if (shader.TEXTURE_MATRIX != null) shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        if (shader.GAME_TIME != null) shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
        if (shader.SCREEN_SIZE != null) {
            var window = net.minecraft.client.Minecraft.getInstance().getWindow();
            shader.SCREEN_SIZE.set((float) window.getWidth(), (float) window.getHeight());
        }
        RenderSystem.setupShaderLights(shader);
    }
    *///?}

    public static long deformedBuilds() {
        return 0;
    }

    public static long deformedVertices() {
        return 0;
    }

    public static long deformedNanos() {
        return 0;
    }

    //? if <1.21.11 {
    private static DisplacementPass.Source uploadDeformSource(RenderType type, List<Vertex> vertices) {
        BakeBuilder bake = new BakeBuilder(type);
        MeshData mesh = null;
        try {
            VertexConsumer builder = bake.consumer();
            float[] weights = new float[vertices.size()];
            int i = 0;
            for (Vertex v : vertices) {
                weights[i++] = v.overlayU / 255f;
                builder.addVertex(v.x, v.y, v.z)
                        .setColor(v.r, v.g, v.b, v.a)
                        .setUv(v.u, v.vCoord)
                        .setUv1(NoOverlay.U, NoOverlay.V)
                        .setUv2(v.lightU, v.lightV)
                        .setNormal(v.nx, v.ny, v.nz);
            }
            mesh = bake.build();
            if (mesh == null) return null;
            return DisplacementPass.create(mesh, weights);
        } catch (Throwable t) {
            LOGGER.warn("Soundstage: could not upload a displacement source; the suspension is drawn at rest", t);
            return null;
        } finally {
            if (mesh != null) free(mesh);
            bake.close();
        }
    }

    private static VertexBuffer uploadRestBuffer(RenderType type, List<Vertex> vertices) {
        BakeBuilder bake = new BakeBuilder(type);
        try {
            VertexConsumer builder = bake.consumer();
            for (Vertex v : vertices) {
                builder.addVertex(v.x, v.y, v.z)
                        .setColor(v.r, v.g, v.b, v.a)
                        .setUv(v.u, v.vCoord)
                        .setUv1(NoOverlay.U, NoOverlay.V)
                        .setUv2(v.lightU, v.lightV)
                        .setNormal(v.nx, v.ny, v.nz);
            }
            MeshData mesh = bake.build();
            if (mesh == null) return null;
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(mesh);
            VertexBuffer.unbind();
            com.mojang.blaze3d.vertex.BufferUploader.invalidate();
            return buffer;
        } finally {
            bake.close();
        }
    }
    //?}

    private static final java.util.Set<Float> FRAME_DISPLACEMENTS = new java.util.HashSet<>();
    private static long displacementFrames;
    private static long displacementValueTotal;
    private static long displacedDrawCount;

    public static double displacementValuesPerFrame() {
        return displacementFrames == 0 ? 0 : displacementValueTotal / (double) displacementFrames;
    }

    public static long displacedDraws() {
        return displacedDrawCount;
    }

    private void drawBuffers(Baked baked, PoseStack.Pose pose) {
        long startedAt = System.nanoTime();
        meshDrawCount++;
        Matrix4f modelView = DRAW_MATRIX.set(RenderSystem.getModelViewMatrix()).mul(pose.pose());
        //? if >=1.21.11 {
        /*org.joml.Vector3f offset = Float.isNaN(pendingDisplacement) ? null : DISPLACEMENT.set(0f, 0f, pendingDisplacement);
        for (Map.Entry<RenderType, RetainedMesh> entry : baked.buffers.entrySet()) {
            RenderType type = SoundstageRenderTypes.drawnAs(entry.getKey());
            //? if >=26.2 {
            /^if (deferralAvailable) {
                Pending queued = pending(type, entry.getValue(), pose, 1f, 1f, 1f);
                queued.displacement = offset == null ? 0f : offset.z;
                OPAQUE_DEFERRED.add(queued);
                continue;
            }
            ^///?}
            drawCount++;
            entry.getValue().draw(type, modelView, 1f, 1f, 1f, 1f, offset, 0, lightsTurnedBy(pose.normal()));
        }
        *///?} else {
        for (Map.Entry<RenderType, VertexBuffer> entry : baked.buffers.entrySet()) {
            RenderType type = SoundstageRenderTypes.drawnAs(entry.getKey());
            VertexBuffer buffer = entry.getValue();
            drawCount++;
            type.setupRenderState();
            var shader = RenderSystem.getShader();
            if (!Float.isNaN(pendingDisplacement) && shader != null) {
                var displacement = shader.getUniform("Displacement");
                if (displacement != null) displacement.set(pendingDisplacement);
            }
            buffer.bind();
            drawLit(buffer, type, modelView, RenderSystem.getProjectionMatrix(), shader, pose.normal());
            VertexBuffer.unbind();
            type.clearRenderState();
        }
        //?}
        drawNanos += System.nanoTime() - startedAt;
    }

    private void replay(Baked baked, PoseStack.Pose pose, GeometrySink liveSink) {
        replay(baked, pose, liveSink, Float.NaN);
    }

    private void replay(Baked baked, PoseStack.Pose pose, GeometrySink liveSink, float displacement) {
        meshDrawCount++;
        replayCount++;
        long startedAt = System.nanoTime();
        try {
            replayBody(baked, pose, liveSink, displacement);
        } finally {
            replayNanos += System.nanoTime() - startedAt;
        }
    }

    private void replayBody(Baked baked, PoseStack.Pose pose, GeometrySink liveSink, float displacement) {
        boolean displaced = !Float.isNaN(displacement);
        float perWeight = displaced ? displacement / 255f : 0f;
        boolean unturned = com.nachtraben.soundstage.RigStaging.fault("unturned-lights");
        for (Map.Entry<RenderType, List<Vertex>> entry : baked.recorded.entrySet()) {
            List<Vertex> vertices = entry.getValue();
            if (vertices.isEmpty()) continue;
            replayVertexCount += vertices.size();
            RenderType type = displaced
                    ? SoundstageRenderTypes.undisplaced(entry.getKey())
                    : entry.getKey();
            liveSink.emit(type, (emitPose, consumer) -> {
                float lastNx = Float.NaN, lastNy = Float.NaN, lastNz = Float.NaN;
                for (Vertex v : vertices) {
                    consumer.addVertex(emitPose.pose(), v.x, v.y, v.z + v.overlayU * perWeight)
                            .setColor(v.r, v.g, v.b, v.a)
                            .setUv(v.u, v.vCoord)
                            .setUv1(displaced ? NoOverlay.U : v.overlayU,
                                    displaced ? NoOverlay.V : v.overlayV)
                            .setUv2(v.lightU, v.lightV);
                    if (v.nx != lastNx || v.ny != lastNy || v.nz != lastNz) {
                        if (unturned) REPLAY_NORMAL.set(v.nx, v.ny, v.nz);
                        else emitPose.transformNormal(v.nx, v.ny, v.nz, REPLAY_NORMAL);
                        lastNx = v.nx;
                        lastNy = v.ny;
                        lastNz = v.nz;
                    }
                    consumer.setNormal(REPLAY_NORMAL.x(), REPLAY_NORMAL.y(), REPLAY_NORMAL.z());
                }
            });
        }
    }

    private static volatile boolean probeRequested;
    private static volatile float[] probeResult;
    private static long feedbackDraws;

    public static void requestSuspensionProbe() {
        probeResult = null;
        probeRequested = true;
    }

    public static float[] suspensionProbe() {
        return probeResult;
    }

    //? if >=1.21.11 {
    /*private static void probe(Pending pending) {
        probeRequested = false;
        try {
            DisplacementPass.Probe answer = DisplacementPass.probe(pending.deformSource(), pending.slot(), pending.displacement());
            probeResult = new float[]{answer.requested(), answer.applied(), answer.displaced() ? 1f : 0f, answer.vertices()};
        } catch (Throwable t) {
            LOGGER.warn("Soundstage: the suspension probe failed", t);
            probeResult = new float[]{pending.displacement(), Float.NaN, 0f, 0f};
        }
    }
    *///?}

    public static long feedbackDraws() {
        return feedbackDraws;
    }

    public static long feedbackPasses() {
        return DisplacementPass.passes();
    }

    public static long feedbackNanos() {
        return DisplacementPass.passNanos();
    }

    public static void setSuspensionFeedback(boolean enabled) {
        DisplacementPass.setEnabled(enabled);
    }

    public static long bakes() {
        return bakeCount;
    }

    private static long bakeCount;

    public static long draws() {
        return drawCount;
    }

    public static long drawNanos() {
        return drawNanos;
    }

    public static long meshDraws() {
        return meshDrawCount;
    }

    private static long drawCount;

    public static long drawCount() {
        return drawCount;
    }
    private static long meshDrawCount;
    private static long drawNanos;
    private static long replayCount;
    private static long replayVertexCount;
    private static long replayNanos;

    public static long replayNanos() {
        return replayNanos;
    }

    public static long replays() {
        return replayCount;
    }

    public static long replayedVertices() {
        return replayVertexCount;
    }

    private Baked bake(BiConsumer<PoseStack, GeometrySink> builder, Form form) {
        bakeCount++;
        Baked baked = new Baked();
        PoseStack scratch = new PoseStack();
        if (form == Form.RECORDED) {
            GeometrySink recordingSink = new GeometrySink() {
                @Override
                public void emit(RenderType type, Emitter body) {
                    body.accept(scratch.last(),
                            new Recorder(baked.recorded.computeIfAbsent(type, t -> new ArrayList<>())));
                }

                @Override
                public void emitBlockModel(RenderType type, BlockState state, int light, int overlay) {
                    throw new UnsupportedOperationException("Baked block models are not cached static geometry.");
                }
            };
            baking++;
            try {
                builder.accept(scratch, recordingSink);
            } finally {
                baking--;
            }
            return baked;
        }

        Map<RenderType, BakeBuilder> active = new LinkedHashMap<>();
        try {
            GeometrySink bakingSink = new GeometrySink() {
                @Override
                public void emit(RenderType type, Emitter body) {
                    VertexConsumer buffer = active.computeIfAbsent(type, BakeBuilder::new).consumer();
                    if (form == Form.SPLIT) {
                        QuadSplitter splitter = new QuadSplitter(buffer,
                                baked.recorded.computeIfAbsent(type, t -> new ArrayList<>()));
                        body.accept(scratch.last(), splitter);
                        splitter.finish();
                    } else {
                        body.accept(scratch.last(), buffer);
                    }
                }

                @Override
                public void emitBlockModel(RenderType type, BlockState state, int light, int overlay) {
                    throw new UnsupportedOperationException("Baked block models are not cached static geometry.");
                }
            };
            baking++;
            try {
                builder.accept(scratch, bakingSink);
            } finally {
                baking--;
            }
            for (Map.Entry<RenderType, BakeBuilder> entry : active.entrySet()) {
                MeshData mesh = entry.getValue().build();
                if (mesh == null) continue;
                //? if >=1.21.11 {
                /*baked.buffers.put(entry.getKey(), RetainedMesh.upload("soundstage static mesh", mesh));
                *///?} else {
                VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                buffer.bind();
                buffer.upload(mesh);
                VertexBuffer.unbind();
                baked.buffers.put(entry.getKey(), buffer);
                //?}
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("StaticMesh bake {} split across {} render types: {}",
                        bakeCount, baked.buffers.size(), baked.buffers.keySet());
            }
        } finally {
            active.values().forEach(BakeBuilder::close);
        }
        return baked;
    }

    public void close() {
        for (Baked baked : cache.values()) {
            retire(baked);
        }
        cache.clear();
    }

    private static final class Vertex {
        float x, y, z;
        int r = 255, g = 255, b = 255, a = 255;
        float u, vCoord;
        int overlayU, overlayV;
        int lightU, lightV;
        float nx, ny, nz;
    }

    private static final class QuadSplitter implements VertexConsumer {
        //? if >=1.21.11 {
        /*@Override
        public VertexConsumer setColor(int argb) {
            return setColor(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24);
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
        *///?}
        //? if >=26.3 {
        /*@Override
        public VertexConsumer setUv3(float u, float v) {
            return this;
        }
        *///?}

        private final VertexConsumer buffer;
        private final List<Vertex> recorded;
        private final Vertex[] quad = new Vertex[4];
        private int held;

        QuadSplitter(VertexConsumer buffer, List<Vertex> recorded) {
            this.buffer = buffer;
            this.recorded = recorded;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (held == 4) flush();
            Vertex vertex = new Vertex();
            vertex.x = x;
            vertex.y = y;
            vertex.z = z;
            quad[held++] = vertex;
            return this;
        }

        private Vertex current() {
            return quad[held - 1];
        }

        void finish() {
            if (held == 4) flush();
        }

        private void flush() {
            held = 0;
            boolean rigid = true;
            for (Vertex v : quad) {
                if (v.overlayU != RIGID_WEIGHT) {
                    rigid = false;
                    break;
                }
            }
            if (!rigid) {
                java.util.Collections.addAll(recorded, quad);
                return;
            }
            for (Vertex v : quad) {
                buffer.addVertex(v.x, v.y, v.z)
                        .setColor(v.r, v.g, v.b, v.a)
                        .setUv(v.u, v.vCoord)
                        .setUv1(NoOverlay.U, NoOverlay.V)
                        .setUv2(v.lightU, v.lightV)
                        .setNormal(v.nx, v.ny, v.nz);
            }
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            Vertex v = current();
            v.r = r;
            v.g = g;
            v.b = b;
            v.a = a;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            current().u = u;
            current().vCoord = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            current().overlayU = u;
            current().overlayV = v;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            current().lightU = u;
            current().lightV = v;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            current().nx = x;
            current().ny = y;
            current().nz = z;
            return this;
        }
    }

    private static final int RIGID_WEIGHT = 255;

    private static final class Recorder implements VertexConsumer {
        //? if >=1.21.11 {
        /*@Override
        public VertexConsumer setColor(int argb) {
            return setColor(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24);
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
        *///?}
        //? if >=26.3 {
        /*@Override
        public VertexConsumer setUv3(float u, float v) {
            return this;
        }
        *///?}

        private final List<Vertex> vertices;
        private Vertex current;

        Recorder(List<Vertex> vertices) {
            this.vertices = vertices;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            current = new Vertex();
            current.x = x;
            current.y = y;
            current.z = z;
            vertices.add(current);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            current.r = r;
            current.g = g;
            current.b = b;
            current.a = a;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            current.u = u;
            current.vCoord = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            current.overlayU = u;
            current.overlayV = v;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            current.lightU = u;
            current.lightV = v;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            current.nx = x;
            current.ny = y;
            current.nz = z;
            return this;
        }
    }
}
