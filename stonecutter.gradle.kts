plugins {
    id("dev.kikugie.stonecutter")
}

buildscript {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

stonecutter active "1.21.1-neoforge"

stonecutter parameters {
    val loader = current.project.substringAfter('-')
    constants { match(loader, "neoforge", "fabric", "forge") }

    replacements.regex(eval(current.version, "<1.20.5")) {
        replace("\\bnet\\.minecraft\\.network\\.protocol\\.common\\.custom\\.CustomPacketPayload\\b",
            "dev.armadeus.armalib.platform.network.compat.CustomPacketPayload",
            "\\bdev\\.armadeus\\.armalib\\.platform\\.network\\.compat\\.CustomPacketPayload\\b",
            "net.minecraft.network.protocol.common.custom.CustomPacketPayload")
        replace("\\bnet\\.minecraft\\.network\\.codec\\.(StreamCodec|ByteBufCodecs|StreamDecoder|StreamEncoder|StreamMemberEncoder)\\b",
            "dev.armadeus.armalib.platform.network.compat.$1",
            "\\bdev\\.armadeus\\.armalib\\.platform\\.network\\.compat\\.(StreamCodec|ByteBufCodecs|StreamDecoder|StreamEncoder|StreamMemberEncoder)\\b",
            "net.minecraft.network.codec.$1")
        replace("\\bnet\\.minecraft\\.network\\.RegistryFriendlyByteBuf\\b",
            "dev.armadeus.armalib.platform.network.compat.RegistryFriendlyByteBuf",
            "\\bdev\\.armadeus\\.armalib\\.platform\\.network\\.compat\\.RegistryFriendlyByteBuf\\b",
            "net.minecraft.network.RegistryFriendlyByteBuf")
        replace("(?<![\\w.])ResourceLocation\\.STREAM_CODEC\\b",
            "dev.armadeus.armalib.platform.network.compat.ByteBufCodecs.RESOURCE_LOCATION",
            "\\bdev\\.armadeus\\.armalib\\.platform\\.network\\.compat\\.ByteBufCodecs\\.RESOURCE_LOCATION\\b",
            "ResourceLocation.STREAM_CODEC")
    }

    replacements.regex(eval(current.version, "<1.21")) {
        for (method in listOf("fromNamespaceAndPath", "parse", "withDefaultNamespace")) {
            replace("(?<![\\w.])ResourceLocation\\.$method\\(",
                "dev.armadeus.armalib.platform.compat.ResourceLocations.$method(",
                "\\bdev\\.armadeus\\.armalib\\.platform\\.compat\\.ResourceLocations\\.$method\\(",
                "ResourceLocation.$method(")
        }
    }

    replacements.string(eval(current.version, "<1.20.2")) {
        replace("public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)",
            "public boolean mouseScrolled(double mouseX, double mouseY, double scrollY)")
        replace("super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)", "super.mouseScrolled(mouseX, mouseY, scrollY)")
    }

    replacements.regex(eval(current.version, "<1.21")) {
        replace("\\b(?!Poses\\b)(\\w+)\\.transformNormal\\(", "com.nachtraben.soundstage.client.render.compat.Poses.transformNormal($1, ",
            "\\bcom\\.nachtraben\\.soundstage\\.client\\.render\\.compat\\.Poses\\.transformNormal\\((\\w+), ", "$1.transformNormal(")
    }

    replacements.regex(eval(current.version, "<1.21")) {
        replace("\\bcom\\.mojang\\.blaze3d\\.vertex\\.VertexConsumer\\b",
            "com.nachtraben.soundstage.client.render.compat.VertexConsumer",
            "\\bcom\\.nachtraben\\.soundstage\\.client\\.render\\.compat\\.VertexConsumer\\b",
            "com.mojang.blaze3d.vertex.VertexConsumer")
    }

    replacements.regex(eval(current.version, "<1.21")) {
        replace("\\bcom\\.mojang\\.blaze3d\\.vertex\\.MeshData\\b", "com.mojang.blaze3d.vertex.BufferBuilder.RenderedBuffer",
            "\\bcom\\.mojang\\.blaze3d\\.vertex\\.BufferBuilder\\.RenderedBuffer\\b", "com.mojang.blaze3d.vertex.MeshData")
        replace("(?<!vertex\\.)\\bMeshData\\b", "RenderedBuffer", "(?<!BufferBuilder\\.)\\bRenderedBuffer\\b", "MeshData")
    }

    replacements.regex(eval(current.version, ">=1.21.11")) {
        replace("\\bResourceLocation\\b", "Identifier", "\\bIdentifier\\b", "ResourceLocation")
    }

    replacements.regex(eval(current.version, ">=1.21.11")) {
        for ((from, to) in listOf(
            "net.minecraft.client.renderer.RenderType" to "net.minecraft.client.renderer.rendertype.RenderType",
            "net.minecraft.Util" to "net.minecraft.util.Util",
            "net.minecraft.world.entity.animal.Parrot" to "net.minecraft.world.entity.animal.parrot.Parrot",
        )) {
            val f = from.replace(".", "\\.")
            val t = to.replace(".", "\\.")
            replace("\\b$f\\b", to, "\\b$t\\b", from)
        }
    }

    replacements.regex(eval(current.version, ">=26.1")) {
        for ((from, to) in listOf(
            "net.minecraft.client.renderer.state.CameraRenderState" to "net.minecraft.client.renderer.state.level.CameraRenderState",
            "net.minecraft.client.renderer.block.model.BakedQuad" to "net.minecraft.client.resources.model.geometry.BakedQuad",
            "com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY" to "com.mojang.blaze3d.vertex.DefaultVertexFormat.ENTITY",
            "DefaultVertexFormat.NEW_ENTITY" to "DefaultVertexFormat.ENTITY",
        )) {
            val f = from.replace(".", "\\.")
            val t = to.replace(".", "\\.")
            replace("\\b$f\\b", to, "\\b$t\\b", from)
        }
    }

    replacements.regex(eval(current.version, ">=26.2")) {
        replace("\\bMinecraft\\.getInstance\\(\\)\\.setScreen\\(", "Minecraft.getInstance().gui.setScreen(",
            "\\bMinecraft\\.getInstance\\(\\)\\.gui\\.setScreen\\(", "Minecraft.getInstance().setScreen(")
        replace("\\bMinecraft\\.getInstance\\(\\)\\.screen\\b(?!\\()", "Minecraft.getInstance().gui.screen()",
            "\\bMinecraft\\.getInstance\\(\\)\\.gui\\.screen\\(\\)", "Minecraft.getInstance().screen")
        replace("\\bRenderSystem\\.getModelViewMatrix\\(\\)", "RenderSystem.getModelViewMatrixCopy()",
            "\\bRenderSystem\\.getModelViewMatrixCopy\\(\\)", "RenderSystem.getModelViewMatrix()")
        replace("\\b(type|t)\\.mode\\(\\), ", "$1.primitiveTopology(), ",
            "\\b(type|t)\\.primitiveTopology\\(\\), ", "$1.mode(), ")
        replace("\\.gameRenderer\\.getMainCamera\\(\\)", ".gameRenderer.mainCamera()",
            "\\.gameRenderer\\.mainCamera\\(\\)", ".gameRenderer.getMainCamera()")
        val colours = listOf("WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY",
            "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK")
        for (kind in listOf("WOOL", "CONCRETE", "STAINED_GLASS")) for (colour in colours) {
            val method = colour.lowercase().split('_').mapIndexed { i, w -> if (i == 0) w else w.replaceFirstChar { it.uppercase() } }.joinToString("")
            replace("\\bBlocks\\.${colour}_$kind\\b", "Blocks.$kind.$method()",
                "\\bBlocks\\.$kind\\.$method\\(\\)", "Blocks.${colour}_$kind")
        }
    }

    replacements.regex(eval(current.version, ">=26.3")) {
        for ((from, to) in listOf(
            "blaze3d.pipeline.RenderPipeline" to "renderpearl.api.pipeline.RenderPipeline",
            "blaze3d.pipeline.CompiledRenderPipeline" to "renderpearl.api.pipeline.CompiledRenderPipeline",
            "blaze3d.pipeline.DepthStencilState" to "renderpearl.api.pipeline.DepthStencilState",
            "blaze3d.pipeline.ColorTargetState" to "renderpearl.api.pipeline.ColorTargetState",
            "blaze3d.pipeline.BlendFunction" to "renderpearl.api.pipeline.BlendFunction",
            "blaze3d.platform.CompareOp" to "renderpearl.api.pipeline.CompareOp",
            "blaze3d.buffers.GpuBufferSlice" to "renderpearl.api.buffers.GpuBufferSlice",
            "blaze3d.buffers.GpuBuffer" to "renderpearl.api.buffers.GpuBuffer",
            "blaze3d.textures.FilterMode" to "renderpearl.api.textures.FilterMode",
            "blaze3d.textures.GpuTextureView" to "renderpearl.api.textures.GpuTextureView",
            "blaze3d.textures.GpuTexture" to "renderpearl.api.textures.GpuTexture",
            "blaze3d.textures.GpuSampler" to "renderpearl.api.textures.GpuSampler",
            "blaze3d.systems.RenderPass" to "renderpearl.api.commands.RenderPass",
            "blaze3d.systems.CommandEncoder" to "renderpearl.api.commands.CommandEncoder",
            "blaze3d.systems.GpuDevice" to "renderpearl.api.device.GpuDevice",
            "blaze3d.vertex.VertexFormatElement" to "renderpearl.api.vertex.VertexFormatElement",
            "blaze3d.vertex.VertexFormat" to "renderpearl.api.vertex.VertexFormat",
            "blaze3d.PrimitiveTopology" to "renderpearl.api.pipeline.PrimitiveTopology",
            "blaze3d.IndexType" to "renderpearl.api.pipeline.IndexType",
            "blaze3d.GpuFormat" to "renderpearl.api.GpuFormat",
        )) {
            val f = from.replace(".", "\\.")
            val t = to.replace(".", "\\.")
            replace("\\bcom\\.mojang\\.$f\\b", "com.mojang.$to", "\\bcom\\.mojang\\.$t\\b", "com.mojang.$from")
        }
    }

    replacements.regex(eval(current.version, ">=26.3")) {
        replace("\\bposeStack\\.mulPose\\(", "poseStack.rotate(", "\\bposeStack\\.rotate\\(", "poseStack.mulPose(")
        replace("\\bevent\\.scancode\\(\\)", "event.keycode()", "\\bevent\\.keycode\\(\\)", "event.scancode()")
        replace("\\bWeighedSoundEvents resolve\\(", "WeighedSoundEvents getOrResolve(",
            "\\bWeighedSoundEvents getOrResolve\\(", "WeighedSoundEvents resolve(")
    }
}
