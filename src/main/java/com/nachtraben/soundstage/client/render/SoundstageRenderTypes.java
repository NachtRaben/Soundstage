package com.nachtraben.soundstage.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
//? if >=1.21.11 {
/*import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderTypes;
*///?}

//? if >=1.21.11 {
/*public final class SoundstageRenderTypes {

    private SoundstageRenderTypes() {
        throw new AssertionError("state holder only");
    }
*///?} else {
public final class SoundstageRenderTypes extends RenderType {

    private SoundstageRenderTypes() {
        super(null, null, null, 0, false, false, null, null);
        throw new AssertionError("state holder only");
    }
//?}

    //? if <1.21.11 {
    private static net.minecraft.client.renderer.RenderStateShard.EmptyTextureStateShard glassTextureState(
            ResourceLocation texture) {
        return new net.minecraft.client.renderer.RenderStateShard.TextureStateShard(texture, false, false);
    }
    //?}

    private static final Function<ResourceLocation, RenderType> GLASS =
            Util.memoize(SoundstageRenderTypes::createGlass);

    public static RenderType glass(ResourceLocation texture) {
        return GLASS.apply(texture);
    }

    private static RenderType createGlass(ResourceLocation texture) {
        //? if >=1.21.11 {
        /*RenderPipeline pipeline = GLASS_PIPELINE;
        return RenderType.create("soundstage_glass", RenderSetup.builder(pipeline)
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .affectsCrumbling()
                .sortOnUpload()
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                //? if >=26.3 {
                /^.setOitPipelines(RenderPipelines.OIT_ENTITY)
                ^///?}
                .createRenderSetup());
        *///?} else {

        return create("soundstage_glass",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                true,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(glassTextureState(texture))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(true));
        //?}
    }

    //? if >=1.21.11 {
    /*private static final RenderPipeline GLASS_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("soundstage", "pipeline/glass"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            //? if >=26.2 {
            /^.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER1)
            .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(
                    com.mojang.blaze3d.platform.CompareOp.GREATER_THAN_OR_EQUAL, false))
            ^///?} else if >=26.1 {
            /^.withSampler("Sampler1")
            .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(
                    com.mojang.blaze3d.platform.CompareOp.LESS_THAN_OR_EQUAL, false))
            ^///?} else {
            .withSampler("Sampler1")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withDepthWrite(false)
            //?}
            .build();

    private static final RenderPipeline ADDITIVE_EYES_PIPELINE = additiveEyesPipeline("pipeline/additive_eyes");

    private static RenderPipeline additiveEyesPipeline(String path) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                .withLocation(ResourceLocation.fromNamespaceAndPath("soundstage", path))
                .withVertexShader("core/entity")
                .withFragmentShader("core/entity")
                .withShaderDefine("EMISSIVE")
                .withShaderDefine("NO_OVERLAY")
                .withShaderDefine("NO_CARDINAL_LIGHTING")
                //? if >=26.2 {
                /^.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER0)
                .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(BlendFunction.ADDITIVE))
                .withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(
                        com.mojang.blaze3d.platform.CompareOp.GREATER_THAN_OR_EQUAL, false))
                .withVertexBinding(0, DefaultVertexFormat.NEW_ENTITY)
                .withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.QUADS)
                ^///?} else if >=26.1 {
                /^.withSampler("Sampler0")
                .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(BlendFunction.ADDITIVE))
                .withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(
                        com.mojang.blaze3d.platform.CompareOp.LESS_THAN_OR_EQUAL, false))
                .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
                ^///?} else {
                .withSampler("Sampler0")
                .withBlend(BlendFunction.ADDITIVE)
                .withDepthWrite(false)
                .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
                //?}
                .build();
    }

    //? if >=26.3 {
    /^private static final net.minecraft.client.renderer.oit.OitPipelineSet ADDITIVE_EYES_OIT =
            net.minecraft.client.renderer.oit.OitPipelineSet.builder("soundstage_additive_eyes",
                    RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                            .withVertexShader("core/entity")
                            .withFragmentShader("core/entity")
                            .withShaderDefine("EMISSIVE")
                            .withShaderDefine("NO_OVERLAY")
                            .withShaderDefine("NO_CARDINAL_LIGHTING")
                            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER0)
                            .withVertexBinding(0, DefaultVertexFormat.NEW_ENTITY)
                            .withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.QUADS)
                            .withShaderDefine("OIT_ADDITIVE"))
                    .build();
    ^///?}

    private static final java.util.function.Function<ResourceLocation, RenderType> ADDITIVE_EYES =
            net.minecraft.Util.memoize(texture -> RenderType.create("soundstage_eyes",
                    RenderSetup.builder(ADDITIVE_EYES_PIPELINE).withTexture("Sampler0", texture).sortOnUpload()
                            //? if >=26.3 {
                            /^.setOitPipelines(ADDITIVE_EYES_OIT)
                            ^///?}
                            .createRenderSetup()));

    static {
        IrisCompat.drawLike(RenderPipelines.ENTITY_TRANSLUCENT, GLASS_PIPELINE);
        IrisCompat.drawLike(RenderPipelines.ENTITY_TRANSLUCENT, ADDITIVE_EYES_PIPELINE);
    }

    public static int submitOrder(RenderType type) {
        RenderPipeline pipeline = type.pipeline();
        boolean pack = IrisCompat.isShaderPackInUse();
        if (pipeline == ADDITIVE_EYES_PIPELINE) return pack ? 2 : 1;
        if (pipeline == GLASS_PIPELINE) return pack ? 1 : 2;
        return 0;
    }

    //? if >=26.3 {
    /^public static final RenderPipeline OVERLAY_PIPELINE = RenderPipeline.builder()
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withLocation(ResourceLocation.fromNamespaceAndPath("soundstage", "pipeline/screen_overlay"))
            .withVertexShader("core/position_tex")
            .withFragmentShader("core/position_tex")
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER0)
            .withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(
                    com.mojang.blaze3d.platform.CompareOp.GREATER_THAN_OR_EQUAL, false, 1f, 4f))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.QUADS)
            .withColorTargetState(com.mojang.blaze3d.pipeline.ColorTargetState.DEFAULT)
            .build();
    ^///?} else if >=26.2 {
    /^public static final RenderPipeline OVERLAY_PIPELINE = RenderPipeline.builder()
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.MATRICES_PROJECTION)
            .withLocation(ResourceLocation.fromNamespaceAndPath("soundstage", "pipeline/screen_overlay"))
            .withVertexShader("core/position_tex")
            .withFragmentShader("core/position_tex")
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER0)
            .withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(
                    com.mojang.blaze3d.platform.CompareOp.GREATER_THAN_OR_EQUAL, false, 1f, 4f))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.QUADS)
            .build();
    ^///?} else if >=26.1 {
    /^public static final RenderPipeline OVERLAY_PIPELINE = RenderPipeline.builder()
            .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withLocation(ResourceLocation.fromNamespaceAndPath("soundstage", "pipeline/screen_overlay"))
            .withVertexShader("core/position_tex")
            .withFragmentShader("core/position_tex")
            .withSampler("Sampler0")
            .withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(
                    com.mojang.blaze3d.platform.CompareOp.LESS_THAN_OR_EQUAL, false, -1f, -4f))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
            .build();
    ^///?} else {
    public static final RenderPipeline OVERLAY_PIPELINE = RenderPipeline.builder()
            .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withLocation(ResourceLocation.fromNamespaceAndPath("soundstage", "pipeline/screen_overlay"))
            .withVertexShader("core/position_tex")
            .withFragmentShader("core/position_tex")
            .withSampler("Sampler0")
            .withDepthWrite(false)
            .withDepthBias(-1f, -4f)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
            .build();
    //?}

    private static final java.util.function.Function<ResourceLocation, RenderType> OVERLAY =
            net.minecraft.Util.memoize(texture -> RenderType.create("soundstage_screen_overlay",
                    RenderSetup.builder(OVERLAY_PIPELINE).withTexture("Sampler0", texture).createRenderSetup()));

    public static RenderType screenOverlay(ResourceLocation texture) {
        return OVERLAY.apply(texture);
    }
    *///?}

