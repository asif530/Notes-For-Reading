package com.technonext.foodi.config;

import jakarta.annotation.Nonnull;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.lang.Nullable;

import java.time.Duration;

public class ReadWriteRedisCache extends RedisCache {
    private final RedisOperations<String, Object> readRedisOperations;
    private final RedisOperations<String, Object> writeRedisOperations;

    public ReadWriteRedisCache(String name,
                               RedisCacheWriter defaultCacheWriter,
                               RedisOperations<String, Object> readRedisOperations,
                               RedisOperations<String, Object> writeRedisOperations,
                               RedisCacheConfiguration cacheConfig) {
        super(name, defaultCacheWriter, cacheConfig);
        this.readRedisOperations = readRedisOperations;
        this.writeRedisOperations = writeRedisOperations;
    }

    @Override
    public ValueWrapper get(@Nonnull Object key) {
        String cacheKey = createCacheKey(key);
        Object value = readRedisOperations.opsForValue().get(cacheKey);

        return (value != null ? () -> value : null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(@Nonnull Object key, @Nullable Class<T> type) {
        String cacheKey = createCacheKey(key);
        Object value = readRedisOperations.opsForValue().get(cacheKey);

        if (value == null) {
            return null;
        }

        if (type != null && !type.isInstance(value)) {
            throw new IllegalStateException("Cached value is not of required type [" + type.getName() + "]");
        }

        return (T) value;
    }

    @Override
    public void put(@Nonnull Object key, Object value) {
        RedisCacheWriter.TtlFunction ttlFunction = getCacheConfiguration().getTtlFunction();
        Duration ttlDuration = ttlFunction.getTimeToLive(key, value);

        String cacheKey = createCacheKey(key);
        if (ttlDuration.isZero() || ttlDuration.isNegative()) {
            writeRedisOperations.opsForValue().set(cacheKey, value);
        } else {
            writeRedisOperations.opsForValue().set(cacheKey, value, ttlDuration);
        }
    }

    @Override
    public void evict(@Nonnull Object key) {
        String cacheKey = createCacheKey(key);
        writeRedisOperations.delete(cacheKey);
    }
}
