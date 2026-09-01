package com.technonext.foodi.config;

import com.technonext.foodi.props.AppProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import redis.clients.jedis.JedisPooled;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory(AppProperties appProperties) {
        return getRedisConnectionFactory(appProperties.getRedisCache().getRead());
    }

    @Bean
    @Primary
    public CacheManager cacheManager(RedisCacheWriter defaultRedisCacheWriter,
                                     RedisTemplate<String, Object> redisReadTemplate,
                                     RedisTemplate<String, Object> redisWriteTemplate,
                                     AppProperties appProperties) {
        RedisCacheConfiguration cacheConfig = getRedisCacheConfiguration(Duration.ofSeconds(appProperties.getRedisCache().getExpiryInSeconds()));
        return new ReadWriteRedisCacheManager(defaultRedisCacheWriter, redisReadTemplate, redisWriteTemplate, cacheConfig);
    }

    @Bean(name = "longLivedCacheManager")
    public CacheManager longLivedCacheManager(RedisCacheWriter defaultRedisCacheWriter,
                                              RedisTemplate<String, Object> redisReadTemplate,
                                              RedisTemplate<String, Object> redisWriteTemplate,
                                              AppProperties appProperties) {
        RedisCacheConfiguration cacheConfig = getRedisCacheConfiguration(Duration.ofDays(appProperties.getRedisCache().getExpiryInDays()));
        return new ReadWriteRedisCacheManager(defaultRedisCacheWriter, redisReadTemplate, redisWriteTemplate, cacheConfig);
    }

    @Bean
    public RedisTemplate<String, Object> redisReadTemplate(AppProperties appProperties) {
        return getRedisTemplate(appProperties.getRedisCache().getRead());
    }

    @Bean
    public RedisTemplate<String, Object> redisWriteTemplate(AppProperties appProperties) {
        return getRedisTemplate(appProperties.getRedisCache().getWrite());
    }

    @Bean
    public RedisCacheWriter defaultRedisCacheWriter(AppProperties appProperties) {
        RedisConnectionFactory writeConnectionFactory = getRedisConnectionFactory(appProperties.getRedisCache().getWrite());
        return RedisCacheWriter.nonLockingRedisCacheWriter(writeConnectionFactory);
    }

    @Bean
    public JedisPooled jedisRead(AppProperties appProperties) {
        AppProperties.RedisConnectionConfig readConfig = appProperties.getRedisCache().getRead();
        return new JedisPooled(readConfig.getHost(), readConfig.getPort(), "default", readConfig.getPassword());
    }

    @Bean
    public JedisPooled jedisWrite(AppProperties appProperties) {
        AppProperties.RedisConnectionConfig write = appProperties.getRedisCache().getWrite();
        return new JedisPooled(write.getHost(), write.getPort(), "default", write.getPassword());
    }

    private RedisTemplate<String, Object> getRedisTemplate(AppProperties.RedisConnectionConfig redisConnectionConfig) {
        RedisConnectionFactory redisConnectionFactory = getRedisConnectionFactory(redisConnectionConfig);

        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    private RedisConnectionFactory getRedisConnectionFactory(AppProperties.RedisConnectionConfig redisConnectionConfig) {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(redisConnectionConfig.getHost(), redisConnectionConfig.getPort());
        redisConfig.setPassword(redisConnectionConfig.getPassword());

        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig);
        factory.afterPropertiesSet();
        return factory;
    }

    private RedisCacheConfiguration getRedisCacheConfiguration(Duration duration) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(duration)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }
}