    //? if >=26.3 {
    /*public static final RenderPipeline VIDEO_MIP_PIPELINE = videoMipPipeline("core/video_mip_spirv");
    *///?} else if >=26.2 {
    /*public static final RenderPipeline VIDEO_MIP_PIPELINE = videoMipPipeline("core/video_mip");
    *///?}

    //? if >=26.2 {
    /*private static RenderPipeline videoMipPipeline(String shader) {
        ResourceLocation source = ResourceLocation.fromNamespaceAndPath("soundstage", shader);
        return RenderPipeline.builder()
                .withLocation(ResourceLocation.fromNamespaceAndPath("soundstage", "pipeline/video_mip"))
                .withVertexShader(source)
                .withFragmentShader(source)
                .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.IN_SAMPLER)
                .withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
                .withColorTargetState(com.mojang.blaze3d.pipeline.ColorTargetState.DEFAULT)
                .build();
    }

    private static volatile boolean videoMipCompiled;

    public static void videoMipCompiled() {
        videoMipCompiled = true;
    }

    public static boolean videoMipReady() {
        return videoMipCompiled;
    }
    *///?}

    private static final Function<ResourceLocation, RenderType> VIDEO =
            Util.memoize(SoundstageRenderTypes::createVideo);

    public static RenderType video(ResourceLocation texture) {
        return VIDEO.apply(texture);
    }

