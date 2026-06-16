package synthlax.lostcitiesfork.varia;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntSupplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import synthlax.lostcitiesfork.setup.Config;

public class TimedCache<K, V> {

    private static final Logger LOGGER = LogManager.getLogger(TimedCache.class);
    private final String name;

    private static class Entry<K, V> extends SoftReference<V> {
        private final K key;
        private long lastAccess;

        private Entry(K key, V value, long lastAccess, ReferenceQueue<V> queue) {
            super(value, queue);
            this.key = key;
            this.lastAccess = lastAccess;
        }
    }

    private final Map<K, Entry<K, V>> cache;
    private final ReferenceQueue<V> refQueue = new ReferenceQueue<>();
    private final IntSupplier ttlSecondsSupplier;
    private final int cachedTtlSeconds;
    private final int maxCacheSize;

    public TimedCache(IntSupplier ttlSecondsSupplier) {
        this("UnnamedTimedCache", ttlSecondsSupplier);
    }

    public TimedCache(String name, IntSupplier ttlSecondsSupplier) {
        this.name = name;
        this.ttlSecondsSupplier = ttlSecondsSupplier;
        
        int ttl = 15;
        try {
            if (ttlSecondsSupplier != null) {
                ttl = ttlSecondsSupplier.getAsInt();
            }
        } catch (Throwable ignored) {}
        this.cachedTtlSeconds = ttl;

        int maxSize = 512;
        try {
            if (Config.MAX_CACHE_SIZE != null) {
                maxSize = Config.MAX_CACHE_SIZE.get();
            }
        } catch (Throwable ignored) {}
        this.maxCacheSize = maxSize;

        // Use LinkedHashMap with accessOrder=true to act as a highly efficient Least Recently Used (LRU) cache!
        this.cache = new LinkedHashMap<>(16, 0.75f, true);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Cache:{}] Created cache with real-time O(k) cleanup. TTL: {}s, MaxSize: {}", name, cachedTtlSeconds, maxCacheSize);
        }
    }

    public synchronized void clear() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Cache:{}] Clearing cache containing {} entries", name, cache.size());
        }
        cache.clear();
        while (refQueue.poll() != null) {
            // Clear out reference queue
        }
    }

    private void processQueue() {
        Reference<? extends V> ref;
        int count = 0;
        while ((ref = refQueue.poll()) != null) {
            @SuppressWarnings("unchecked")
            Entry<K, V> entry = (Entry<K, V>) ref;
            if (cache.remove(entry.key) != null) {
                count++;
            }
        }
        if (count > 0 && LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Cache:{}] GC collected and evicted {} soft-referenced entries.", name, count);
        }
    }

    private void evictEldest() {
        while (cache.size() > maxCacheSize) {
            Iterator<Map.Entry<K, Entry<K, V>>> iterator = cache.entrySet().iterator();
            if (iterator.hasNext()) {
                Map.Entry<K, Entry<K, V>> entry = iterator.next();
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Cache:{}] Max size {} exceeded (Current size: {}). Evicting eldest entry key: {}", name, maxCacheSize, cache.size(), entry.getKey());
                }
                iterator.remove();
            } else {
                break;
            }
        }
    }

    private void cleanExpired(long now) {
        long ttlMillis = getTtlMillis();
        if (ttlMillis <= 0) {
            if (!cache.isEmpty()) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Cache:{}] TTL is 0 or negative. Clearing entire cache.", name);
                }
                cache.clear();
            }
            return;
        }
        int removedCount = 0;
        Iterator<Map.Entry<K, Entry<K, V>>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<K, Entry<K, V>> entry = iterator.next();
            if (entry.getValue().get() == null || now - entry.getValue().lastAccess >= ttlMillis) {
                iterator.remove();
                removedCount++;
            } else {
                break; // Since the map is in access-order, all subsequent entries are newer/non-expired!
            }
        }
        if (removedCount > 0 && LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Cache:{}] Real-time cleanup removed {} expired/GCed entries. (Remaining: {})", name, removedCount, cache.size());
        }
    }

    public synchronized V get(K key) {
        long now = System.currentTimeMillis();
        processQueue();
        cleanExpired(now);
        Entry<K, V> entry = cache.get(key); // Automatically updates access order since accessOrder=true
        if (entry == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Cache:{}] Miss for key: {}", name, key);
            }
            return null;
        }
        V val = entry.get();
        if (val == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Cache:{}] Removed GC-collected entry for key: {}", name, key);
            }
            cache.remove(key);
            return null;
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Cache:{}] Hit for key: {}", name, key);
        }
        entry.lastAccess = now;
        return val;
    }

    public synchronized void put(K key, V value) {
        long now = System.currentTimeMillis();
        processQueue();
        cleanExpired(now);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Cache:{}] Putting entry for key: {}", name, key);
        }
        cache.put(key, new Entry<>(key, value, now, refQueue));
        evictEldest();
    }

    public synchronized V computeIfAbsent(K key, Function<K, V> supplier) {
        long now = System.currentTimeMillis();
        processQueue();
        cleanExpired(now);
        Entry<K, V> entry = cache.get(key); // Automatically updates access order since accessOrder=true
        if (entry != null) {
            V val = entry.get();
            if (val == null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Cache:{}] Evicting GC-collected entry for key: {}", name, key);
                }
                cache.remove(key);
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Cache:{}] Hit (computeIfAbsent) for key: {}", name, key);
                }
                entry.lastAccess = now;
                return val;
            }
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Cache:{}] Miss (computeIfAbsent) for key: {}", name, key);
        }
        V value = supplier.apply(key);
        if (value != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Cache:{}] Putting computed entry for key: {}", name, key);
            }
            cache.put(key, new Entry<>(key, value, now, refQueue));
            evictEldest();
        }
        return value;
    }

    private long getTtlMillis() {
        if (cachedTtlSeconds <= 0) {
            return 0L;
        }
        return cachedTtlSeconds * 1000L;
    }
}