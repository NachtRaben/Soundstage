package com.nachtraben.soundstage.core.video;

import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;

import java.nio.ByteBuffer;

public final class H264Headers {

    private static final int NAL_TYPE_SPS = 7;

    private H264Headers() {}

    public static long codedPixels(byte[] annexB) {
        long largest = 0;
        ByteBuffer data = ByteBuffer.wrap(annexB);
        ByteBuffer nal;
        while ((nal = H264Utils.nextNALUnit(data)) != null) {
            if (!nal.hasRemaining() || (nal.get() & 0x1f) != NAL_TYPE_SPS) continue;
            SeqParameterSet sps;
            try {
                sps = H264Utils.readSPS(nal);
            } catch (RuntimeException e) {
                continue;
            }
            largest = Math.max(largest, codedPixels(sps));
        }
        return largest;
    }

    private static long codedPixels(SeqParameterSet sps) {
        long widthInMbs = sps.picWidthInMbsMinus1 + 1L;
        long heightInMapUnits = sps.picHeightInMapUnitsMinus1 + 1L;
        if (widthInMbs <= 0 || heightInMapUnits <= 0) return Long.MAX_VALUE;
        try {
            return Math.multiplyExact(widthInMbs * 16, heightInMapUnits * (sps.frameMbsOnlyFlag ? 16 : 32));
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}
