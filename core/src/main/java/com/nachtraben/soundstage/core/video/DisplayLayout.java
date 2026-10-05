package com.nachtraben.soundstage.core.video;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public final class DisplayLayout {

    public static final int MAX_SPAN = 16;

    private final int minCol;
    private final int minRow;
    private final int widthCells;
    private final int heightCells;
    private final int startCol;
    private final int startRow;
    private final Set<Long> localCells;
    private final int anchorCol;
    private final int anchorRow;

    private DisplayLayout(int minCol, int minRow, int widthCells, int heightCells,
                          int startCol, int startRow, Set<Long> localCells) {
        this.minCol = minCol;
        this.minRow = minRow;
        this.widthCells = widthCells;
        this.heightCells = heightCells;
        this.startCol = startCol;
        this.startRow = startRow;
        this.localCells = localCells;
        int col = widthCells, row = heightCells;
        for (long cell : localCells) {
            int c = unpackCol(cell), r = unpackRow(cell);
            if (r < row || (r == row && c < col)) {
                row = r;
                col = c;
            }
        }
        this.anchorCol = col;
        this.anchorRow = row;
    }

    public static long pack(int col, int row) {
        return ((long) col << 32) | (row & 0xFFFFFFFFL);
    }

    public static int unpackCol(long cell) {
        return (int) (cell >> 32);
    }

    public static int unpackRow(long cell) {
        return (int) cell;
    }

    public static DisplayLayout of(Set<Long> cells, long start, int cap) {
        if (!cells.contains(start)) {
            throw new IllegalArgumentException("start must be one of cells");
        }
        int startAbsCol = unpackCol(start);
        int startAbsRow = unpackRow(start);

        Set<Long> visited = new HashSet<>();
        visited.add(start);
        Deque<Long> frontier = new ArrayDeque<>();
        frontier.add(start);

        int minCol = startAbsCol, maxCol = startAbsCol;
        int minRow = startAbsRow, maxRow = startAbsRow;

        while (!frontier.isEmpty()) {
            long current = frontier.poll();
            int col = unpackCol(current);
            int row = unpackRow(current);
            long[] neighbours = {
                    pack(col + 1, row), pack(col - 1, row),
                    pack(col, row + 1), pack(col, row - 1),
            };
            for (long neighbour : neighbours) {
                if (visited.contains(neighbour) || !cells.contains(neighbour)) {
                    continue;
                }
                int nCol = unpackCol(neighbour);
                int nRow = unpackRow(neighbour);
                int newMinCol = Math.min(minCol, nCol);
                int newMaxCol = Math.max(maxCol, nCol);
                int newMinRow = Math.min(minRow, nRow);
                int newMaxRow = Math.max(maxRow, nRow);
                if (newMaxCol - newMinCol + 1 > cap || newMaxRow - newMinRow + 1 > cap) {
                    continue;
                }
                visited.add(neighbour);
                minCol = newMinCol;
                maxCol = newMaxCol;
                minRow = newMinRow;
                maxRow = newMaxRow;
                frontier.add(neighbour);
            }
        }

        Set<Long> localCells = new HashSet<>(visited.size());
        for (long cell : visited) {
            localCells.add(pack(unpackCol(cell) - minCol, unpackRow(cell) - minRow));
        }
        return new DisplayLayout(minCol, minRow, maxCol - minCol + 1, maxRow - minRow + 1,
                startAbsCol - minCol, startAbsRow - minRow, localCells);
    }

    public int minCol() { return minCol; }
    public int minRow() { return minRow; }
    public int widthCells() { return widthCells; }
    public int heightCells() { return heightCells; }

    public int startCol() { return startCol; }

    public int startRow() { return startRow; }

    public boolean contains(int localCol, int localRow) {
        return localCells.contains(pack(localCol, localRow));
    }

    public int anchorCol() { return anchorCol; }

    public int anchorRow() { return anchorRow; }

    public Rect captionArea(Rect visible) {
        final double edge = 1e-9;
        double x0 = visible.x(), x1 = visible.x() + visible.width();
        double y1 = visible.y() + visible.height();
        int firstCol = (int) Math.floor(x0 + edge), lastCol = (int) Math.ceil(x1 - edge) - 1;
        for (int row = 0; row < y1 - edge; row++) {
            double bestLeft = 0, bestRight = 0;
            int runStart = Integer.MIN_VALUE;
            for (int col = firstCol; col <= lastCol + 1; col++) {
                boolean present = col <= lastCol && contains(col, row);
                if (present && runStart == Integer.MIN_VALUE) runStart = col;
                if (!present && runStart != Integer.MIN_VALUE) {
                    double left = Math.max(x0, runStart), right = Math.min(x1, col);
                    if (right - left > bestRight - bestLeft) {
                        bestLeft = left;
                        bestRight = right;
                    }
                    runStart = Integer.MIN_VALUE;
                }
            }
            if (bestRight > bestLeft) {
                return new Rect(bestLeft, row, bestRight - bestLeft, y1 - row);
            }
        }
        return null;
    }

    public record Rect(double x, double y, double width, double height) {}

    public record Slice(double cellX, double cellY, double cellWidth, double cellHeight,
                        double u0, double v0, double u1, double v1) {}

    public static Rect fit(int videoW, int videoH, double displayW, double displayH) {
        double scale = Math.min(displayW / videoW, displayH / videoH);
        double width = videoW * scale;
        double height = videoH * scale;
        return new Rect((displayW - width) / 2.0, (displayH - height) / 2.0, width, height);
    }

    public static Rect fill(int videoW, int videoH, double displayW, double displayH) {
        double scale = Math.max(displayW / videoW, displayH / videoH);
        double width = videoW * scale;
        double height = videoH * scale;
        return new Rect((displayW - width) / 2.0, (displayH - height) / 2.0, width, height);
    }

    public static Slice slice(Rect picture, int col, int row) {
        double cellLeft = col;
        double cellRight = col + 1.0;
        double cellBottom = row;
        double cellTop = row + 1.0;

        double overlapLeft = Math.max(cellLeft, picture.x());
        double overlapRight = Math.min(cellRight, picture.x() + picture.width());
        double overlapBottom = Math.max(cellBottom, picture.y());
        double overlapTop = Math.min(cellTop, picture.y() + picture.height());

        if (overlapLeft >= overlapRight || overlapBottom >= overlapTop) {
            return null;
        }

        double u0 = (overlapLeft - picture.x()) / picture.width();
        double u1 = (overlapRight - picture.x()) / picture.width();
        double v0 = (picture.y() + picture.height() - overlapTop) / picture.height();
        double v1 = (picture.y() + picture.height() - overlapBottom) / picture.height();

        return new Slice(overlapLeft - cellLeft, overlapBottom - cellBottom,
                overlapRight - overlapLeft, overlapTop - overlapBottom,
                u0, v0, u1, v1);
    }
}
