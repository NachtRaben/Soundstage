package com.nachtraben.soundstage.core.video;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public abstract class DecodeWorkers {

    public static final int MAX_THREADS = 4;
    public static final int LEFT_TO_GAME = 2;

    public static int threadsFor(int processors) {
        return Math.max(1, Math.min(MAX_THREADS, processors - LEFT_TO_GAME));
    }

    public abstract void run(int tasks, IntConsumer body);

    public abstract int parallelism();

    public static DecodeWorkers direct() {
        return Direct.INSTANCE;
    }

    public static DecodeWorkers pool(String name, int threads, Consumer<Thread> prepare) {
        return new Pool(name, threads, prepare);
    }

    private static final class Direct extends DecodeWorkers {
        static final Direct INSTANCE = new Direct();

        @Override
        public void run(int tasks, IntConsumer body) {
            for (int task = 0; task < tasks; task++) body.accept(task);
        }

        @Override
        public int parallelism() {
            return 1;
        }
    }

    private static final class Batch {
        final IntConsumer body;
        final int tasks;
        final AtomicInteger next = new AtomicInteger();
        final AtomicInteger unfinished;
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread caller = Thread.currentThread();

        Batch(int tasks, IntConsumer body) {
            this.tasks = tasks;
            this.body = body;
            this.unfinished = new AtomicInteger(tasks);
        }

        boolean work() {
            boolean last = false;
            int task;
            while ((task = next.getAndIncrement()) < tasks) {
                try {
                    body.accept(task);
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
                if (unfinished.decrementAndGet() == 0) last = true;
            }
            return last;
        }

        boolean taken() {
            return next.get() >= tasks;
        }
    }

    private static final class Pool extends DecodeWorkers {
        private final String name;
        private final int threads;
        private final Consumer<Thread> prepare;
        private final ArrayDeque<Batch> queue = new ArrayDeque<>();
        private boolean started;

        Pool(String name, int threads, Consumer<Thread> prepare) {
            this.name = name;
            this.threads = Math.max(1, threads);
            this.prepare = prepare;
        }

        @Override
        public int parallelism() {
            return threads;
        }

        @Override
        public void run(int tasks, IntConsumer body) {
            if (tasks <= 0) return;
            Batch batch = new Batch(tasks, body);
            synchronized (queue) {
                if (!started) start();
                queue.addLast(batch);
                queue.notifyAll();
            }
            boolean interrupted = false;
            while (batch.unfinished.get() > 0) {
                LockSupport.park(this);
                if (Thread.interrupted()) interrupted = true;
            }
            if (interrupted) Thread.currentThread().interrupt();
            Throwable failure = batch.failure.get();
            if (failure instanceof RuntimeException e) throw e;
            if (failure instanceof Error e) throw e;
            if (failure != null) throw new IllegalStateException(failure);
        }

        private void start() {
            started = true;
            for (int i = 1; i <= threads; i++) {
                Thread thread = new Thread(this::loop, name + "-" + i);
                thread.setDaemon(true);
                thread.start();
            }
        }

        private void loop() {
            try {
                prepare.accept(Thread.currentThread());
            } catch (RuntimeException e) {
            }
            while (true) {
                Batch batch;
                synchronized (queue) {
                    while ((batch = queue.peekFirst()) == null) {
                        try {
                            queue.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }
                if (batch.work()) LockSupport.unpark(batch.caller);
                synchronized (queue) {
                    if (batch.taken()) queue.remove(batch);
                }
            }
        }
    }
}
