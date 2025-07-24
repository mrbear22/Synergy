package me.synergy.utils;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class Timings {
    private static final Map<String, Deque<Long>> startTimes = new ConcurrentHashMap<>();
    private static final Map<String, Deque<Long>> durations = new ConcurrentHashMap<>();
    private static final int MAX_TIMINGS = 100;
    
    public void startTiming(String id) {
        startTimes.putIfAbsent(id, new ConcurrentLinkedDeque<>());
        startTimes.get(id).add(System.nanoTime());
    }
    
    public void reportTiming(String name, String description) {
        Deque<Long> startDeque = startTimes.get(name);
        if (startDeque == null || startDeque.isEmpty()) return;
        
        long start = startDeque.pollLast();
        if (start == 0) return;
        
        long duration = System.nanoTime() - start;
        double durationMs = duration / 1_000_000.0;

        if (durationMs > 1.0) {
            System.out.printf("[TIMING] %s: %.2f ms - %s%n", name, durationMs, description);
        }
        
        durations.putIfAbsent(name, new ConcurrentLinkedDeque<>());
        Deque<Long> durationDeque = durations.get(name);
        
        if (durationDeque.size() >= MAX_TIMINGS) {
            durationDeque.pollFirst();
        }
        durationDeque.add(duration);
    }
    
    public double getAverageTiming(String name) {
        Deque<Long> durationDeque = durations.get(name);
        if (durationDeque == null || durationDeque.isEmpty()) return 0;
        return durationDeque.stream().mapToDouble(d -> d / 1_000_000.0).average().orElse(0);
    }
    
    public Map<String, Double> getAllAverages() {
        Map<String, Double> averages = new ConcurrentHashMap<>();
        durations.forEach((name, durationDeque) -> {
            double avg = durationDeque.stream().mapToDouble(d -> d / 1_000_000.0).average().orElse(0);
            averages.put(name, avg);
        });
        return averages;
    }
    
    public void printTimingStats() {
        System.out.println("\n=== TIMING STATISTICS ===");
        getAllAverages().forEach((name, avgMs) -> {
            System.out.printf("%s: %.2f ms (avg)%n", name, avgMs);
        });
        System.out.println("========================\n");
    }

    public void clearAll() {
        startTimes.clear();
        durations.clear();
    }

    public void clear(String name) {
        startTimes.remove(name);
        durations.remove(name);
    }
}