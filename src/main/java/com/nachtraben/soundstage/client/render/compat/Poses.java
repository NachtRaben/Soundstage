package com.nachtraben.soundstage.client.render.compat;

//? if <1.21 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;

public final class Poses {
    private Poses() {
    }

    public static Vector3f transformNormal(PoseStack.Pose pose, float x, float y, float z, Vector3f dest) {
        return pose.normal().transform(x, y, z, dest).normalize();
    }
}
*///?}
