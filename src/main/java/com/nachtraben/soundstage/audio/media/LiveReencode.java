package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.core.video.FfmpegCommand;
import com.nachtraben.soundstage.core.video.PictureSize;
import com.nachtraben.soundstage.core.video.TsDemuxer;
import com.nachtraben.soundstage.core.video.VideoAccessUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

final class LiveReencode implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("Soundstage");

    static final long MAX_BACKLOG_BYTES = 16L << 20;

    private static final int LOGGED_ERROR_LINES = 10;

    private static final byte[] END = new byte[0];

    private final String describes;
    private final PictureSize.Copy picture;
    private final Consumer<VideoAccessUnit> output;
    private final Object lock = new Object();
    private Run current;
    private boolean closed;
    private boolean finishing;
    private volatile String failure;

    LiveReencode(String describes, PictureSize.Copy picture, Consumer<VideoAccessUnit> output) {
        this.describes = describes;
        this.picture = picture;
        this.output = output;
    }

    void start() throws IOException {
        synchronized (lock) {
            current = new Run(Ffmpeg.liveReencode(picture));
        }
    }

    boolean feed(byte[] bytes, int offset, int length) {
        if (length <= 0) return true;
        synchronized (lock) {
            if (closed || current == null) return true;
            if (current.backlog.addAndGet(length) > MAX_BACKLOG_BYTES) return false;
            current.queue.add(Arrays.copyOfRange(bytes, offset, offset + length));
            return true;
        }
    }

    void restart() throws IOException {
        Run old;
        synchronized (lock) {
            if (closed) return;
            old = current;
            current = null;
        }
        if (old != null) old.kill();
        Run next = new Run(Ffmpeg.liveReencode(picture));
        synchronized (lock) {
            if (!closed) {
                current = next;
                return;
            }
        }
        next.kill();
    }

    String failure() {
        return failure;
    }

    void finish(long millis, Runnable ended) {
        Run run;
        synchronized (lock) {
            run = closed || finishing ? null : current;
            if (run != null) {
                finishing = true;
                run.ending = true;
                run.endMillis = millis;
                run.onEnd = ended;
                run.queue.add(END);
            }
        }
        if (run == null) ended.run();
    }

    @Override
    public void close() {
        Run last;
        synchronized (lock) {
            if (closed) return;
            closed = true;
            last = finishing ? null : current;
            current = null;
        }
        if (last != null) last.kill();
    }

    private final class Run {
        final Process process;
        final LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
        final AtomicLong backlog = new AtomicLong();
        final Thread writer;
        final Thread reader;
        final Thread errors;
        volatile boolean forwarding = true;
        volatile boolean ending;
        volatile long endMillis;
        volatile Runnable onEnd;

        Run(Process process) {
            this.process = process;
            try {
                writer = Ffmpeg.daemon("Soundstage-Reencode-In", this::write);
                reader = Ffmpeg.daemon("Soundstage-Reencode-Out", this::read);
                errors = Ffmpeg.daemon("Soundstage-ffmpeg-stderr", this::logErrors);
                writer.start();
                reader.start();
                errors.start();
            } catch (RuntimeException | Error e) {
                Ffmpeg.stop(process);
                throw e;
            }
        }

        private void write() {
            try (OutputStream in = process.getOutputStream()) {
                while (true) {
                    byte[] chunk = queue.poll(1, TimeUnit.SECONDS);
                    if (chunk == null) {
                        if (!forwarding) return;
                        continue;
                    }
                    if (chunk == END) break;
                    backlog.addAndGet(-chunk.length);
                    in.write(chunk);
                    in.flush();
                }
            } catch (IOException e) {
                failed("ffmpeg stopped reading the stream");
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                if (forwarding && !process.waitFor(endMillis, TimeUnit.MILLISECONDS)) kill();
            } catch (InterruptedException e) {
                kill();
            }
        }

        private void read() {
            TsDemuxer demuxer = new TsDemuxer(new TsDemuxer.Listener() {
                @Override
                public void onVideo(VideoAccessUnit unit) {
                    if (forwarding) output.accept(unit);
                }

                @Override
                public void onAudio(long pts90k, int adtsFrameCount) {
                }
            });
            try (InputStream out = process.getInputStream()) {
                byte[] buffer = new byte[64 * 1024];
                for (int n; (n = out.read(buffer)) >= 0; ) demuxer.accept(buffer, 0, n);
                demuxer.flush();
            } catch (IOException ignored) {
            } catch (RuntimeException e) {
                LOGGER.error("Soundstage: the re-encoded picture of {} could not be handed on.", describes, e);
            }
            if (!ending) failed("ffmpeg stopped making the picture");
            Ffmpeg.stop(process);
            Runnable after = onEnd;
            onEnd = null;
            if (after != null) after.run();
        }

        private void logErrors() {
            int[] seen = {0};
            Ffmpeg.readLines(process.getErrorStream(), raw -> {
                String line = FfmpegCommand.withoutUrls(raw);
                if (!forwarding) {
                    LOGGER.debug("Soundstage: ffmpeg, stopped, re-encoding {}: {}", describes, line);
                } else if (seen[0]++ < LOGGED_ERROR_LINES) {
                    LOGGER.warn("Soundstage: ffmpeg re-encoding {}: {}", describes, line);
                }
            });
        }

        private void failed(String why) {
            if (forwarding && !ending && failure == null) failure = why;
        }

        void kill() {
            forwarding = false;
            queue.clear();
            queue.add(END);
            try {
                process.getInputStream().close();
            } catch (IOException ignored) {
            }
            process.destroy();
        }
    }
}
