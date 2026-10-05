package com.nachtraben.soundstage.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

//? if >=1.21.11 {
/*final class DisplacementPass {

    private static boolean usable = true;

    private static final int TRAVELS_PER_DRAW = 64;

    private static final java.nio.FloatBuffer TRAVEL_UPLOAD = org.lwjgl.BufferUtils.createFloatBuffer(TRAVELS_PER_DRAW);

    private static final java.util.Map<Integer, Program> PROGRAMS = new java.util.HashMap<>();

    private static long passes;
    private static long displacedCopies;
    private static long passNanos;
    private static boolean loggedFirstPass;

    private DisplacementPass() {
    }

    static boolean isUsable() {
        return usable && dev.armadeus.armalib.platform.client.render.RenderBackend.isOpenGl();
    }

    private static boolean enabled = true;

    static void setEnabled(boolean on) {
        enabled = on;
    }

    private static final class NoFeedback {
        static final boolean STAGED = com.nachtraben.soundstage.RigStaging.sessionStages("soundstage:woofer-excursion", "no-feedback");

        static {
            if (STAGED) {
                StaticMesh.LOGGER.warn("Soundstage: red control — the suspension is not displaced under a shader pack "
                        + "(rig soundstage:woofer-excursion, no-feedback)");
            }
        }
    }

    static long passes() {
        return passes;
    }

    static long displacedCopies() {
        return displacedCopies;
    }

    static long passNanos() {
        return passNanos;
    }

    static final class Source implements AutoCloseable {
        final dev.armadeus.armalib.platform.client.render.RetainedMesh rest;
        final int vertexCount;
        final int stride;
        final int positionOffset;
        private final float[] weights;
        private final java.util.function.IntFunction<dev.armadeus.armalib.platform.client.render.RetainedMesh> uploader;
        private final int weightBuffer;
        private int vertexArray;
        private dev.armadeus.armalib.platform.client.render.RetainedMesh copies;
        private float[] travels = new float[4];
        private int used;
        private boolean displaced;
        private boolean closed;

        private Source(dev.armadeus.armalib.platform.client.render.RetainedMesh rest, int vertexCount, int stride,
                       int positionOffset, float[] weights, int weightBuffer,
                       java.util.function.IntFunction<dev.armadeus.armalib.platform.client.render.RetainedMesh> uploader) {
            this.rest = rest;
            this.vertexCount = vertexCount;
            this.stride = stride;
            this.positionOffset = positionOffset;
            this.weights = weights;
            this.weightBuffer = weightBuffer;
            this.uploader = uploader;
        }

        int slotFor(float travel) {
            for (int i = 0; i < used; i++) {
                if (Float.floatToIntBits(travels[i]) == Float.floatToIntBits(travel)) return i;
            }
            if (used == travels.length) travels = java.util.Arrays.copyOf(travels, used * 2);
            travels[used] = travel;
            int slot = used++;
            if (copies == null || copies.copies() < used) {
                int capacity = Math.max(1, Integer.highestOneBit(used - 1) << 1);
                if (copies != null) copies.close();
                copies = uploader.apply(capacity);
            }
            return slot;
        }

        boolean displaced() {
            return displaced;
        }

        dev.armadeus.armalib.platform.client.render.RetainedMesh copies() {
            return copies;
        }

        boolean hasTravels() {
            return used > 0;
        }

        void endFrame() {
            used = 0;
            displaced = false;
        }

        float weight(int vertex) {
            return weights[vertex];
        }

        boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (vertexArray != 0) org.lwjgl.opengl.GL30.glDeleteVertexArrays(vertexArray);
            if (weightBuffer != 0) org.lwjgl.opengl.GL15.glDeleteBuffers(weightBuffer);
            vertexArray = 0;
            rest.close();
            if (copies != null) copies.close();
            copies = null;
        }
    }

    static Source create(java.util.function.IntFunction<dev.armadeus.armalib.platform.client.render.RetainedMesh> uploader,
                         float[] weights, int stride, int positionOffset) {
        dev.armadeus.armalib.platform.client.render.RetainedMesh rest = uploader.apply(1);
        int group = group(stride);
        if (rest.vertexCount() != weights.length || rest.vertexCount() % group != 0 || rest.glVertexBuffer() == 0) {
            rest.close();
            return null;
        }
        java.nio.FloatBuffer data = org.lwjgl.system.MemoryUtil.memAllocFloat(weights.length);
        int previous = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING);
        int weightBuffer = 0;
        try {
            data.put(weights).flip();
            weightBuffer = org.lwjgl.opengl.GL15.glGenBuffers();
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, weightBuffer);
            org.lwjgl.opengl.GL15.glBufferData(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, data, org.lwjgl.opengl.GL15.GL_STATIC_DRAW);
        } finally {
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, previous);
            org.lwjgl.system.MemoryUtil.memFree(data);
        }
        return new Source(rest, rest.vertexCount(), stride, positionOffset, weights.clone(), weightBuffer, uploader);
    }

    static int group(int stride) {
        return stride % 4 == 0 ? 1 : stride % 2 == 0 ? 2 : 4;
    }

    static void displaceAll(java.util.List<Source> sources) {
        if (!isUsable() || !enabled || NoFeedback.STAGED) return;
        long startedAt = System.nanoTime();
        int program0 = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
        int vertexArray0 = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_VERTEX_ARRAY_BINDING);
        int arrayBuffer0 = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING);
        int feedbackBuffer0 = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING);
        boolean discarding0 = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL30.GL_RASTERIZER_DISCARD);
        boolean feedbackBegun = false;
        try {
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL30.GL_RASTERIZER_DISCARD);
            for (Source source : sources) {
                if (source.closed || source.used == 0 || source.copies == null) continue;
                int target = source.copies.glVertexBuffer();
                int input = source.rest.glVertexBuffer();
                if (target == 0 || input == 0) continue;
                Program program = program(source.stride, source.positionOffset);
                if (program == null) {
                    usable = false;
                    return;
                }
                org.lwjgl.opengl.GL20.glUseProgram(program.id);
                org.lwjgl.opengl.GL30.glBindVertexArray(vertexArray(source, program, input));
                int points = source.vertexCount / program.group;
                long copyBytes = (long) source.vertexCount * source.stride;
                for (int base = 0; base < source.used; base += TRAVELS_PER_DRAW) {
                    int count = Math.min(TRAVELS_PER_DRAW, source.used - base);
                    TRAVEL_UPLOAD.clear();
                    TRAVEL_UPLOAD.put(source.travels, base, count).flip();
                    org.lwjgl.opengl.GL20.glUniform1fv(program.travel, TRAVEL_UPLOAD);
                    org.lwjgl.opengl.GL30.glBindBufferRange(org.lwjgl.opengl.GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, target,
                            base * copyBytes, count * copyBytes);
                    org.lwjgl.opengl.GL30.glBeginTransformFeedback(org.lwjgl.opengl.GL11.GL_POINTS);
                    feedbackBegun = true;
                    org.lwjgl.opengl.GL31.glDrawArraysInstanced(org.lwjgl.opengl.GL11.GL_POINTS, 0, points, count);
                    org.lwjgl.opengl.GL30.glEndTransformFeedback();
                    feedbackBegun = false;
                }
                source.displaced = true;
                displacedCopies += source.used;
            }
            passes++;
            if (!loggedFirstPass) {
                loggedFirstPass = true;
                StaticMesh.LOGGER.info("Soundstage: driver suspension displaced by transform feedback under a shader "
                        + "pack; the pack's program draws it");
            }
        } catch (Throwable t) {
            usable = false;
            for (Source source : sources) source.displaced = false;
            StaticMesh.LOGGER.warn("Soundstage: transform feedback failed; the driver suspension is drawn at rest "
                    + "under a shader pack for the rest of the session", t);
        } finally {
            if (feedbackBegun) org.lwjgl.opengl.GL30.glEndTransformFeedback();
            if (!discarding0) org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL30.GL_RASTERIZER_DISCARD);
            org.lwjgl.opengl.GL30.glBindBufferBase(org.lwjgl.opengl.GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, 0);
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL30.GL_TRANSFORM_FEEDBACK_BUFFER, feedbackBuffer0);
            org.lwjgl.opengl.GL30.glBindVertexArray(vertexArray0);
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, arrayBuffer0);
            org.lwjgl.opengl.GL20.glUseProgram(program0);
            passNanos += System.nanoTime() - startedAt;
        }
    }

    private static int vertexArray(Source source, Program program, int input) {
        if (source.vertexArray != 0) return source.vertexArray;
        int array = org.lwjgl.opengl.GL30.glGenVertexArrays();
        org.lwjgl.opengl.GL30.glBindVertexArray(array);
        int groupBytes = program.group * source.stride;
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, input);
        for (int attribute = 0; attribute < program.wordAttributes; attribute++) {
            int size = Math.min(4, program.words - attribute * 4);
            org.lwjgl.opengl.GL20.glEnableVertexAttribArray(attribute);
            org.lwjgl.opengl.GL30.glVertexAttribIPointer(attribute, size, org.lwjgl.opengl.GL11.GL_UNSIGNED_INT, groupBytes,
                    attribute * 16L);
        }
        int weight = program.wordAttributes;
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, source.weightBuffer);
        org.lwjgl.opengl.GL20.glEnableVertexAttribArray(weight);
        org.lwjgl.opengl.GL20.glVertexAttribPointer(weight, program.group, org.lwjgl.opengl.GL11.GL_FLOAT, false,
                program.group * 4, 0L);
        source.vertexArray = array;
        return array;
    }

    private record Program(int id, int travel, int group, int words, int wordAttributes) {}

    private static Program program(int stride, int positionOffset) {
        int key = stride << 16 | positionOffset;
        Program program = PROGRAMS.get(key);
        if (program == null && !PROGRAMS.containsKey(key)) {
            program = build(stride, positionOffset);
            PROGRAMS.put(key, program);
        }
        return program;
    }

    private static Program build(int stride, int positionOffset) {
        int group = group(stride);
        int words = group * stride / 4;
        int wordAttributes = (words + 3) / 4;
        if (wordAttributes + 1 > 16 || words > 64) {
            StaticMesh.LOGGER.warn("Soundstage: a {}-byte vertex is too wide for the displacement pass", stride);
            return null;
        }
        String source = source(group, stride, positionOffset, words, wordAttributes);
        int shader = 0;
        int id = 0;
        try {
            shader = org.lwjgl.opengl.GL20.glCreateShader(org.lwjgl.opengl.GL20.GL_VERTEX_SHADER);
            org.lwjgl.opengl.GL20.glShaderSource(shader, source);
            org.lwjgl.opengl.GL20.glCompileShader(shader);
            if (org.lwjgl.opengl.GL20.glGetShaderi(shader, org.lwjgl.opengl.GL20.GL_COMPILE_STATUS) == org.lwjgl.opengl.GL11.GL_FALSE) {
                StaticMesh.LOGGER.warn("Soundstage: the displacement pass failed to compile: {}",
                        org.lwjgl.opengl.GL20.glGetShaderInfoLog(shader));
                return null;
            }
            id = org.lwjgl.opengl.GL20.glCreateProgram();
            org.lwjgl.opengl.GL20.glAttachShader(id, shader);
            for (int attribute = 0; attribute < wordAttributes; attribute++) {
                org.lwjgl.opengl.GL20.glBindAttribLocation(id, attribute, "aWords" + attribute);
            }
            org.lwjgl.opengl.GL20.glBindAttribLocation(id, wordAttributes, "aWeight");
            CharSequence[] varyings = new CharSequence[wordAttributes];
            for (int attribute = 0; attribute < wordAttributes; attribute++) varyings[attribute] = "oWords" + attribute;
            org.lwjgl.opengl.GL30.glTransformFeedbackVaryings(id, varyings, org.lwjgl.opengl.GL30.GL_INTERLEAVED_ATTRIBS);
            org.lwjgl.opengl.GL20.glLinkProgram(id);
            if (org.lwjgl.opengl.GL20.glGetProgrami(id, org.lwjgl.opengl.GL20.GL_LINK_STATUS) == org.lwjgl.opengl.GL11.GL_FALSE) {
                StaticMesh.LOGGER.warn("Soundstage: the displacement pass failed to link: {}",
                        org.lwjgl.opengl.GL20.glGetProgramInfoLog(id));
                org.lwjgl.opengl.GL20.glDeleteProgram(id);
                return null;
            }
            int travel = org.lwjgl.opengl.GL20.glGetUniformLocation(id, "Travel");
            StaticMesh.LOGGER.info("Soundstage: displacement pass ready for {}-byte vertices, Position at {} "
                    + "({} a run, {} words; program {})", stride, positionOffset, group, words, id);
            return new Program(id, travel, group, words, wordAttributes);
        } catch (Throwable t) {
            StaticMesh.LOGGER.warn("Soundstage: the displacement pass is unavailable", t);
            if (id != 0) org.lwjgl.opengl.GL20.glDeleteProgram(id);
            return null;
        } finally {
            if (shader != 0) org.lwjgl.opengl.GL20.glDeleteShader(shader);
        }
    }

    private static String source(int group, int stride, int positionOffset, int words, int wordAttributes) {
        StringBuilder glsl = new StringBuilder("#version 330 core\n\n");
        for (int attribute = 0; attribute < wordAttributes; attribute++) {
            glsl.append("in ").append(uintType(Math.min(4, words - attribute * 4))).append(" aWords").append(attribute).append(";\n");
        }
        glsl.append("in ").append(group == 1 ? "float" : "vec" + group).append(" aWeight;\n");
        glsl.append("uniform float Travel[").append(TRAVELS_PER_DRAW).append("];\n");
        for (int attribute = 0; attribute < wordAttributes; attribute++) {
            glsl.append("flat out ").append(uintType(Math.min(4, words - attribute * 4))).append(" oWords").append(attribute).append(";\n");
        }
        glsl.append("\nvoid main() {\n    uint w[").append(words).append("];\n");
        for (int word = 0; word < words; word++) {
            glsl.append("    w[").append(word).append("] = aWords").append(word / 4).append(component(words, word)).append(";\n");
        }
        glsl.append("    float travel = Travel[gl_InstanceID];\n");
        for (int vertex = 0; vertex < group; vertex++) {
            int at = vertex * stride + positionOffset + 8;
            int word = at / 4;
            int shift = (at % 4) * 8;
            String weight = group == 1 ? "aWeight" : "aWeight." + "xyzw".charAt(vertex);
            glsl.append("    {\n        float d = ").append(weight).append(" * travel;\n");
            if (shift == 0) {
                glsl.append("        w[").append(word).append("] = floatBitsToUint(uintBitsToFloat(w[").append(word).append("]) + d);\n");
            } else {
                String low = "((1u << " + shift + "u) - 1u)";
                glsl.append("        uint bits = (w[").append(word).append("] >> ").append(shift).append("u) | (w[")
                        .append(word + 1).append("] << ").append(32 - shift).append("u);\n");
                glsl.append("        uint moved = floatBitsToUint(uintBitsToFloat(bits) + d);\n");
                glsl.append("        w[").append(word).append("] = (w[").append(word).append("] & ").append(low)
                        .append(") | (moved << ").append(shift).append("u);\n");
                glsl.append("        w[").append(word + 1).append("] = (w[").append(word + 1).append("] & ~").append(low)
                        .append(") | (moved >> ").append(32 - shift).append("u);\n");
            }
            glsl.append("    }\n");
        }
        for (int attribute = 0; attribute < wordAttributes; attribute++) {
            int size = Math.min(4, words - attribute * 4);
            glsl.append("    oWords").append(attribute).append(" = ").append(uintType(size)).append("(");
            for (int i = 0; i < size; i++) {
                if (i > 0) glsl.append(", ");
                glsl.append("w[").append(attribute * 4 + i).append("]");
            }
            glsl.append(");\n");
        }
        glsl.append("}\n");
        return glsl.toString();
    }

    private static String uintType(int size) {
        return size == 1 ? "uint" : "uvec" + size;
    }

    private static String component(int words, int word) {
        int attribute = word / 4;
        int size = Math.min(4, words - attribute * 4);
        return size == 1 ? "" : "." + "xyzw".charAt(word % 4);
    }

    record Probe(float requested, float applied, boolean displaced, int vertices) {}

    static Probe probe(Source source, int slot, float requested) {
        int restName = source.rest.glVertexBuffer();
        boolean displaced = source.displaced && source.copies != null;
        int drawnName = displaced ? source.copies.glVertexBuffer() : restName;
        long drawnOffset = displaced ? (long) slot * source.vertexCount * source.stride : 0L;
        int bytes = source.vertexCount * source.stride;
        java.nio.ByteBuffer rest = org.lwjgl.system.MemoryUtil.memAlloc(bytes);
        java.nio.ByteBuffer drawn = org.lwjgl.system.MemoryUtil.memAlloc(bytes);
        int copyRead0 = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL31.GL_COPY_READ_BUFFER);
        try {
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL31.GL_COPY_READ_BUFFER, restName);
            org.lwjgl.opengl.GL15.glGetBufferSubData(org.lwjgl.opengl.GL31.GL_COPY_READ_BUFFER, 0L, rest);
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL31.GL_COPY_READ_BUFFER, drawnName);
            org.lwjgl.opengl.GL15.glGetBufferSubData(org.lwjgl.opengl.GL31.GL_COPY_READ_BUFFER, drawnOffset, drawn);
            float[] moved = new float[source.vertexCount];
            int n = 0;
            for (int vertex = 0; vertex < source.vertexCount; vertex++) {
                float weight = source.weight(vertex);
                if (weight < 0.5f) continue;
                int at = vertex * source.stride + source.positionOffset + 8;
                moved[n++] = (drawn.getFloat(at) - rest.getFloat(at)) / weight;
            }
            java.util.Arrays.sort(moved, 0, n);
            return new Probe(requested, n == 0 ? 0f : moved[n / 2], displaced, n);
        } finally {
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL31.GL_COPY_READ_BUFFER, copyRead0);
            org.lwjgl.system.MemoryUtil.memFree(rest);
            org.lwjgl.system.MemoryUtil.memFree(drawn);
        }
    }
}
*///?} else {
final class DisplacementPass {

