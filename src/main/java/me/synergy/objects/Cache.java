package me.synergy.objects;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Cache {
    
    private final UUID uuid;
    private static final Map<UUID, Map<String, CachedData<?>>> cache = new HashMap<>();

    public Cache(UUID uuid) {
        this.uuid = uuid;
        cache.putIfAbsent(uuid, new HashMap<>());
    }

    public <T> void add(String option, T value, long expiryInSeconds) {
        Map<String, CachedData<?>> userCache = cache.get(uuid);
        if (userCache != null) {
            userCache.put(option, new CachedData<>(new DataObject(value), expiryInSeconds));
        }
    }

    public DataObject get(String identifier) {
        Map<String, CachedData<?>> userCache = cache.get(uuid);
        if (userCache == null) return null;

        CachedData<?> cachedData = userCache.get(identifier);
        if (cachedData != null && !isExpired(identifier)) {
            return cachedData.getValue();
        }
        return null;
    }

    public void clear() {
        cache.remove(uuid);
    }

    public boolean isExpired(String identifier) {
        Map<String, CachedData<?>> userCache = cache.get(uuid);
        if (userCache == null) return true;

        CachedData<?> cachedData = userCache.get(identifier);
        return cachedData == null || Instant.now().isAfter(cachedData.getExpiryTime());
    }

    private static class CachedData<T> {
        private final DataObject value;
        private final Instant expiryTime;

        public CachedData(DataObject value, long expiryInSeconds) {
            this.value = value;
            this.expiryTime = Instant.now().plusSeconds(expiryInSeconds);
        }

        public DataObject getValue() {
            return value;
        }

        public Instant getExpiryTime() {
            return expiryTime;
        }
    }

}

