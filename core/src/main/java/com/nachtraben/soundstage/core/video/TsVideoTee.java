package com.nachtraben.soundstage.core.video;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class TsVideoTee extends FilterInputStream {

    private final TsDemuxer demuxer;

    private final byte[] single = new byte[1];

    public TsVideoTee(InputStream in, TsDemuxer demuxer) {
        super(in);
        this.demuxer = demuxer;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b >= 0) {
            single[0] = (byte) b;
            demuxer.accept(single, 0, 1);
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0) {
            demuxer.accept(b, off, n);
        }
        return n;
    }

    @Override
    public void close() throws IOException {
        demuxer.flush();
        super.close();
    }
}
