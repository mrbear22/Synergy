package me.synergy.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class RepeatingTask {

    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(2);
    private static final ConcurrentHashMap<RepeatingTask, ScheduledFuture<?>> TASKS = new ConcurrentHashMap<>();

    private final Consumer<RepeatingTask> task;
    private final Runnable simpleTask;
    private Supplier<Boolean> untilCondition;

    @FunctionalInterface
    public interface StoppableTask {
        void run(RepeatingTask task);
    }

    public RepeatingTask(Runnable task) {
        this.simpleTask = task;
        this.task = null;
    }

    public RepeatingTask(StoppableTask task) {
        this.task = task::run;
        this.simpleTask = null;
    }

    public RepeatingTask() {
        this.task = null;
        this.simpleTask = null;
    }

    public RepeatingTask until(Supplier<Boolean> condition) {
        this.untilCondition = condition;
        return this;
    }

    public RepeatingTask until(boolean condition) {
        this.untilCondition = () -> condition;
        return this;
    }

    public RepeatingTask whileOnline(org.bukkit.entity.Player player) {
        this.untilCondition = () -> !player.isOnline();
        return this;
    }

    public RepeatingTask whileAlive(org.bukkit.entity.Player player) {
        this.untilCondition = () -> player.isDead();
        return this;
    }

    public RepeatingTask forTimes(int maxTimes) {
        final int[] counter = {0};
        this.untilCondition = () -> ++counter[0] >= maxTimes;
        return this;
    }

    public RepeatingTask every30Seconds() {
        return start(30_000);
    }

    public RepeatingTask every5Minutes() {
        return start(300_000);
    }

    public RepeatingTask everyHour() {
        return start(3_600_000);
    }

    public RepeatingTask start(long intervalMillis) {
        if (task == null && simpleTask == null) return this;

        stop();
        
        Runnable taskToRun;
        if (task != null) {
            taskToRun = () -> {
                if (untilCondition != null && untilCondition.get()) {
                    stop();
                    return;
                }
                task.accept(this);
            };
        } else {
            taskToRun = () -> {
                if (untilCondition != null && untilCondition.get()) {
                    stop();
                    return;
                }
                simpleTask.run();
            };
        }
        
        ScheduledFuture<?> future = EXECUTOR.scheduleAtFixedRate(taskToRun, 0, intervalMillis, TimeUnit.MILLISECONDS);
        TASKS.put(this, future);
        return this;
    }

    public void stop() {
        ScheduledFuture<?> future = TASKS.remove(this);
        if (future != null) {
            future.cancel(false);
        }
    }

    public void shutdown() {
        TASKS.values().forEach(future -> future.cancel(false));
        TASKS.clear();
        EXECUTOR.shutdown();
    }
}