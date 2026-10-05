package com.nachtraben.soundstage.core.video;

import org.jcodec.codecs.h264.H264Decoder;
import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.codecs.h264.io.model.Frame;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.common.model.ColorSpace;

import java.nio.ByteBuffer;

public final class H264FrameDecoder {

    private static final int NAL_TYPE_SPS = 7;

    private final H264Decoder decoder = new H264Decoder();

    private org.jcodec.common.model.Picture buffer =
            org.jcodec.common.model.Picture.create(16, 16, ColorSpace.YUV420J);

    private ColorMatrix matrix = ColorMatrix.BT601;
    private boolean fullRange = false;

    public Picture decode(byte[] annexB) {
        try {
            scanForSps(annexB);
            Frame frame = decoder.decodeFrame(ByteBuffer.wrap(annexB), buffer.getData());
            if (frame == null) return null;
            return new Picture(frame.getCroppedWidth(), frame.getCroppedHeight(), matrix, fullRange,
                    frame);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void scanForSps(byte[] annexB) {
        ByteBuffer data = ByteBuffer.wrap(annexB);
        ByteBuffer nal;
        while ((nal = H264Utils.nextNALUnit(data)) != null) {
            int type = nal.get() & 0x1f;
            if (type != NAL_TYPE_SPS) continue;
            SeqParameterSet sps = H264Utils.readSPS(nal);
            int width = (sps.picWidthInMbsMinus1 + 1) * 16;
            int height = (sps.picHeightInMapUnitsMinus1 + 1) * 16 * (sps.frameMbsOnlyFlag ? 1 : 2);
            ensureBuffer(width, height);
            if (sps.vuiParams != null) {
                fullRange = sps.vuiParams.videoFullRangeFlag;
                matrix = ColorMatrix.of(sps.vuiParams.matrixCoefficients,
                        sps.vuiParams.colourDescriptionPresentFlag, height);
            }
        }
    }

    private void ensureBuffer(int width, int height) {
        if (width > buffer.getWidth() || height > buffer.getHeight()) {
            buffer = org.jcodec.common.model.Picture.create(width, height, ColorSpace.YUV420J);
        }
    }

    public record Picture(int width, int height, ColorMatrix matrix, boolean fullRange,
                           org.jcodec.common.model.Picture planes) {

        public void toRgba(ByteBuffer out) {
            ColorMatrix.Coefficients c = matrix.coefficients(fullRange);
            byte[] luma = planes.getPlaneData(0);
            byte[] cb = planes.getPlaneData(1);
            byte[] cr = planes.getPlaneData(2);
            int strideY = planes.getPlaneWidth(0);
            int strideC = planes.getPlaneWidth(1);
            int originX = planes.getStartX();
            int originY = planes.getStartY();

            ByteBuffer dst = out.duplicate();

            byte[] row = new byte[width * 4];
            for (int y = 0; y < height; y++) {
                int py = originY + y;
                int yRowBase = py * strideY + originX;
                int cRowBase = (py >> 1) * strideC + (originX >> 1);
                int rowPos = 0;
                for (int x = 0; x < width; x++) {
                    int yv = luma[yRowBase + x] + 128;
                    int cbv = cb[cRowBase + (x >> 1)] + 128;
                    int crv = cr[cRowBase + (x >> 1)] + 128;
                    int ySample = c.yScale256() * (yv - c.yOffset());
                    row[rowPos++] = clampByte((ySample + c.rCr256() * (crv - 128) + 128) >> 8);
                    row[rowPos++] = clampByte(
                            (ySample - c.gCb256() * (cbv - 128) - c.gCr256() * (crv - 128) + 128) >> 8);
                    row[rowPos++] = clampByte((ySample + c.bCb256() * (cbv - 128) + 128) >> 8);
                    row[rowPos++] = (byte) 255;
                }
                dst.put(row, 0, row.length);
            }
        }

        private static byte clampByte(int v) {
            return (byte) (v < 0 ? 0 : Math.min(v, 255));
        }
    }
}
