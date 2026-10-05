package com.nachtraben.soundstage.core.video;

public record VideoAccessUnit(long pts90k, long dts90k, boolean keyframe, byte[] annexB) {
}
