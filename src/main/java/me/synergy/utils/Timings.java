package me.synergy.utils;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class Timings {

    private static final Map<String, Deque<Long>> timings = new ConcurrentHashMap<>();
    private static final int MAX_TIMINGS = 100;

    public void startTiming(String id) {
        timings.putIfAbsent(id, new ConcurrentLinkedDeque<>());
        timings.get(id).add(System.nanoTime());
    }

    public void endTiming(String id) {
        Deque<Long> deque = timings.get(id);
        if (deque == null || deque.isEmpty()) return;

        long start = deque.pollLast();

        if (start == 0) return;

        long duration = System.nanoTime() - start;

        Deque<Long> durations = timings.getOrDefault(id, new ConcurrentLinkedDeque<>());
        if (durations.size() >= MAX_TIMINGS) {
            durations.pollFirst();
        }
        durations.add(duration);
        timings.put(id, durations);
    }
    
    public double getAverageTiming(String id) {
        Deque<Long> durations = timings.get(id);
        if (durations == null || durations.isEmpty()) return 0;

        return durations.stream().mapToDouble(d -> d / 1_000_000.0).average().orElse(0);
    }

    public Map<String, Double> getAllAverages() {
        Map<String, Double> averages = new ConcurrentHashMap<>();
        timings.forEach((id, durations) -> {
            double avg = durations.stream().mapToDouble(d -> d / 1_000_000.0).average().orElse(0);
            averages.put(id, avg);
        });
        return averages;
    }
    
}