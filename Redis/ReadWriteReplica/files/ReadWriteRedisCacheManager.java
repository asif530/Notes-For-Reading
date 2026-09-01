package com.technonext.foodi.config;

import jakarta.annotation.Nonnull;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.core.RedisOperations;

public class ReadWriteRedisCacheManager extends RedisCacheManager {
    private final RedisCacheWriter defaultCacheWriter;
    private final RedisOperations<String, Object> readRedisOperations;
    private final RedisOperations<String, Object> writeRedisOperations;
    private final RedisCacheConfiguration config;

    public ReadWriteRedisCacheManager(RedisCacheWriter defaultCacheWriter,
                                      RedisOperations<String, Object> readRedisOperations,
                                      RedisOperations<String, Object> writeRedisOperations,
                                      RedisCacheConfiguration config) {
        super(defaultCacheWriter, config);
        this.defaultCacheWriter = defaultCacheWriter;
        this.readRedisOperations = readRedisOperations;
        this.writeRedisOperations = writeRedisOperations;
        this.config = config;
    }

    @Override
    @Nonnull
    protected RedisCache createRedisCache(@Nonnull String name, RedisCacheConfiguration cacheConfig) {
        return new ReadWriteRedisCache(name, defaultCacheWriter, readRedisOperations, writeRedisOperations,
                cacheConfig != null ? cacheConfig : config);
    }
}
