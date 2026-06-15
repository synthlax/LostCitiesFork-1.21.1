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
    private long nextCleanupAt;

    private static volatile int cachedMaxCacheSize = 8192;
    private static volatile long lastMaxCacheSizeRead = 0;

    private volatile int cachedTtlSeconds = -1;
    private volatile long lastTtlRead = 0;

    public TimedCache(IntSupplier ttlSecondsSupplier) {
        this("UnnamedTimedCache", ttlSecondsSupplier);
    }

    public TimedCache(String name, IntSupplier ttlSecondsSupplier) {
        this.name = name;
        this.ttlSecondsSupplier = ttlSecondsSupplier;
        this.nextCleanupAt = System.currentTimeMillis();
        // Use LinkedHashMap with accessOrder=true to act as a highly efficient Least Recently Used (LRU) cache!
        this.cache = new LinkedHashMap<>(16, 0.75f, true);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Cache:{}] Created cache with TTL check interval {}ms", name, getCleanupIntervalMillis());
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

    private static int getMaxCacheSize() {
        long now = System.currentTimeMillis();
        if (now - lastMaxCacheSizeRead > 5000) { // Refresh at most once every 5 seconds
            try {
                if (Config.MAX_CACHE_SIZE != null) {
                    cachedMaxCacheSize = Config.MAX_CACHE_SIZE.get();
                }
            } catch (Exception ignored) {
            }
            lastMaxCacheSizeRead = now;
        }
        return cachedMaxCacheSize;
    }

    private int getTtlSeconds() {
        long now = System.currentTimeMillis();
        if (cachedTtlSeconds == -1 || now - lastTtlRead > 5000) { // Refresh at most once every 5 seconds
            try {
                cachedTtlSeconds = ttlSecondsSupplier.getAsInt();
            } catch (Exception ignored) {
                if (cachedTtlSeconds == -1) {
                    cachedTtlSeconds = 300;
                }
            }
            lastTtlRead = now;
        }
        return cachedTtlSeconds;
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
        int maxSize = getMaxCacheSize();
        while (cache.size() > maxSize) {
            Iterator<Map.Entry<K, Entry<K, V>>> iterator = cache.entrySet().iterator();
            if (iterator.hasNext()) {
                Map.Entry<K, Entry<K, V>> entry = iterator.next();
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Cache:{}] Max size {} exceeded (Current size: {}). Evicting eldest entry key: {}", name, maxSize, cache.size(), entry.getKey());
                }
                iterator.remove();
            } else {
                break;
            }
        }
    }

    public synchronized V get(K key) {
        long now = System.currentTimeMillis();
        processQueue();
        Entry<K, V> entry = cache.get(key); // Automatically updates access order since accessOrder=true
        if (entry == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Cache:{}] Miss for key: {}", name, key);
            }
            maybeCleanup(now);
            return null;
        }
        V val = entry.get();
        if (val == null || isExpired(entry, now)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Cache:{}] Removed expired or GC-collected entry for key: {}", name, key);
            }
            cache.remove(key);
            maybeCleanup(now);
            return null;
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Cache:{}] Hit for key: {}", name, key);
        }
        entry.lastAccess = now;
        maybeCleanup(now);
        return val;
    }

    public synchronized void put(K key, V value) {
        long now = System.currentTimeMillis();
        processQueue();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Cache:{}] Putting entry for key: {}", name, key);
        }
        cache.put(key, new Entry<>(key, value, now, refQueue));
        evictEldest();
        maybeCleanup(now);
    }

    public synchronized V computeIfAbsent(K key, Function<K, V> supplier) {
        long now = System.currentTimeMillis();
        processQueue();
        Entry<K, V> entry = cache.get(key); // Automatically updates access order since accessOrder=true
        if (entry != null) {
            V val = entry.get();
            if (val == null || isExpired(entry, now)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Cache:{}] Evicting expired or GC-collected entry for key: {}", name, key);
                }
                cache.remove(key);
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Cache:{}] Hit (computeIfAbsent) for key: {}", name, key);
                }
                entry.lastAccess = now;
                maybeCleanup(now);
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
        maybeCleanup(now);
        return value;
    }

    private boolean isExpired(Entry<K, V> entry, long now) {
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
        processQueue();
        long ttlMillis = getTtlMillis();
        int sizeBefore = cache.size();
        if (ttlMillis <= 0) {
            if (sizeBefore > 0 && LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Cache:{}] TTL is 0 or negative. Clearing entire cache containing {} entries.", name, sizeBefore);
            }
            cache.clear();
            return;
        }
        int removedCount = 0;
        Iterator<Map.Entry<K, Entry<K, V>>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<K, Entry<K, V>> entry = iterator.next();
            if (entry.getValue().get() == null || now - entry.getValue().lastAccess >= ttlMillis) {
                iterator.remove();
                removedCount++;
            }
        }
        if (removedCount > 0 && LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Cache:{}] Periodic cleanup removed {} expired or GC-collected entries. Size reduced from {} to {}.", name, removedCount, sizeBefore, cache.size());
        }
    }

    private long getCleanupIntervalMillis() {
        long ttlMillis = getTtlMillis();
        return Math.max(1000L, ttlMillis / 2);
    }

    private long getTtlMillis() {
        int ttlSeconds = getTtlSeconds();
        if (ttlSeconds <= 0) {
            return 0L;
        }
        return ttlSeconds * 1000L;
    }
}