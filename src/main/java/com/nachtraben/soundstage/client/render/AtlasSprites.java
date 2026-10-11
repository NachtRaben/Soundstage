package com.nachtraben.soundstage.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nachtraben.soundstage.SoundstageInit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

final class AtlasSprites {
    private AtlasSprites() {}

    static final RenderType CUTOUT = SoundstageRenderTypes.cutoutNoCull(TextureAtlas.LOCATION_BLOCKS);

    private static final ResourceLocation WHITE =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "block/white");
    private static final ResourceLocation CONE =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "block/subwoofer_cone");

    static TextureAtlasSprite sprite(ResourceLocation id) {
        //? if >=1.21.11 {
        /*return Minecraft.getInstance().getAtlasManager()
                .getAtlasOrThrow(ResourceLocation.withDefaultNamespace("blocks")).getSprite(id);
        *///?} else {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id);
        //?}
    }

    static GeometrySink.Emitter white(GeometrySink.Emitter body) {
        return (pose, consumer) -> {
            TextureAtlasSprite white = sprite(WHITE);
            body.accept(pose, new MappedUv(consumer,
                    (white.getU0() + white.getU1()) / 2f, (white.getV0() + white.getV1()) / 2f, 0f, 0f));
        };
    }

    static GeometrySink.Emitter cone(GeometrySink.Emitter body) {
        return (pose, consumer) -> {
            TextureAtlasSprite cone = sprite(CONE);
            body.accept(pose, new MappedUv(consumer, cone.getU0(), cone.getV0(),
                    cone.getU1() - cone.getU0(), cone.getV1() - cone.getV0()));
        };
    }

    private record MappedUv(VertexConsumer delegate, float u0, float v0, float du, float dv) implements VertexConsumer {
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
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u0 + u * du, v0 + v * dv);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u1, int v1) {
            delegate.setUv1(u1, v1);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u2, int v2) {
            delegate.setUv2(u2, v2);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v,
                              int overlay, int light, float normalX, float normalY, float normalZ) {
            delegate.addVertex(x, y, z, color, u0 + u * du, v0 + v * dv, overlay, light, normalX, normalY, normalZ);
        }
    }
}
