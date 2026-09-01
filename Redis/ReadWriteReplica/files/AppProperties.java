package com.technonext.foodi.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties("app")
public class AppProperties {
    private RedisCacheConfig redisCache;

    @Data
    public static class RedisCacheConfig {
        private RedisConnectionConfig read;
        private RedisConnectionConfig write;
        private Integer expiryInSeconds;
        private Integer expiryInDays;
    }

    @Data
    public static class RedisConnectionConfig {
        private String host;
        private Integer port;
        private String password;
    }
}
