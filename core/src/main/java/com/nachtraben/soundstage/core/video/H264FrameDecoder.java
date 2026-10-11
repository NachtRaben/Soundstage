/*
 * Parts of this file are derived from JCodec's H264Decoder (https://github.com/jcodec/jcodec).
 *
 * Copyright 2008-2017 JCodecProject
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer. Redistributions in binary form
 * must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.nachtraben.soundstage.core.video;

import org.jcodec.codecs.h264.H264Const;
import org.jcodec.codecs.h264.H264Decoder;
import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.codecs.h264.POCManager;
import org.jcodec.codecs.h264.decode.DeblockerInput;
import org.jcodec.codecs.h264.decode.FrameReader;
import org.jcodec.codecs.h264.decode.SliceDecoder;
import org.jcodec.codecs.h264.decode.SliceReader;
import org.jcodec.codecs.h264.io.model.Frame;
import org.jcodec.codecs.h264.io.model.NALUnit;
import org.jcodec.codecs.h264.io.model.NALUnitType;
import org.jcodec.codecs.h264.io.model.PictureParameterSet;
import org.jcodec.codecs.h264.io.model.RefPicMarking;
import org.jcodec.codecs.h264.io.model.RefPicMarkingIDR;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.codecs.h264.io.model.SliceHeader;
import org.jcodec.common.IntObjectMap;
import org.jcodec.common.model.ColorSpace;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class H264FrameDecoder {

    private static final int NAL_TYPE_SPS = 7;
    private static final int MAX_SPARE = 2;

    private final DecodeWorkers workers;
    private final FrameReader reader = new FrameReader();
    private final POCManager poc = new POCManager();
    private Frame[] shortRefs;
    private IntObjectMap<Frame> longRefs;
    private final List<byte[][]> spare = new ArrayList<>();
    private final Map<byte[][], DeblockerInput> inputs = new IdentityHashMap<>();
    private byte[][] lent;
    private int codedWidth;
    private int codedHeight;

    private ColorMatrix matrix = ColorMatrix.BT601;
    private boolean fullRange = false;

    public H264FrameDecoder() {
        this(DecodeWorkers.direct());
    }

    public H264FrameDecoder(DecodeWorkers workers) {
        this.workers = workers;
    }

    public Picture decode(byte[] annexB) {
        try {
            scanForSps(annexB);
            Frame frame = decodeFrame(H264Utils.splitFrame(ByteBuffer.wrap(annexB)));
            if (frame == null) return null;
            return new Picture(frame.getCroppedWidth(), frame.getCroppedHeight(), matrix, fullRange, frame);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Frame decodeFrame(List<ByteBuffer> nalUnits) {
        if (lent != null) {
            release(lent);
            lent = null;
        }
        List<SliceReader> slices = reader.readFrame(nalUnits);
        if (slices == null || slices.isEmpty()) return null;
        SliceReader first = slices.get(0);
        NALUnit firstNu = first.getNALUnit();
        SliceHeader header = first.getSliceHeader();
        SeqParameterSet sps = header.sps;
        validate(sps, header.pps);

        int mbWidth = sps.picWidthInMbsMinus1 + 1;
        int mbHeight = SeqParameterSet.getPicHeightInMbs(sps);
        resize(mbWidth << 4, mbHeight << 4);
        if (shortRefs == null) {
            shortRefs = new Frame[1 << (sps.log2MaxFrameNumMinus4 + 4)];
            longRefs = new IntObjectMap<>();
        }
        byte[][] planes = take();
        try {
            DeblockerInput di = inputFor(planes, sps, mbWidth, mbHeight);
            Frame result = H264Decoder.createFrame(sps, planes, header.frameNum, header.sliceType, di.mvs,
                    di.refsUsed, poc.calcPOC(header, firstNu));
            Frame[] shorts = shortRefs;
            IntObjectMap<Frame> longs = longRefs;
            workers.run(slices.size(),
                    i -> new SliceDecoder(sps, shorts, longs, di, result).decodeFromReader(slices.get(i)));
            new H264Deblocker(di, mbWidth, mbHeight).deblock(result, workers);

            if (firstNu.nal_ref_idc != 0) {
                if (firstNu.type == NALUnitType.IDR_SLICE) {
                    markIdr(header.refPicMarkingIDR, result, header);
                } else {
                    mark(header.refPicMarkingNonIDR, result, header, sps);
                }
            } else {
                lent = result.getData();
            }
            return result;
        } catch (RuntimeException | Error e) {
            if (lent != planes && !referenced(planes)) release(planes);
            throw e;
        }
    }

    private boolean referenced(byte[][] planes) {
        for (Frame frame : shortRefs) {
            if (frame != null && frame.getData() == planes) return true;
        }
        for (int key : longRefs.keys()) {
            Frame frame = longRefs.get(key);
            if (frame != null && frame.getData() == planes) return true;
        }
        return false;
    }

    private void resize(int width, int height) {
        if (width == codedWidth && height == codedHeight) return;
        codedWidth = width;
        codedHeight = height;
        for (byte[][] planes : spare) inputs.remove(planes);
        spare.clear();
    }

    private DeblockerInput inputFor(byte[][] planes, SeqParameterSet sps, int mbWidth, int mbHeight) {
        DeblockerInput di = inputs.get(planes);
        if (di == null || di.mbTypes.length != mbWidth * mbHeight || di.nCoeff.length != mbHeight << 2
                || di.nCoeff[0].length != mbWidth << 2) {
            di = new DeblockerInput(sps);
            inputs.put(planes, di);
            return di;
        }
        for (int[] row : di.nCoeff) Arrays.fill(row, 0);
        di.mvs.clear();
        Arrays.fill(di.mbTypes, null);
        Arrays.fill(di.tr8x8Used, false);
        for (int[] qps : di.mbQps) Arrays.fill(qps, 0);
        Arrays.fill(di.shs, null);
        Arrays.fill(di.refsUsed, null);
        return di;
    }

    private byte[][] take() {
        if (!spare.isEmpty()) return spare.remove(spare.size() - 1);
        return org.jcodec.common.model.Picture.create(codedWidth, codedHeight, ColorSpace.YUV420J).getData();
    }

    private void release(byte[][] planes) {
        if (planes[0].length == codedWidth * codedHeight && spare.size() < MAX_SPARE) {
            spare.add(planes);
        } else {
            inputs.remove(planes);
        }
    }

    private void release(Frame frame) {
        if (frame != null) release(frame.getData());
    }

    private void markIdr(RefPicMarkingIDR marking, Frame picture, SliceHeader header) {
        clearAll();
        if (marking.isUseForlongTerm()) {
            longRefs.put(0, picture);
            picture.setShortTerm(false);
        } else {
            shortRefs[header.frameNum] = picture;
        }
    }

    private void clearAll() {
        for (int i = 0; i < shortRefs.length; i++) {
            release(shortRefs[i]);
            shortRefs[i] = null;
        }
        for (int key : longRefs.keys()) release(longRefs.get(key));
        longRefs.clear();
    }

    private void mark(RefPicMarking marking, Frame picture, SliceHeader header, SeqParameterSet sps) {
        int maxFrames = 1 << (sps.log2MaxFrameNumMinus4 + 4);
        Frame saved = picture;
        if (marking != null) {
            for (RefPicMarking.Instruction instr : marking.getInstructions()) {
                switch (instr.getType()) {
                    case REMOVE_SHORT -> {
                        int index = Math.floorMod(header.frameNum - instr.getArg1(), maxFrames);
                        release(shortRefs[index]);
                        shortRefs[index] = null;
                    }
                    case REMOVE_LONG -> {
                        release(longRefs.get(instr.getArg1()));
                        longRefs.remove(instr.getArg1());
                    }
                    case CONVERT_INTO_LONG -> {
                        int index = Math.floorMod(header.frameNum - instr.getArg1(), maxFrames);
                        release(longRefs.get(instr.getArg2()));
                        longRefs.put(instr.getArg2(), shortRefs[index]);
                        shortRefs[index] = null;
                        longRefs.get(instr.getArg2()).setShortTerm(false);
                    }
                    case TRUNK_LONG -> {
                        for (int key : longRefs.keys()) {
                            if (key > instr.getArg1() - 1) {
                                release(longRefs.get(key));
                                longRefs.remove(key);
                            }
                        }
                    }
                    case CLEAR -> clearAll();
                    case MARK_LONG -> {
                        release(longRefs.get(instr.getArg1()));
                        saved.setShortTerm(false);
                        longRefs.put(instr.getArg1(), saved);
                        saved = null;
                    }
                }
            }
        }
        if (saved != null) {
            release(shortRefs[header.frameNum]);
            shortRefs[header.frameNum] = saved;
        }
        if (marking == null) {
            int maxShort = Math.max(1, sps.numRefFrames - longRefs.size());
            int min = Integer.MAX_VALUE, count = 0, minFrameNo = 0;
            for (Frame ref : shortRefs) {
                if (ref == null) continue;
                int frameNo = ref.getFrameNo();
                int unwrapped = frameNo > header.frameNum ? frameNo - maxFrames : frameNo;
                if (unwrapped < min) {
                    min = unwrapped;
                    minFrameNo = frameNo;
                }
                count++;
            }
            if (count > maxShort) {
                release(shortRefs[minFrameNo]);
                shortRefs[minFrameNo] = null;
            }
        }
    }

    private static void validate(SeqParameterSet sps, PictureParameterSet pps) {
        if (sps.mbAdaptiveFrameFieldFlag) throw new IllegalStateException("MBAFF");
        if (sps.bitDepthLumaMinus8 != 0 || sps.bitDepthChromaMinus8 != 0) {
            throw new IllegalStateException("high bit depth");
        }
        if (sps.chromaFormatIdc != ColorSpace.YUV420J) throw new IllegalStateException(sps.chromaFormatIdc + " colour");
        if (!sps.frameMbsOnlyFlag || sps.fieldPicFlag) throw new IllegalStateException("interlace");
        if (pps.constrainedIntraPredFlag) throw new IllegalStateException("constrained intra prediction");
        if (sps.qpprimeYZeroTransformBypassFlag) throw new IllegalStateException("transform bypass");
        if (sps.profileIdc != H264Const.PROFILE_BASELINE && sps.profileIdc != H264Const.PROFILE_MAIN
                && sps.profileIdc != H264Const.PROFILE_HIGH) {
            throw new IllegalStateException(sps.profileIdc + " profile");
        }
    }

    private void scanForSps(byte[] annexB) {
        ByteBuffer data = ByteBuffer.wrap(annexB);
        ByteBuffer nal;
        while ((nal = H264Utils.nextNALUnit(data)) != null) {
            int type = nal.get() & 0x1f;
            if (type != NAL_TYPE_SPS) continue;
            byte[] copy = new byte[nal.remaining()];
            nal.duplicate().get(copy);
            SeqParameterSet sps = H264Utils.readSPS(ByteBuffer.wrap(copy));
            int height = (sps.picHeightInMapUnitsMinus1 + 1) * 16 * (sps.frameMbsOnlyFlag ? 1 : 2);
            if (sps.vuiParams != null) {
                fullRange = sps.vuiParams.videoFullRangeFlag;
                matrix = ColorMatrix.of(sps.vuiParams.matrixCoefficients,
                        sps.vuiParams.colourDescriptionPresentFlag, height);
            }
        }
    }

    public record Picture(int width, int height, ColorMatrix matrix, boolean fullRange,
                           org.jcodec.common.model.Picture planes) {

        public void toRgba(ByteBuffer out) {
            toRgba(out, DecodeWorkers.direct());
        }

        public void toRgba(ByteBuffer out, DecodeWorkers workers) {
            Conversion conversion = new Conversion(this, out);
            int bands = Math.min(height, workers.parallelism() * 2);
            if (bands <= 1) {
                conversion.rows(0, height);
                return;
            }
            workers.run(bands, band -> conversion.rows(height * band / bands, height * (band + 1) / bands));
        }
    }

    private static final class Conversion {
        private static final int CLAMP_OFFSET = 1024;
        private static final int[] CLAMP = new int[2048 + 1024];

        static {
            for (int i = 0; i < CLAMP.length; i++) CLAMP[i] = Math.max(0, Math.min(255, i - CLAMP_OFFSET));
        }

        private final Picture picture;
        private final IntBuffer out;
        private final ColorMatrix.Coefficients c;

        Conversion(Picture picture, ByteBuffer out) {
            this.picture = picture;
            this.out = out.duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
            this.c = picture.matrix().coefficients(picture.fullRange());
        }

        void rows(int from, int to) {
            org.jcodec.common.model.Picture planes = picture.planes();
            int width = picture.width();
            byte[] luma = planes.getPlaneData(0);
            byte[] cb = planes.getPlaneData(1);
            byte[] cr = planes.getPlaneData(2);
            int strideY = planes.getPlaneWidth(0);
            int strideC = planes.getPlaneWidth(1);
            int originX = planes.getStartX();
            int originY = planes.getStartY();
            int yScale = c.yScale256();
            int yBase = yScale * (128 - c.yOffset()) + 128 + (CLAMP_OFFSET << 8);
            int rCr = c.rCr256(), gCb = c.gCb256(), gCr = c.gCr256(), bCb = c.bCb256();
            int[] row = new int[width];
            for (int y = from; y < to; y++) {
                int py = originY + y;
                int yRow = py * strideY + originX;
                int cRow = (py >> 1) * strideC + (originX >> 1);
                for (int x = 0; x < width; x++) {
                    int chroma = cRow + (x >> 1);
                    int cbv = cb[chroma], crv = cr[chroma];
                    int r = rCr * crv;
                    int g = -gCb * cbv - gCr * crv;
                    int b = bCb * cbv;
                    int ys = yScale * luma[yRow + x] + yBase;
                    row[x] = CLAMP[(ys + r) >> 8] | CLAMP[(ys + g) >> 8] << 8 | CLAMP[(ys + b) >> 8] << 16 | 0xFF000000;
                    if ((x & 1) == 0 && x + 1 < width) {
                        x++;
                        ys = yScale * luma[yRow + x] + yBase;
                        row[x] = CLAMP[(ys + r) >> 8] | CLAMP[(ys + g) >> 8] << 8 | CLAMP[(ys + b) >> 8] << 16
                                | 0xFF000000;
                    }
                }
                out.put(y * width, row, 0, width);
            }
        }
    }
}
