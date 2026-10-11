/*
 * Parts of this file are derived from JCodec's DeblockingFilter (https://github.com/jcodec/jcodec).
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

import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.codecs.h264.decode.DeblockerInput;
import org.jcodec.codecs.h264.decode.deblock.DeblockingFilter;
import org.jcodec.codecs.h264.io.model.SliceHeader;
import org.jcodec.common.model.Picture;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.LockSupport;

final class H264Deblocker {

    private final DeblockerInput di;
    private final int mbWidth;
    private final int mbHeight;

    H264Deblocker(DeblockerInput di, int mbWidth, int mbHeight) {
        this.di = di;
        this.mbWidth = mbWidth;
        this.mbHeight = mbHeight;
    }

    void deblock(Picture picture, DecodeWorkers workers) {
        int tasks = Math.min(workers.parallelism(), mbHeight);
        if (tasks <= 1) {
            int[][] bsV = new int[4][4], bsH = new int[4][4];
            for (int mbAddr = 0, n = mbWidth * mbHeight; mbAddr < n; mbAddr++) {
                macroblock(picture, mbAddr, bsV, bsH);
            }
            return;
        }
        AtomicInteger nextRow = new AtomicInteger();
        AtomicIntegerArray done = new AtomicIntegerArray(mbHeight);
        workers.run(tasks, task -> {
            int[][] bsV = new int[4][4], bsH = new int[4][4];
            int row;
            while ((row = nextRow.getAndIncrement()) < mbHeight) {
                for (int x = 0; x < mbWidth; x++) {
                    if (row > 0) awaitAbove(done, row - 1, Math.min(x + 2, mbWidth));
                    macroblock(picture, row * mbWidth + x, bsV, bsH);
                    done.set(row, x + 1);
                }
            }
        });
    }

    private static void awaitAbove(AtomicIntegerArray done, int row, int count) {
        for (int spins = 0; done.get(row) < count; spins++) {
            if (spins < 100) {
                Thread.onSpinWait();
            } else if (spins < 200) {
                Thread.yield();
            } else {
                LockSupport.parkNanos(20_000);
            }
        }
    }

    private void macroblock(Picture picture, int mbAddr, int[][] bsV, int[][] bsH) {
        calcBsH(mbAddr, bsH);
        calcBsV(mbAddr, bsV);
        for (int comp = 0; comp < picture.getColor().nComp; comp++) {
            fillVerticalEdge(picture, comp, mbAddr, bsV);
            fillHorizontalEdge(picture, comp, mbAddr, bsH);
        }
    }

    private int boundaryStrength(boolean atMbBoundary, boolean leftIntra, boolean rightIntra, int leftCoeff,
                                 int rightCoeff, int mvA0, int mvB0, int mvA1, int mvB1, int mbAddrA, int mbAddrB) {
        if (atMbBoundary && (leftIntra || rightIntra)) return 4;
        if (leftIntra || rightIntra) return 3;
        if (leftCoeff > 0 || rightCoeff > 0) return 2;

        int nA = (H264Utils.Mv.mvRef(mvA0) == -1 ? 0 : 1) + (H264Utils.Mv.mvRef(mvA1) == -1 ? 0 : 1);
        int nB = (H264Utils.Mv.mvRef(mvB0) == -1 ? 0 : 1) + (H264Utils.Mv.mvRef(mvB1) == -1 ? 0 : 1);
        if (nA != nB) return 1;

        Picture ra0 = H264Utils.Mv.mvRef(mvA0) < 0 ? null : di.refsUsed[mbAddrA][0][H264Utils.Mv.mvRef(mvA0)];
        Picture ra1 = H264Utils.Mv.mvRef(mvA1) < 0 ? null : di.refsUsed[mbAddrA][1][H264Utils.Mv.mvRef(mvA1)];
        Picture rb0 = H264Utils.Mv.mvRef(mvB0) < 0 ? null : di.refsUsed[mbAddrB][0][H264Utils.Mv.mvRef(mvB0)];
        Picture rb1 = H264Utils.Mv.mvRef(mvB1) < 0 ? null : di.refsUsed[mbAddrB][1][H264Utils.Mv.mvRef(mvB1)];

        if (ra0 != rb0 && ra0 != rb1 || ra1 != rb0 && ra1 != rb1 || rb0 != ra0 && rb0 != ra1
                || rb1 != ra0 && rb1 != ra1) {
            return 1;
        }
        if (ra0 == ra1 && ra1 == rb0 && rb0 == rb1) {
            return ra0 != null && (mvThresh(mvA0, mvB0) || mvThresh(mvA1, mvB0) || mvThresh(mvA0, mvB1)
                    || mvThresh(mvA1, mvB1)) ? 1 : 0;
        } else if (ra0 == rb0 && ra1 == rb1) {
            return ra0 != null && mvThresh(mvA0, mvB0) || ra1 != null && mvThresh(mvA1, mvB1) ? 1 : 0;
        } else if (ra0 == rb1 && ra1 == rb0) {
            return ra0 != null && mvThresh(mvA0, mvB1) || ra1 != null && mvThresh(mvA1, mvB0) ? 1 : 0;
        }
        return 0;
    }

    private static boolean mvThresh(int v0, int v1) {
        return Math.abs(H264Utils.Mv.mvX(v0) - H264Utils.Mv.mvX(v1)) >= 4
                || Math.abs(H264Utils.Mv.mvY(v0) - H264Utils.Mv.mvY(v1)) >= 4;
    }

    private static int index(int offset, int qp) {
        return Math.max(0, Math.min(51, qp + offset));
    }

    private void calcBsH(int mbAddr, int[][] bs) {
        SliceHeader sh = di.shs[mbAddr];
        int mbX = mbAddr % mbWidth;
        int mbY = mbAddr / mbWidth;
        boolean topAvailable = mbY > 0 && (sh.disableDeblockingFilterIdc != 2 || di.shs[mbAddr - mbWidth] == sh);
        boolean thisIntra = di.mbTypes[mbAddr] != null && di.mbTypes[mbAddr].isIntra();
        if (topAvailable) {
            boolean topIntra = di.mbTypes[mbAddr - mbWidth] != null && di.mbTypes[mbAddr - mbWidth].isIntra();
            for (int blkX = 0; blkX < 4; blkX++) {
                int thisBlkX = (mbX << 2) + blkX;
                int thisBlkY = mbY << 2;
                bs[0][blkX] = boundaryStrength(true, topIntra, thisIntra, di.nCoeff[thisBlkY][thisBlkX],
                        di.nCoeff[thisBlkY - 1][thisBlkX], di.mvs.getMv(thisBlkX, thisBlkY, 0),
                        di.mvs.getMv(thisBlkX, thisBlkY - 1, 0), di.mvs.getMv(thisBlkX, thisBlkY, 1),
                        di.mvs.getMv(thisBlkX, thisBlkY - 1, 1), mbAddr, mbAddr - mbWidth);
            }
        }
        for (int blkY = 1; blkY < 4; blkY++) {
            for (int blkX = 0; blkX < 4; blkX++) {
                int thisBlkX = (mbX << 2) + blkX;
                int thisBlkY = (mbY << 2) + blkY;
                bs[blkY][blkX] = boundaryStrength(false, thisIntra, thisIntra, di.nCoeff[thisBlkY][thisBlkX],
                        di.nCoeff[thisBlkY - 1][thisBlkX], di.mvs.getMv(thisBlkX, thisBlkY, 0),
                        di.mvs.getMv(thisBlkX, thisBlkY - 1, 0), di.mvs.getMv(thisBlkX, thisBlkY, 1),
                        di.mvs.getMv(thisBlkX, thisBlkY - 1, 1), mbAddr, mbAddr);
            }
        }
    }

    private void calcBsV(int mbAddr, int[][] bs) {
        SliceHeader sh = di.shs[mbAddr];
        int mbX = mbAddr % mbWidth;
        int mbY = mbAddr / mbWidth;
        boolean leftAvailable = mbX > 0 && (sh.disableDeblockingFilterIdc != 2 || di.shs[mbAddr - 1] == sh);
        boolean thisIntra = di.mbTypes[mbAddr] != null && di.mbTypes[mbAddr].isIntra();
        if (leftAvailable) {
            boolean leftIntra = di.mbTypes[mbAddr - 1] != null && di.mbTypes[mbAddr - 1].isIntra();
            for (int blkY = 0; blkY < 4; blkY++) {
                int thisBlkX = mbX << 2;
                int thisBlkY = (mbY << 2) + blkY;
                bs[blkY][0] = boundaryStrength(true, leftIntra, thisIntra, di.nCoeff[thisBlkY][thisBlkX],
                        di.nCoeff[thisBlkY][thisBlkX - 1], di.mvs.getMv(thisBlkX, thisBlkY, 0),
                        di.mvs.getMv(thisBlkX - 1, thisBlkY, 0), di.mvs.getMv(thisBlkX, thisBlkY, 1),
                        di.mvs.getMv(thisBlkX - 1, thisBlkY, 1), mbAddr, mbAddr - 1);
            }
        }
        for (int blkX = 1; blkX < 4; blkX++) {
            for (int blkY = 0; blkY < 4; blkY++) {
                int thisBlkX = (mbX << 2) + blkX;
                int thisBlkY = (mbY << 2) + blkY;
                bs[blkY][blkX] = boundaryStrength(false, thisIntra, thisIntra, di.nCoeff[thisBlkY][thisBlkX],
                        di.nCoeff[thisBlkY][thisBlkX - 1], di.mvs.getMv(thisBlkX, thisBlkY, 0),
                        di.mvs.getMv(thisBlkX - 1, thisBlkY, 0), di.mvs.getMv(thisBlkX, thisBlkY, 1),
                        di.mvs.getMv(thisBlkX - 1, thisBlkY, 1), mbAddr, mbAddr);
            }
        }
    }

    private void fillHorizontalEdge(Picture pic, int comp, int mbAddr, int[][] bs) {
        SliceHeader sh = di.shs[mbAddr];
        int alpha = sh.sliceAlphaC0OffsetDiv2 << 1;
        int beta = sh.sliceBetaOffsetDiv2 << 1;
        int mbX = mbAddr % mbWidth;
        int mbY = mbAddr / mbWidth;
        boolean topAvailable = mbY > 0 && (sh.disableDeblockingFilterIdc != 2 || di.shs[mbAddr - mbWidth] == sh);
        int curQp = di.mbQps[comp][mbAddr];
        int cW = 2 - pic.getColor().compWidth[comp];
        int cH = 2 - pic.getColor().compHeight[comp];
        if (topAvailable) {
            int topQp = di.mbQps[comp][mbAddr - mbWidth];
            int avgQp = (topQp + curQp + 1) >> 1;
            for (int blkX = 0; blkX < 4; blkX++) {
                if (bs[0][blkX] == 0) continue;
                filterBlockEdgeHoris(pic, comp, ((mbX << 2) + blkX) << cW, (mbY << 2) << cH, index(alpha, avgQp),
                        index(beta, avgQp), bs[0][blkX], 1 << cW);
            }
        }
        boolean skip4x4 = comp == 0 && di.tr8x8Used[mbAddr] || cH == 1;
        for (int blkY = 1; blkY < 4; blkY++) {
            if (skip4x4 && (blkY & 1) == 1) continue;
            for (int blkX = 0; blkX < 4; blkX++) {
                if (bs[blkY][blkX] == 0) continue;
                filterBlockEdgeHoris(pic, comp, ((mbX << 2) + blkX) << cW, ((mbY << 2) + blkY) << cH,
                        index(alpha, curQp), index(beta, curQp), bs[blkY][blkX], 1 << cW);
            }
        }
    }

    private void fillVerticalEdge(Picture pic, int comp, int mbAddr, int[][] bs) {
        SliceHeader sh = di.shs[mbAddr];
        int alpha = sh.sliceAlphaC0OffsetDiv2 << 1;
        int beta = sh.sliceBetaOffsetDiv2 << 1;
        int mbX = mbAddr % mbWidth;
        int mbY = mbAddr / mbWidth;
        boolean leftAvailable = mbX > 0 && (sh.disableDeblockingFilterIdc != 2 || di.shs[mbAddr - 1] == sh);
        int curQp = di.mbQps[comp][mbAddr];
        int cW = 2 - pic.getColor().compWidth[comp];
        int cH = 2 - pic.getColor().compHeight[comp];
        if (leftAvailable) {
            int leftQp = di.mbQps[comp][mbAddr - 1];
            int avgQpV = (leftQp + curQp + 1) >> 1;
            for (int blkY = 0; blkY < 4; blkY++) {
                if (bs[blkY][0] == 0) continue;
                filterBlockEdgeVert(pic, comp, (mbX << 2) << cW, ((mbY << 2) + blkY) << cH, index(alpha, avgQpV),
                        index(beta, avgQpV), bs[blkY][0], 1 << cH);
            }
        }
        boolean skip4x4 = comp == 0 && di.tr8x8Used[mbAddr] || cW == 1;
        for (int blkX = 1; blkX < 4; blkX++) {
            if (skip4x4 && (blkX & 1) == 1) continue;
            for (int blkY = 0; blkY < 4; blkY++) {
                if (bs[blkY][blkX] == 0) continue;
                filterBlockEdgeVert(pic, comp, ((mbX << 2) + blkX) << cW, ((mbY << 2) + blkY) << cH,
                        index(alpha, curQp), index(beta, curQp), bs[blkY][blkX], 1 << cH);
            }
        }
    }

    private static void filterBlockEdgeHoris(Picture pic, int comp, int x, int y, int indexAlpha, int indexBeta,
                                             int bs, int blkW) {
        int stride = pic.getPlaneWidth(comp);
        byte[] pels = pic.getPlaneData(comp);
        int offset = y * stride + x;
        boolean chroma = comp != 0;
        for (int pixOff = 0; pixOff < blkW; pixOff++) {
            int p2Idx = offset - 3 * stride + pixOff;
            int p1Idx = offset - 2 * stride + pixOff;
            int p0Idx = offset - stride + pixOff;
            int q0Idx = offset + pixOff;
            int q1Idx = offset + stride + pixOff;
            int q2Idx = offset + 2 * stride + pixOff;
            if (bs == 4) {
                DeblockingFilter.filterBs4(indexAlpha, indexBeta, pels, pels, offset - 4 * stride + pixOff, p2Idx,
                        p1Idx, p0Idx, q0Idx, q1Idx, q2Idx, offset + 3 * stride + pixOff, chroma);
            } else {
                DeblockingFilter.filterBs(bs, indexAlpha, indexBeta, pels, pels, p2Idx, p1Idx, p0Idx, q0Idx, q1Idx,
                        q2Idx, chroma);
            }
        }
    }

    private static void filterBlockEdgeVert(Picture pic, int comp, int x, int y, int indexAlpha, int indexBeta,
                                            int bs, int blkH) {
        int stride = pic.getPlaneWidth(comp);
        byte[] pels = pic.getPlaneData(comp);
        boolean chroma = comp != 0;
        for (int i = 0; i < blkH; i++) {
            int offsetQ = (y + i) * stride + x;
            if (bs == 4) {
                DeblockingFilter.filterBs4(indexAlpha, indexBeta, pels, pels, offsetQ - 4, offsetQ - 3, offsetQ - 2,
                        offsetQ - 1, offsetQ, offsetQ + 1, offsetQ + 2, offsetQ + 3, chroma);
            } else {
                DeblockingFilter.filterBs(bs, indexAlpha, indexBeta, pels, pels, offsetQ - 3, offsetQ - 2,
                        offsetQ - 1, offsetQ, offsetQ + 1, offsetQ + 2, chroma);
            }
        }
    }
}