    private static boolean usable = true;

    private static final int TRAVELS_PER_DRAW = 64;

    private static final java.nio.FloatBuffer TRAVEL_UPLOAD = org.lwjgl.BufferUtils.createFloatBuffer(TRAVELS_PER_DRAW);

    private static final java.util.Map<Integer, Program> PROGRAMS = new java.util.HashMap<>();

    private static long passes;
    private static long displacedCopies;
    private static long passNanos;
    private static boolean loggedFirstPass;

    private DisplacementPass() {
    }

    static boolean isUsable() {
        return usable && enabled && !NoFeedback.STAGED
                && dev.armadeus.armalib.platform.client.render.RenderBackend.isOpenGl();
    }

    private static boolean enabled = true;

    static void setEnabled(boolean on) {
        enabled = on;
    }

    private static final class NoFeedback {
        static final boolean STAGED = com.nachtraben.soundstage.RigStaging.sessionStages("soundstage:woofer-excursion", "no-feedback");

        static {
            if (STAGED) {
                StaticMesh.LOGGER.warn("Soundstage: red control — the suspension is not displaced under a shader pack "
                        + "(rig soundstage:woofer-excursion, no-feedback)");
            }
        }
    }

    static long passes() {
        return passes;
    }

    static long displacedCopies() {
        return displacedCopies;
    }

