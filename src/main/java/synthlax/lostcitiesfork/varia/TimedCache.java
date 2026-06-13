package synthlax.lostcitiesfork.varia;

import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntSupplier;

import synthlax.lostcitiesfork.setup.Config;

public class TimedCache<K, V> {

    private static class Entry<V> {
        private final SoftReference<V> valueRef;
        private long lastAccess;

        private Entry(V value, long lastAccess) {
            this.valueRef = new SoftReference<>(value);
            this.lastAccess = lastAccess;
        }
        
        private V getValue() {
            return valueRef.get();
        }
    }

    private final Map<K, Entry<V>> cache;
    private final IntSupplier ttlSecondsSupplier;
    private long nextCleanupAt;

    public TimedCache(IntSupplier ttlSecondsSupplier) {
        this.ttlSecondsSupplier = ttlSecondsSupplier;
        this.nextCleanupAt = System.currentTimeMillis();
        // Use LinkedHashMap with accessOrder=true to act as a highly efficient Least Recently Used (LRU) cache!
        this.cache = new LinkedHashMap<>(16, 0.75f, true);
    }

    public synchronized void clear() {
        cache.clear();
    }

    private void evictEldest() {
        int maxSize = 8192;
        try {
            if (Config.MAX_CACHE_SIZE != null) {
                maxSize = Config.MAX_CACHE_SIZE.get();
            }
        } catch (Exception ignored) {
        }
        if (cache.size() > maxSize) {
            Iterator<Map.Entry<K, Entry<V>>> iterator = cache.entrySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    public synchronized V get(K key) {
        long now = System.currentTimeMillis();
        Entry<V> entry = cache.get(key); // Automatically updates access order since accessOrder=true
        if (entry == null) {
            maybeCleanup(now);
            return null;
        }
        V val = entry.getValue();
        if (val == null || isExpired(entry, now)) {
            cache.remove(key);
            maybeCleanup(now);
            return null;
        }
        entry.lastAccess = now;
        maybeCleanup(now);
        return val;
    }

    public synchronized void put(K key, V value) {
        long now = System.currentTimeMillis();
        cache.put(key, new Entry<>(value, now));
        evictEldest();
        maybeCleanup(now);
    }

    public synchronized V computeIfAbsent(K key, Function<K, V> supplier) {
        long now = System.currentTimeMillis();
        Entry<V> entry = cache.get(key); // Automatically updates access order since accessOrder=true
        if (entry != null) {
            V val = entry.getValue();
            if (val == null || isExpired(entry, now)) {
                cache.remove(key);
            } else {
                entry.lastAccess = now;
                maybeCleanup(now);
                return val;
            }
        }
        V value = supplier.apply(key);
        if (value != null) {
            cache.put(key, new Entry<>(value, now));
            evictEldest();
        }
        maybeCleanup(now);
        return value;
    }

    private boolean isExpired(Entry<V> entry, long now) {
        return now - entry.lastAccess >= getTtlMillis();
    }

    private synchronized void maybeCleanup(long now) {
        if (now < nextCleanupAt) {
            return;
        }
        cleanup(now);
        nextCleanupAt = now + getCleanupIntervalMillis();
    }

    private synchronized void cleanup(long now) {
        long ttlMillis = getTtlMillis();
        if (ttlMillis <= 0) {
            cache.clear();
            return;
        }
        Iterator<Map.Entry<K, Entry<V>>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<K, Entry<V>> entry = iterator.next();
            if (entry.getValue().getValue() == null || now - entry.getValue().lastAccess >= ttlMillis) {
                iterator.remove();
            }
        }
    }

    private long getCleanupIntervalMillis() {
        long ttlMillis = getTtlMillis();
        return Math.max(1000L, ttlMillis / 2);
    }

    private long getTtlMillis() {
        int ttlSeconds = ttlSecondsSupplier.getAsInt();
        if (ttlSeconds <= 0) {
            return 0L;
        }
        return ttlSeconds * 1000L;
    }
}