package me.synergy.modules;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;

import me.synergy.brains.Synergy;

public final class Tasks {

    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("Synergy-Timer").factory());
    private static final ExecutorService WORKERS = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Synergy-", 0).factory());

    private Tasks() {}

    public static ExecutorService executor() {
        return WORKERS;
    }

    public static void async(Runnable task) {
        WORKERS.execute(guard(task));
    }

    public static <T> CompletableFuture<T> supply(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        WORKERS.execute(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public static ScheduledFuture<?> later(Runnable task, Duration delay) {
        return TIMER.schedule(() -> async(task), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    public static ScheduledFuture<?> every(Runnable task, Duration period) {
        return TIMER.scheduleWithFixedDelay(() -> async(task), period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
    }

    public static void stop() {
        TIMER.shutdownNow();
        WORKERS.shutdownNow();
    }

    private static Runnable guard(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable e) {
                Synergy.getLogger().error("Task failed: " + e, e);
            }
        };
    }
}
