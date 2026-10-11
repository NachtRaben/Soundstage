package com.nachtraben.soundstage.client.video;

import dev.armadeus.armalib.platform.client.render.RenderBackend;
import dev.armadeus.armalib.ui.StreamingTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL45;
import org.lwjgl.opengl.GL;

import java.nio.ByteBuffer;
//? if >=26.2 {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.nachtraben.soundstage.client.render.SoundstageRenderTypes;

import java.util.Optional;
import java.util.function.Supplier;
*///?}

public final class VideoTexture extends StreamingTexture {
    private static final String LABEL = "soundstage video";

    private VideoTexture(boolean mipmapped) {
        super(LABEL, mipmapped);
    }

    public static VideoTexture create() {
        return new VideoTexture(mipmapsFillable());
    }

    private static boolean mipmapsFillable() {
        if (RenderBackend.isOpenGl()) return true;
        //? if >=26.2 {
        /*return SoundstageRenderTypes.videoMipReady();
        *///?} else {
        return false;
        //?}
    }

    @Override
    public void upload(ByteBuffer pixels, int width, int height) {
        super.upload(pixels, width, height);
        if (!mipmapped()) return;
        //? if >=26.2 {
        /*if (!RenderBackend.isOpenGl()) {
            fillLevels();
            return;
        }
        *///?}
        generateMipmaps(glName());
    }

    private static void generateMipmaps(int texture) {
        if (texture == 0) return;
        if (GL.getCapabilities().OpenGL45) {
            GL45.glGenerateTextureMipmap(texture);
            return;
        }
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
        }
    }

    //? if >=26.2 {
    /*private static final Supplier<String> PASS_LABEL = () -> LABEL + " mip level";

    private void fillLevels() {
        //? if >=26.3 {
        /^com.mojang.blaze3d.pipeline.CompiledRenderPipeline pipeline =
                RenderSystem.getCompiledPipelineNullable(SoundstageRenderTypes.VIDEO_MIP_PIPELINE);
        if (pipeline == null) return;
        ^///?}
        com.mojang.blaze3d.systems.CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        com.mojang.blaze3d.textures.GpuSampler linear =
                RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
        int levels = mipLevels(width(), height());
        for (int level = 1; level < levels; level++) {
            try (com.mojang.blaze3d.systems.RenderPass pass =
                         encoder.createRenderPass(PASS_LABEL, levelView(level), Optional.empty())) {
                //? if >=26.3 {
                /^pass.setPipeline(pipeline);
                pass.setUniform("InSampler", levelView(level - 1), linear);
                ^///?} else {
                pass.setPipeline(SoundstageRenderTypes.VIDEO_MIP_PIPELINE);
                pass.bindTexture("InSampler", levelView(level - 1), linear);
                //?}
                pass.draw(3, 1, 0, 0);
            }
        }
    }
    *///?}
}
