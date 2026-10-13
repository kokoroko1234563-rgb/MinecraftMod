package io.github.kokoroko1234563.axiomatic.machine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Worker threads for heavy machine computations (witness searches, compiled functions, prime
 * forging), so the server tick never waits on mathematics. Work submitted here must only compute:
 * it gets copies of the definitions and returns a result the machine commits on the server thread.
 */
public final class Async {
    private Async() {}

    private static final AtomicInteger COUNT = new AtomicInteger();

    public static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1)), r -> {
                Thread t = new Thread(r, "Axiomatic computation " + COUNT.incrementAndGet());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });
}