    static long passNanos() {
        return passNanos;
    }

    static final class Source implements AutoCloseable {
        final int restVbo;
        final int weightVbo;
        final int vertexCount;
        final int stride;
        final int positionOffset;
        final com.mojang.blaze3d.vertex.VertexFormat format;
        private final float[] weights;
        private int feedbackVao;
        private int copiesVbo;
        private int copiesCapacity;
        private int drawVao;
        private int restVao;
        private float[] travels = new float[4];
        private int used;
        private boolean displaced;
        private boolean closed;

        private Source(int restVbo, int weightVbo, int vertexCount, int stride, int positionOffset,
                       com.mojang.blaze3d.vertex.VertexFormat format, float[] weights) {
            this.restVbo = restVbo;
            this.weightVbo = weightVbo;
            this.vertexCount = vertexCount;
            this.stride = stride;
            this.positionOffset = positionOffset;
            this.format = format;
            this.weights = weights;
        }

        int slotFor(float travel) {
            for (int i = 0; i < used; i++) {
                if (Float.floatToIntBits(travels[i]) == Float.floatToIntBits(travel)) return i;
            }
            if (used == travels.length) travels = java.util.Arrays.copyOf(travels, used * 2);
            travels[used] = travel;
            int slot = used++;
            if (copiesVbo == 0 || copiesCapacity < used) {
                int capacity = Math.max(1, Integer.highestOneBit(used - 1) << 1);
                growCopies(capacity);
            }
            return slot;
        }

