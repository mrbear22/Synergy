package me.synergy.objects;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import me.synergy.modules.Tasks;

public final class Cache {

    private record Entry(DataObject value, long expires) {}

    private static final Map<UUID, Map<String, Entry>> CACHE = new ConcurrentHashMap<>();

    static {
        Tasks.every(Cache::purge, Duration.ofMinutes(5));
    }

    private final UUID uuid;

    public Cache(UUID uuid) {
        this.uuid = uuid;
    }

    public void add(String key, Object value, long seconds) {
        if (uuid != null) CACHE.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>()).put(key, new Entry(new DataObject(value), System.currentTimeMillis() + seconds * 1000));
    }

    public DataObject get(String key) {
        Entry entry = uuid == null ? null : CACHE.getOrDefault(uuid, Map.of()).get(key);
        return entry != null && entry.expires() > System.currentTimeMillis() ? entry.value() : null;
    }

    public boolean has(String key) {
        return get(key) != null;
    }

    public void remove(String key) {
        if (uuid != null) CACHE.getOrDefault(uuid, Map.of()).remove(key);
    }

    public void clear() {
        if (uuid != null) CACHE.remove(uuid);
    }

    private static void purge() {
        long now = System.currentTimeMillis();
        CACHE.values().forEach(entries -> entries.values().removeIf(entry -> entry.expires() <= now));
        CACHE.values().removeIf(Map::isEmpty);
    }
}
