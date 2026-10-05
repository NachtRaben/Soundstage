package com.nachtraben.soundstage.core.ffmpeg;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class TarReader {

    enum Type { FILE, SYMLINK, DIRECTORY, OTHER }

    record Entry(String name, Type type, String linkName, long size) {
    }

    private static final int BLOCK = 512;
    private static final int MAX_META = 64 * 1024;

    private final InputStream in;
    private final byte[] header = new byte[BLOCK];
    private long remaining;
    private long padding;
    private boolean ended;

    TarReader(InputStream in) {
        this.in = in;
    }

    Entry next() throws IOException {
        skip(remaining + padding);
        remaining = 0;
        padding = 0;
        if (ended) return null;
        String longName = null;
        String longLink = null;
        String paxPath = null;
        String paxLink = null;
        long paxSize = -1;
        while (true) {
            if (!readBlock()) {
                ended = true;
                return null;
            }
            if (allZero(header)) {
                ended = true;
                return null;
            }
            verifyChecksum();
            char flag = (char) (header[156] & 0xFF);
            long size = parseSize();
            switch (flag) {
                case 'L' -> longName = trimNul(readMeta(size));
                case 'K' -> longLink = trimNul(readMeta(size));
                case 'x' -> {
                    String pax = readMeta(size);
                    for (String[] record : paxRecords(pax)) {
                        switch (record[0]) {
                            case "path" -> paxPath = record[1];
                            case "linkpath" -> paxLink = record[1];
                            case "size" -> paxSize = parseDecimal(record[1]);
                            default -> { }
                        }
                    }
                }
                case 'g' -> skip(size + pad(size));
                default -> {
                    if (paxSize >= 0) size = paxSize;
                    String name = paxPath != null ? paxPath : longName != null ? longName : ustarName();
                    String link = paxLink != null ? paxLink : longLink != null ? longLink : field(157, 100);
                    Type type = switch (flag) {
                        case '0', '\0', '7' -> Type.FILE;
                        case '2' -> Type.SYMLINK;
                        case '5' -> Type.DIRECTORY;
                        default -> Type.OTHER;
                    };
                    remaining = size;
                    padding = pad(size);
                    return new Entry(name, type, type == Type.SYMLINK ? link : null, type == Type.FILE ? size : 0);
                }
            }
        }
    }

    InputStream content() {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                if (remaining <= 0) return -1;
                int b = in.read();
                if (b < 0) throw new EOFException("tar entry cut short");
                remaining--;
                return b;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                if (remaining <= 0) return -1;
                int n = in.read(buffer, offset, (int) Math.min(length, remaining));
                if (n < 0) throw new EOFException("tar entry cut short");
                remaining -= n;
                return n;
            }
        };
    }

    private boolean readBlock() throws IOException {
        int filled = 0;
        while (filled < BLOCK) {
            int n = in.read(header, filled, BLOCK - filled);
            if (n < 0) {
                if (filled == 0) return false;
                throw new EOFException("tar header cut short");
            }
            filled += n;
        }
        return true;
    }

    private String readMeta(long size) throws IOException {
        if (size < 0 || size > MAX_META) throw new IOException("tar metadata entry of " + size + " bytes");
        byte[] data = in.readNBytes((int) size);
        if (data.length != size) throw new EOFException("tar metadata cut short");
        skip(pad(size));
        return new String(data, StandardCharsets.UTF_8);
    }

    private void skip(long count) throws IOException {
        while (count > 0) {
            long n = in.skip(count);
            if (n <= 0) {
                if (in.read() < 0) throw new EOFException("tar entry cut short");
                n = 1;
            }
            count -= n;
        }
    }

    private static long pad(long size) {
        long over = size % BLOCK;
        return over == 0 ? 0 : BLOCK - over;
    }

    private String ustarName() {
        String name = field(0, 100);
        boolean ustar = header[257] == 'u' && header[258] == 's' && header[259] == 't' && header[260] == 'a'
                && header[261] == 'r' && header[262] == 0;
        if (ustar) {
            String prefix = field(345, 155);
            if (!prefix.isEmpty()) return prefix + "/" + name;
        }
        return name;
    }

    private String field(int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) end++;
        return new String(header, offset, end - offset, StandardCharsets.UTF_8);
    }

    private long parseSize() throws IOException {
        if ((header[124] & 0x80) != 0) {
            long value = header[124] & 0x7F;
            for (int i = 125; i < 136; i++) {
                if (value > (Long.MAX_VALUE >> 8)) throw new IOException("tar size out of range");
                value = (value << 8) | (header[i] & 0xFF);
            }
            return value;
        }
        return parseOctal(124, 12);
    }

    private long parseOctal(int offset, int length) throws IOException {
        long value = 0;
        int i = offset;
        int end = offset + length;
        while (i < end && header[i] == ' ') i++;
        for (; i < end; i++) {
            byte b = header[i];
            if (b == 0 || b == ' ') break;
            if (b < '0' || b > '7') throw new IOException("tar header holds a bad number");
            value = (value << 3) | (b - '0');
        }
        return value;
    }

    private void verifyChecksum() throws IOException {
        long stored = parseOctal(148, 8);
        long sum = 0;
        for (int i = 0; i < BLOCK; i++) sum += (i >= 148 && i < 156) ? ' ' : (header[i] & 0xFF);
        if (sum != stored) throw new IOException("tar header checksum does not match");
    }

    private static boolean allZero(byte[] block) {
        for (byte b : block) if (b != 0) return false;
        return true;
    }

    private static String trimNul(String s) {
        int nul = s.indexOf('\0');
        return nul < 0 ? s : s.substring(0, nul);
    }

    private static long parseDecimal(String s) throws IOException {
        try {
            long v = Long.parseLong(s.trim());
            if (v < 0) throw new IOException("negative pax size");
            return v;
        } catch (NumberFormatException e) {
            throw new IOException("pax size is not a number");
        }
    }

    private static java.util.List<String[]> paxRecords(String pax) throws IOException {
        java.util.List<String[]> records = new java.util.ArrayList<>();
        byte[] bytes = pax.getBytes(StandardCharsets.UTF_8);
        int at = 0;
        while (at < bytes.length) {
            int space = at;
            while (space < bytes.length && bytes[space] != ' ') space++;
            if (space >= bytes.length) throw new IOException("pax record without a length");
            int length;
            try {
                length = Integer.parseInt(new String(bytes, at, space - at, StandardCharsets.US_ASCII));
            } catch (NumberFormatException e) {
                throw new IOException("pax record length is not a number");
            }
            if (length <= space - at + 1 || at + length > bytes.length || bytes[at + length - 1] != '\n') {
                throw new IOException("pax record overruns its header");
            }
            String record = new String(bytes, space + 1, at + length - space - 2, StandardCharsets.UTF_8);
            int equals = record.indexOf('=');
            if (equals < 0) throw new IOException("pax record without a value");
            records.add(new String[] {record.substring(0, equals), record.substring(equals + 1)});
            at += length;
        }
        return records;
    }
}