        private void growCopies(int capacity) {
            int previous = org.lwjgl.opengl.GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            int fresh = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, fresh);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, (long) capacity * vertexCount * stride, GL15.GL_STREAM_COPY);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previous);
            if (copiesVbo != 0) GL15.glDeleteBuffers(copiesVbo);
            copiesVbo = fresh;
            copiesCapacity = capacity;
            if (drawVao != 0) {
                GL30.glDeleteVertexArrays(drawVao);
                drawVao = 0;
            }
        }

        boolean displaced() {
            return displaced;
        }

        boolean hasTravels() {
            return used > 0;
        }

        void endFrame() {
            used = 0;
            displaced = false;
        }

        float weight(int vertex) {
            return weights[vertex];
        }

        boolean isClosed() {
            return closed;
        }

        private int feedbackVao(Program program) {
            if (feedbackVao != 0) return feedbackVao;
            feedbackVao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(feedbackVao);
            int groupBytes = program.group * stride;
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, restVbo);
            for (int attribute = 0; attribute < program.wordAttributes; attribute++) {
                int size = Math.min(4, program.words - attribute * 4);
                GL20.glEnableVertexAttribArray(attribute);
                GL30.glVertexAttribIPointer(attribute, size, GL11.GL_UNSIGNED_INT, groupBytes, attribute * 16L);
            }
            int weight = program.wordAttributes;
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, weightVbo);
            GL20.glEnableVertexAttribArray(weight);
            GL20.glVertexAttribPointer(weight, program.group, GL11.GL_FLOAT, false, program.group * 4, 0L);
            GL30.glBindVertexArray(0);
            return feedbackVao;
        }

        private int formatVao(int buffer) {
            int vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            int index = 0;
            for (com.mojang.blaze3d.vertex.VertexFormatElement element : format.getElements()) {
                //? if >=1.21 {
                int offset = format.getOffset(element);
                //?} else {
                /*int offset = offsetOf(format, element);
                if (element.getUsage() == com.mojang.blaze3d.vertex.VertexFormatElement.Usage.PADDING) {
                    index++;
                    continue;
                }
                *///?}
                setupElement(element, index, offset, stride);
                GL20.glEnableVertexAttribArray(index);
                index++;
            }
            GL30.glBindVertexArray(0);
            return vao;
        }

        int drawVao() {
            if (drawVao == 0) drawVao = formatVao(copiesVbo);
            return drawVao;
        }

        int restVao() {
            if (restVao == 0) restVao = formatVao(restVbo);
            return restVao;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (feedbackVao != 0) GL30.glDeleteVertexArrays(feedbackVao);
            if (drawVao != 0) GL30.glDeleteVertexArrays(drawVao);
            if (restVao != 0) GL30.glDeleteVertexArrays(restVao);
            if (copiesVbo != 0) GL15.glDeleteBuffers(copiesVbo);
            feedbackVao = 0;
            drawVao = 0;
            restVao = 0;
            copiesVbo = 0;
            GL15.glDeleteBuffers(restVbo);
            GL15.glDeleteBuffers(weightVbo);
        }
    }

    static Source create(MeshData mesh, float[] weights) {
        com.mojang.blaze3d.vertex.VertexFormat format = mesh.drawState().format();
        //? if >=1.21 {
        int positionOffset = format.getOffset(com.mojang.blaze3d.vertex.VertexFormatElement.POSITION);
        //?} else {
        /*int positionOffset = offsetOf(format, com.mojang.blaze3d.vertex.DefaultVertexFormat.ELEMENT_POSITION);
        *///?}
        int stride = format.getVertexSize();
        int vertexCount = mesh.drawState().vertexCount();
        int group = group(stride);
        if (vertexCount != weights.length || vertexCount % group != 0) {
            return null;
        }
        ByteBuffer bytes = mesh.vertexBuffer();
        int previous = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int restVbo = GL15.glGenBuffers();
        int weightVbo = 0;
        try {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, restVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, bytes, GL15.GL_STATIC_DRAW);
            weightVbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, weightVbo);
            FloatBuffer weightData = org.lwjgl.BufferUtils.createFloatBuffer(weights.length);
            weightData.put(weights).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, weightData, GL15.GL_STATIC_DRAW);
        } finally {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previous);
        }
        return new Source(restVbo, weightVbo, vertexCount, stride, positionOffset, format, weights.clone());
    }

    static int group(int stride) {
        return stride % 4 == 0 ? 1 : stride % 2 == 0 ? 2 : 4;
    }

    static void displaceAll(java.util.List<Source> sources) {
        if (!isUsable()) return;
        long startedAt = System.nanoTime();
        int program0 = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int vertexArray0 = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int arrayBuffer0 = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int feedbackBuffer0 = GL11.glGetInteger(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING);
        boolean discarding0 = GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD);
        boolean feedbackBegun = false;
        try {
            GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
            for (Source source : sources) {
                if (source.closed || source.used == 0 || source.copiesVbo == 0) continue;
                Program program = program(source.stride, source.positionOffset);
                if (program == null) {
                    usable = false;
                    return;
                }
                GL20.glUseProgram(program.id);
                GL30.glBindVertexArray(source.feedbackVao(program));
                int points = source.vertexCount / program.group;
                long copyBytes = (long) source.vertexCount * source.stride;
                for (int base = 0; base < source.used; base += TRAVELS_PER_DRAW) {
                    int count = Math.min(TRAVELS_PER_DRAW, source.used - base);
                    TRAVEL_UPLOAD.clear();
                    TRAVEL_UPLOAD.put(source.travels, base, count).flip();
                    GL20.glUniform1fv(program.travel, TRAVEL_UPLOAD);
                    GL30.glBindBufferRange(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, source.copiesVbo,
                            base * copyBytes, count * copyBytes);
                    GL30.glBeginTransformFeedback(GL11.GL_POINTS);
                    feedbackBegun = true;
                    GL31.glDrawArraysInstanced(GL11.GL_POINTS, 0, points, count);
                    GL30.glEndTransformFeedback();
                    feedbackBegun = false;
                }
                source.displaced = true;
                displacedCopies += source.used;
            }
            passes++;
            if (!loggedFirstPass) {
                loggedFirstPass = true;
                StaticMesh.LOGGER.info("Soundstage: driver suspension displaced by transform feedback under a shader "
                        + "pack; the pack's program draws it");
            }
        } catch (Throwable t) {
            usable = false;
            for (Source source : sources) source.displaced = false;
            StaticMesh.LOGGER.warn("Soundstage: transform feedback failed; the driver suspension is drawn at rest "
                    + "under a shader pack for the rest of the session", t);
        } finally {
            if (feedbackBegun) GL30.glEndTransformFeedback();
            if (!discarding0) GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
            GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, 0);
            GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, feedbackBuffer0);
            GL30.glBindVertexArray(vertexArray0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer0);
            GL20.glUseProgram(program0);
            passNanos += System.nanoTime() - startedAt;
        }
    }

    private record Program(int id, int travel, int group, int words, int wordAttributes) {}

    private static Program program(int stride, int positionOffset) {
        int key = stride << 16 | positionOffset;
        Program program = PROGRAMS.get(key);
        if (program == null && !PROGRAMS.containsKey(key)) {
            program = build(stride, positionOffset);
            PROGRAMS.put(key, program);
        }
        return program;
    }

    private static Program build(int stride, int positionOffset) {
        int group = group(stride);
        int words = group * stride / 4;
        int wordAttributes = (words + 3) / 4;
        if (wordAttributes + 1 > 16 || words > 64) {
            StaticMesh.LOGGER.warn("Soundstage: a {}-byte vertex is too wide for the displacement pass", stride);
            return null;
        }
        String source = source(group, stride, positionOffset, words, wordAttributes);
        int shader = 0;
        int id = 0;
        try {
            shader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                StaticMesh.LOGGER.warn("Soundstage: the displacement pass failed to compile: {}",
                        GL20.glGetShaderInfoLog(shader));
                return null;
            }
            id = GL20.glCreateProgram();
            GL20.glAttachShader(id, shader);
            for (int attribute = 0; attribute < wordAttributes; attribute++) {
                GL20.glBindAttribLocation(id, attribute, "aWords" + attribute);
            }
            GL20.glBindAttribLocation(id, wordAttributes, "aWeight");
            CharSequence[] varyings = new CharSequence[wordAttributes];
            for (int attribute = 0; attribute < wordAttributes; attribute++) varyings[attribute] = "oWords" + attribute;
            GL30.glTransformFeedbackVaryings(id, varyings, GL30.GL_INTERLEAVED_ATTRIBS);
            GL20.glLinkProgram(id);
            if (GL20.glGetProgrami(id, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                StaticMesh.LOGGER.warn("Soundstage: the displacement pass failed to link: {}",
                        GL20.glGetProgramInfoLog(id));
                GL20.glDeleteProgram(id);
                return null;
            }
            int travel = GL20.glGetUniformLocation(id, "Travel");
            StaticMesh.LOGGER.info("Soundstage: displacement pass ready for {}-byte vertices, Position at {} "
                    + "({} a run, {} words; program {})", stride, positionOffset, group, words, id);
            return new Program(id, travel, group, words, wordAttributes);
        } catch (Throwable t) {
            StaticMesh.LOGGER.warn("Soundstage: the displacement pass is unavailable", t);
            if (id != 0) GL20.glDeleteProgram(id);
            return null;
        } finally {
            if (shader != 0) GL20.glDeleteShader(shader);
        }
    }

    private static String source(int group, int stride, int positionOffset, int words, int wordAttributes) {
        StringBuilder glsl = new StringBuilder("#version 330 core\n\n");
        for (int attribute = 0; attribute < wordAttributes; attribute++) {
            glsl.append("in ").append(uintType(Math.min(4, words - attribute * 4))).append(" aWords").append(attribute).append(";\n");
        }
        glsl.append("in ").append(group == 1 ? "float" : "vec" + group).append(" aWeight;\n");
        glsl.append("uniform float Travel[").append(TRAVELS_PER_DRAW).append("];\n");
        for (int attribute = 0; attribute < wordAttributes; attribute++) {
            glsl.append("flat out ").append(uintType(Math.min(4, words - attribute * 4))).append(" oWords").append(attribute).append(";\n");
        }
        glsl.append("\nvoid main() {\n    uint w[").append(words).append("];\n");
        for (int word = 0; word < words; word++) {
            glsl.append("    w[").append(word).append("] = aWords").append(word / 4).append(component(words, word)).append(";\n");
        }
        glsl.append("    float travel = Travel[gl_InstanceID];\n");
        for (int vertex = 0; vertex < group; vertex++) {
            int at = vertex * stride + positionOffset + 8;
            int word = at / 4;
            int shift = (at % 4) * 8;
            String weight = group == 1 ? "aWeight" : "aWeight." + "xyzw".charAt(vertex);
            glsl.append("    {\n        float d = ").append(weight).append(" * travel;\n");
            if (shift == 0) {
                glsl.append("        w[").append(word).append("] = floatBitsToUint(uintBitsToFloat(w[").append(word).append("]) + d);\n");
            } else {
                String low = "((1u << " + shift + "u) - 1u)";
                glsl.append("        uint bits = (w[").append(word).append("] >> ").append(shift).append("u) | (w[")
                        .append(word + 1).append("] << ").append(32 - shift).append("u);\n");
                glsl.append("        uint moved = floatBitsToUint(uintBitsToFloat(bits) + d);\n");
                glsl.append("        w[").append(word).append("] = (w[").append(word).append("] & ").append(low)
                        .append(") | (moved << ").append(shift).append("u);\n");
                glsl.append("        w[").append(word + 1).append("] = (w[").append(word + 1).append("] & ~").append(low)
                        .append(") | (moved >> ").append(32 - shift).append("u);\n");
            }
            glsl.append("    }\n");
        }
        for (int attribute = 0; attribute < wordAttributes; attribute++) {
            int size = Math.min(4, words - attribute * 4);
            glsl.append("    oWords").append(attribute).append(" = ").append(uintType(size)).append("(");
            for (int i = 0; i < size; i++) {
                if (i > 0) glsl.append(", ");
                glsl.append("w[").append(attribute * 4 + i).append("]");
            }
            glsl.append(");\n");
        }
        glsl.append("}\n");
        return glsl.toString();
    }

    private static String uintType(int size) {
        return size == 1 ? "uint" : "uvec" + size;
    }

    private static String component(int words, int word) {
        int attribute = word / 4;
        int size = Math.min(4, words - attribute * 4);
        return size == 1 ? "" : "." + "xyzw".charAt(word % 4);
    }

    private static void setupElement(VertexFormatElement element, int index, int offset, int stride) {
        //? if >=26.2 {
        /*throw new UnsupportedOperationException("transform feedback is not ported to 26.2");
        *///?} else {
        //? if >=1.21 {
        int count = element.count();
        switch (element.type()) {
        //?} else {
        /*int count = element.getCount();
        switch (element.getType()) {
        *///?}
            case FLOAT -> GL20.glVertexAttribPointer(index, count, GL11.GL_FLOAT, false, stride, offset);
            case UBYTE -> GL20.glVertexAttribPointer(index, count, GL11.GL_UNSIGNED_BYTE, true, stride, offset);
            case BYTE -> GL20.glVertexAttribPointer(index, count, GL11.GL_BYTE, true, stride, offset);
            case USHORT -> GL30.glVertexAttribIPointer(index, count, GL11.GL_UNSIGNED_SHORT, stride, offset);
            case SHORT -> GL30.glVertexAttribIPointer(index, count, GL11.GL_SHORT, stride, offset);
            case UINT -> GL30.glVertexAttribIPointer(index, count, GL11.GL_UNSIGNED_INT, stride, offset);
            case INT -> GL30.glVertexAttribIPointer(index, count, GL11.GL_INT, stride, offset);
        }
        //?}
    }

    //? if <1.21 {
    /*private static int offsetOf(VertexFormat format, VertexFormatElement element) {
        int offset = 0;
        for (VertexFormatElement e : format.getElements()) {
            if (e.equals(element)) return offset;
            offset += e.getByteSize();
        }
        throw new IllegalArgumentException("no " + element + " in " + format);
    }
    *///?}

    static void bindForDraw(Source source, int slot, boolean displaced) {
        GL30.glBindVertexArray(displaced ? source.drawVao() : source.restVao());
    }

    static int baseVertex(Source source, int slot, boolean displaced) {
        return displaced ? slot * source.vertexCount : 0;
    }

    static void drawElementsBaseVertex(int glMode, int indexCount, int glIndexType, int baseVertex) {
        GL32.glDrawElementsBaseVertex(glMode, indexCount, glIndexType, 0L, baseVertex);
    }

    static void unbind() {
        GL30.glBindVertexArray(0);
        com.mojang.blaze3d.vertex.BufferUploader.invalidate();
        RenderSystem.assertOnRenderThread();
    }

    static float[] probe(Source source, int slot, float requested) {
        boolean displaced = source.displaced && source.copiesVbo != 0;
        int drawnVbo = displaced ? source.copiesVbo : source.restVbo;
        long drawnOffset = displaced ? (long) slot * source.vertexCount * source.stride : 0L;
        int bytes = source.vertexCount * source.stride;
        ByteBuffer rest = org.lwjgl.system.MemoryUtil.memAlloc(bytes);
        ByteBuffer drawn = org.lwjgl.system.MemoryUtil.memAlloc(bytes);
        int bound = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, source.restVbo);
            GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, rest);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, drawnVbo);
            GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, drawnOffset, drawn);
            float[] moved = new float[source.vertexCount];
            int n = 0;
            for (int vertex = 0; vertex < source.vertexCount; vertex++) {
                float weight = source.weight(vertex);
                if (weight < 0.5f) continue;
                int at = vertex * source.stride + source.positionOffset + 8;
                moved[n++] = (drawn.getFloat(at) - rest.getFloat(at)) / weight;
            }
            java.util.Arrays.sort(moved, 0, n);
            return new float[]{requested, n == 0 ? 0f : moved[n / 2], displaced ? 1f : 0f, n};
        } finally {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bound);
            org.lwjgl.system.MemoryUtil.memFree(rest);
            org.lwjgl.system.MemoryUtil.memFree(drawn);
        }
    }
}
//?}