    private static RenderType createVideo(ResourceLocation texture) {
        //? if >=1.21.11 {
        /*return RenderType.create("soundstage_video", RenderSetup.builder(RenderPipelines.BEACON_BEAM_OPAQUE)
                .withTexture("Sampler0", texture)
                .sortOnUpload()
                .createRenderSetup());
        *///?} else {

        return create("soundstage_video",
                DefaultVertexFormat.BLOCK,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_BEACON_BEAM_SHADER)
                        .setTextureState(new net.minecraft.client.renderer.RenderStateShard.TextureStateShard(
                                texture, true, true))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .createCompositeState(false));
        //?}
    }

    //? if >=1.21.11 {
    /*public static final RenderPipeline SPIDER_PIPELINE =
            spiderPipeline("pipeline/spider", spiderVertexShader(), true);

    private static ResourceLocation spiderVertexShader() {
        //? if >=26.3 {
        /^return ResourceLocation.fromNamespaceAndPath("soundstage", "core/spider_spirv");
        ^///?} else {
        return ResourceLocation.fromNamespaceAndPath("soundstage", "core/spider");
        //?}
    }

    private static RenderPipeline spiderPipeline(String location, ResourceLocation vertexShader, boolean depth) {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(ResourceLocation.fromNamespaceAndPath("soundstage", location))
                .withVertexShader(vertexShader)
                .withFragmentShader("core/entity")
                .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                .withShaderDefine("PER_FACE_LIGHTING")
                .withShaderDefine("NO_OVERLAY")
                .withCull(false)
                //? if >=26.2 {
                /^.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER0_SAMPLER2)
                .withVertexBinding(0, DefaultVertexFormat.NEW_ENTITY)
                .withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.QUADS)
                ^///?} else if >=26.1 {
                /^.withSampler("Sampler0")
                .withSampler("Sampler2")
                .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
                ^///?} else {
                .withSampler("Sampler0")
                .withSampler("Sampler2")
                .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
                //?}
                //? if >=26.3 {
                /^.withColorTargetState(com.mojang.blaze3d.pipeline.ColorTargetState.DEFAULT)
                ^///?}
                ;
        //? if >=26.1 {
        /^if (depth) builder.withDepthStencilState(com.mojang.blaze3d.pipeline.DepthStencilState.DEFAULT);
        ^///?} else {
        if (!depth) {
            builder.withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false);
        }
        //?}
        return builder.build();
    }

    private static volatile boolean spiderCompiled;

    public static void spiderCompiled() {
        spiderCompiled = true;
    }

    public static boolean spiderShaderReady() {
        return spiderCompiled;
    }
    *///?} else {
    private static net.minecraft.client.renderer.ShaderInstance spiderShader;

    public static void setSpiderShader(net.minecraft.client.renderer.ShaderInstance shader) {
        spiderShader = shader;
    }

    public static net.minecraft.client.renderer.ShaderInstance spiderShader() {
        return spiderShader;
    }

    public static boolean spiderShaderReady() {
        return spiderShader != null;
    }

    public static ResourceLocation spiderShaderId() {
        //? if <1.21 {
        /*return ResourceLocation.fromNamespaceAndPath("soundstage", "soundstage_spider_1201");
        *///?} else {
        return ResourceLocation.fromNamespaceAndPath("soundstage", "soundstage_spider");
        //?}
    }
    //?}

    private static final Function<ResourceLocation, RenderType> SPIDER =
            Util.memoize(SoundstageRenderTypes::createSpider);

    public static RenderType spider(ResourceLocation texture) {
        return SPIDER.apply(texture);
    }

    public static RenderType undisplaced(RenderType type) {
        RenderType plain = UNDISPLACED.get(type);
        return plain != null ? plain : type;
    }

    private static final Map<RenderType, RenderType> UNDISPLACED =
            new ConcurrentHashMap<>();

    private static RenderType createSpider(ResourceLocation texture) {
        //? if >=1.21.11 {
        /*RenderType type = RenderType.create("soundstage_spider", RenderSetup.builder(SPIDER_PIPELINE)
                .withTexture("Sampler0", texture)
                .useLightmap()
                .affectsCrumbling()
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                .createRenderSetup());
        *///?} else {

        RenderType type = create("soundstage_spider",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                true,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(new net.minecraft.client.renderer.RenderStateShard.ShaderStateShard(
                                SoundstageRenderTypes::spiderShader))
                        .setTextureState(glassTextureState(texture))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .createCompositeState(true));
        //?}
        UNDISPLACED.put(type, cutoutNoCull(texture));
        return type;
    }

    public static RenderType eyes(ResourceLocation texture) {
        //? if >=1.21.11 {
        /*return ADDITIVE_EYES.apply(texture);
        *///?} else {
        return RenderType.eyes(texture);
        //?}
    }

    public static RenderType cutoutBlock() {
        //? if >=1.21.11 {
        /*return RenderTypes.cutoutMovingBlock();
        *///?} else {
        return RenderType.cutout();
        //?}
    }

    public static RenderType cutoutNoCull(ResourceLocation texture) {
        //? if >=26.1 {
        /*return RenderTypes.entityCutout(texture);
        *///?} else if >=1.21.11 {
        /*return RenderTypes.entityCutoutNoCull(texture);
        *///?} else {
        return RenderType.entityCutoutNoCull(texture);
        //?}
    }

    public static RenderType entitySolid(ResourceLocation texture) {
        //? if >=1.21.11 {
        /*return RenderTypes.entitySolid(texture);
        *///?} else {
        return RenderType.entitySolid(texture);
        //?}
    }

    //? if <26.3 {
    public static RenderType textBackground() {
        //? if >=1.21.11 {
        /*return RenderTypes.textBackground();
        *///?} else {
        return RenderType.textBackground();
        //?}
    }
    //?}
}
