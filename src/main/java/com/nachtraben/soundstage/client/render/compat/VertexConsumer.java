package com.nachtraben.soundstage.client.render.compat;

//? if <1.21 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public interface VertexConsumer {
    VertexConsumer addVertex(float x, float y, float z);

    VertexConsumer setColor(int red, int green, int blue, int alpha);

    VertexConsumer setUv(float u, float v);

    VertexConsumer setUv1(int u, int v);

    VertexConsumer setUv2(int u, int v);

    VertexConsumer setNormal(float normalX, float normalY, float normalZ);

    default void addVertex(float x, float y, float z, int color, float u, float v, int packedOverlay, int packedLight,
                           float normalX, float normalY, float normalZ) {
        addVertex(x, y, z);
        setColor(color);
        setUv(u, v);
        setOverlay(packedOverlay);
        setLight(packedLight);
        setNormal(normalX, normalY, normalZ);
    }

    default VertexConsumer setColor(float red, float green, float blue, float alpha) {
        return setColor((int) (red * 255.0F), (int) (green * 255.0F), (int) (blue * 255.0F), (int) (alpha * 255.0F));
    }

    default VertexConsumer setColor(int argb) {
        return setColor(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24);
    }

    default VertexConsumer setLight(int packedLight) {
        return setUv2(packedLight & 65535, packedLight >> 16 & 65535);
    }

    default VertexConsumer setOverlay(int packedOverlay) {
        return setUv1(packedOverlay & 65535, packedOverlay >> 16 & 65535);
    }

    default VertexConsumer addVertex(PoseStack.Pose pose, float x, float y, float z) {
        return addVertex(pose.pose(), x, y, z);
    }

    default VertexConsumer addVertex(Matrix4f pose, float x, float y, float z) {
        Vector3f position = pose.transformPosition(x, y, z, new Vector3f());
        return addVertex(position.x(), position.y(), position.z());
    }

    default VertexConsumer setNormal(PoseStack.Pose pose, float normalX, float normalY, float normalZ) {
        Vector3f normal = pose.normal().transform(new Vector3f(normalX, normalY, normalZ));
        return setNormal(normal.x(), normal.y(), normal.z());
    }

    static Adapter of(com.mojang.blaze3d.vertex .VertexConsumer target) {
        return new Adapter(target);
    }

    final class Adapter implements VertexConsumer {
        private final com.mojang.blaze3d.vertex .VertexConsumer target;
        private boolean open;
        private float x, y, z;
        private int red = 255, green = 255, blue = 255, alpha = 255;
        private float u, v;
        private int overlayU, overlayV;
        private int lightU, lightV;
        private float normalX, normalY = 1f, normalZ;

        Adapter(com.mojang.blaze3d.vertex .VertexConsumer target) {
            this.target = target;
        }

        public com.mojang.blaze3d.vertex .VertexConsumer unwrap() {
            finish();
            return target;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            finish();
            open = true;
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            overlayU = u;
            overlayV = v;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            lightU = u;
            lightV = v;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            normalX = x;
            normalY = y;
            normalZ = z;
            return this;
        }

        public void finish() {
            if (!open) return;
            open = false;
            target.vertex(x, y, z).color(red, green, blue, alpha).uv(u, v).overlayCoords(overlayU, overlayV)
                    .uv2(lightU, lightV).normal(normalX, normalY, normalZ).endVertex();
        }
    }
}
*///?}
