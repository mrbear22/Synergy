package me.synergy.modules;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class Timings {

    private static final Map<String, Deque<Long>> starts = new ConcurrentHashMap<>();
    private static final Map<String, Deque<Long>> durations = new ConcurrentHashMap<>();

    private Timings() {}

    public static void startTiming(String id) {
        starts.computeIfAbsent(id, k -> new ConcurrentLinkedDeque<>()).add(System.nanoTime());
    }

    public static void reportTiming(String id, String description) {
        Long start = starts.getOrDefault(id, new ConcurrentLinkedDeque<>()).pollLast();
        if (start == null) return;
        Deque<Long> list = durations.computeIfAbsent(id, k -> new ConcurrentLinkedDeque<>());
        if (list.size() >= 100) list.pollFirst();
        list.add(System.nanoTime() - start);
    }

    public static Map<String, Double> getAllAverages() {
        Map<String, Double> averages = new ConcurrentHashMap<>();
        durations.forEach((id, list) -> averages.put(id, list.stream().mapToDouble(d -> d / 1_000_000.0).average().orElse(0)));
        return averages;
    }
}
