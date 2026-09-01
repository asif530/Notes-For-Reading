❯ Analyze ReadWriteRedisCache, ReadWriteRedisCacheManager, RedisConfig. Make a summary.
Architecture: Read/Write Splitting for Redis

This setup implements read/write splitting — reads go to a Redis replica, writes go to the Redis primary. This is a common pattern for scaling Redis in production.
                                                                                                                                                                                    
---                                                                                                                                                                               
RedisConfig (Configuration Hub)

Defines all Redis beans with two separate connection paths:                                                                                                                       
┌─────────────────────────┬────────────────────────────────────────────────────┬──────────────────────────────────────────┐                                                       
│          Bean           │                      Purpose                       │                Connection                │                                                       
├─────────────────────────┼────────────────────────────────────────────────────┼──────────────────────────────────────────┤                                                       
│ redisReadTemplate       │ Read operations                                    │ appProperties.redisCache.read (replica)  │                                                       
├─────────────────────────┼────────────────────────────────────────────────────┼──────────────────────────────────────────┤                                                       
│ redisWriteTemplate      │ Write operations                                   │ appProperties.redisCache.write (primary) │                                                       
├─────────────────────────┼────────────────────────────────────────────────────┼──────────────────────────────────────────┤                                                       
│ defaultRedisCacheWriter │ Fallback writer for RedisCacheManager parent class │ Write connection                         │                                                       
├─────────────────────────┼────────────────────────────────────────────────────┼──────────────────────────────────────────┤                                                       
│ cacheManager (@Primary) │ Default cache manager                              │ TTL in seconds                           │                                                       
├─────────────────────────┼────────────────────────────────────────────────────┼──────────────────────────────────────────┤                                                       
│ longLivedCacheManager   │ Named cache manager                                │ TTL in days                              │                                                       
├─────────────────────────┼────────────────────────────────────────────────────┼──────────────────────────────────────────┤                                                       
│ jedisRead / jedisWrite  │ Jedis clients (for non-Spring-cache usage)         │ Read/Write respectively                  │                                                       
└─────────────────────────┴────────────────────────────────────────────────────┴──────────────────────────────────────────┘                                                       
Both cache managers are ReadWriteRedisCacheManager instances, differing only in TTL duration. Serialization uses StringRedisSerializer for keys and                               
GenericJackson2JsonRedisSerializer for values (JSON format).
                                                                                                                                                                                    
---                                                                                                                                                                               
ReadWriteRedisCacheManager (Cache Manager)

Extends RedisCacheManager. Its sole job is to override createRedisCache() so that every cache created by Spring's @Cacheable infrastructure produces a ReadWriteRedisCache instead
of the default RedisCache. It passes both the read and write RedisOperations into each cache instance.
                                                                                                                                                                                    
---                                                                                                                                                                               
ReadWriteRedisCache (Cache Implementation)

Extends RedisCache and overrides the core operations to split reads and writes:                                                                                                   
┌─────────────────┬──────────────────────┬────────────────┐                                                                                                                       
│    Operation    │         Uses         │ Redis Instance │                                                                                                                       
├─────────────────┼──────────────────────┼────────────────┤                                                                                                                       
│ get(key)        │ readRedisOperations  │ Replica        │                                                                                                                       
├─────────────────┼──────────────────────┼────────────────┤                                                                                                                       
│ get(key, type)  │ readRedisOperations  │ Replica        │                                                                                                                       
├─────────────────┼──────────────────────┼────────────────┤                                                                                                                       
│ put(key, value) │ writeRedisOperations │ Primary        │                                                                                                                       
├─────────────────┼──────────────────────┼────────────────┤                                                                                                                       
│ evict(key)      │ writeRedisOperations │ Primary        │                                                                                                                       
└─────────────────┴──────────────────────┴────────────────┘                                                                                                                       
The put method reads the TTL from RedisCacheConfiguration.getTtlFunction() and applies it. If TTL is zero/negative, it stores without expiry.
                                                                                                                                                                                    
---                                                                                                                                                                               
Flow Summary

@Cacheable annotation                                                                                                                                                             
→ Spring AOP proxy intercepts                                                                                                                                                   
→ CacheManager.getCache(name)                                                                                                                                                 
→ ReadWriteRedisCacheManager.createRedisCache()                                                                                                                             
→ ReadWriteRedisCache instance                                                                                                                                            
→ get() → reads from REPLICA                                                                                                                                            
→ put() → writes to PRIMARY

Key Design Decisions

1. Read/write split at the Spring Cache abstraction level — transparent to all @Cacheable/@CacheEvict callers
2. Two TTL tiers — short-lived (seconds, @Primary) and long-lived (days, named longLivedCacheManager)
3. JSON serialization — GenericJackson2JsonRedisSerializer stores type info in JSON, enabling deserialization without knowing the class upfront
4. Dual client libraries — Lettuce (via LettuceConnectionFactory for Spring templates) and Jedis (JedisPooled beans for direct usage outside Spring Cache)                        

Guide of read write redis cache setup.    
Read/Write Redis Cache Setup — Guide

1. Purpose

This setup implements read/write splitting for Redis caching. All cache reads go to a Redis replica and all writes go to the Redis primary. This offloads read traffic from the   
primary node, which is the standard scaling pattern for Redis replication.
                                                                                                                                                                                    
---                                                                                                                                                                               
2. Configuration (YAML)

app:                                                                                                                                                                              
redis-cache:                                                                                                                                                                    
read:                                                                                                                                                                         
host: ${REDIS_READ_REPLICA_HOST}                                                                                                                                            
port: ${REDIS_READ_REPLICA_PORT}                                                                                                                                            
password: ${REDIS_READ_REPLICA_PASSWORD}                                                                                                                                    
write:                                                                                                                                                                        
host: ${REDIS_WRITE_REPLICA_HOST}                                                                                                                                           
port: ${REDIS_WRITE_REPLICA_PORT}                                                                                                                                           
password: ${REDIS_WRITE_REPLICA_PASSWORD}                                                                                                                                   
expiry-in-seconds: ${REDIS_CACHE_EXPIRY_IN_SECONDS}                                                                                                                           
expiry-in-days: 1

These bind to AppProperties.RedisCacheConfig which holds two RedisConnectionConfig objects (read/write) plus two TTL values.
                                                                                                                                                                                    
---                                                                                                                                                                               
3. The Three Classes and Their Roles

3.1 RedisConfig — Bean Wiring

File: config/RedisConfig.java

This is the @Configuration class that creates all beans. The key beans:                                                                                                           
┌─────────────────────────┬───────────────────────────────┬─────────────────────────────────────────────────────────────────────────────┐                                         
│          Bean           │             Type              │                                What it does                                 │                                         
├─────────────────────────┼───────────────────────────────┼─────────────────────────────────────────────────────────────────────────────┤                                         
│ redisReadTemplate       │ RedisTemplate<String, Object> │ Template connected to the read replica                                      │                                         
├─────────────────────────┼───────────────────────────────┼─────────────────────────────────────────────────────────────────────────────┤                                         
│ redisWriteTemplate      │ RedisTemplate<String, Object> │ Template connected to the write primary                                     │                                         
├─────────────────────────┼───────────────────────────────┼─────────────────────────────────────────────────────────────────────────────┤                                         
│ defaultRedisCacheWriter │ RedisCacheWriter              │ Used internally by the parent RedisCacheManager; connects to write instance │                                         
├─────────────────────────┼───────────────────────────────┼─────────────────────────────────────────────────────────────────────────────┤                                         
│ cacheManager (@Primary) │ CacheManager                  │ Default cache manager, TTL = expiryInSeconds                                │                                         
├─────────────────────────┼───────────────────────────────┼─────────────────────────────────────────────────────────────────────────────┤                                         
│ longLivedCacheManager   │ CacheManager                  │ Named cache manager, TTL = expiryInDays                                     │                                         
├─────────────────────────┼───────────────────────────────┼─────────────────────────────────────────────────────────────────────────────┤                                         
│ jedisRead / jedisWrite  │ JedisPooled                   │ Raw Jedis clients for code that bypasses Spring Cache                       │                                         
└─────────────────────────┴───────────────────────────────┴─────────────────────────────────────────────────────────────────────────────┘                                         
Both cache managers are ReadWriteRedisCacheManager instances. The only difference is the TTL configured in their RedisCacheConfiguration.

Serialization: Keys use StringRedisSerializer, values use GenericJackson2JsonRedisSerializer (JSON with @class type info embedded).

Each RedisTemplate and RedisCacheWriter gets its own LettuceConnectionFactory pointing to the respective host.

3.2 ReadWriteRedisCacheManager — Cache Factory

File: config/ReadWriteRedisCacheManager.java

Extends Spring's RedisCacheManager. Overrides one method:

@Override                                                                                                                                                                         
protected RedisCache createRedisCache(String name, RedisCacheConfiguration cacheConfig) {                                                                                         
return new ReadWriteRedisCache(name, defaultCacheWriter,                                                                                                                      
readRedisOperations, writeRedisOperations,                                                                                                                            
cacheConfig != null ? cacheConfig : config);                                                                                                                          
}

Every time Spring needs a new cache (e.g., the first @Cacheable call for a given cache name), this method runs. It produces a ReadWriteRedisCache instead of the default          
RedisCache, injecting both the read and write templates.

3.3 ReadWriteRedisCache — The Actual Split

File: config/ReadWriteRedisCache.java

Extends RedisCache. Overrides the four core cache operations:                                                                                                                     
┌───────────────────────────────┬────────────────────┬──────────────────────┬─────────┐                                                                                           
│            Method             │     Operation      │         Uses         │ Target  │                                                                                           
├───────────────────────────────┼────────────────────┼──────────────────────┼─────────┤                                                                                           
│ get(Object key)               │ Cache lookup       │ readRedisOperations  │ Replica │                                                                                           
├───────────────────────────────┼────────────────────┼──────────────────────┼─────────┤                                                                                           
│ get(Object key, Class<T>)     │ Typed cache lookup │ readRedisOperations  │ Replica │                                                                                           
├───────────────────────────────┼────────────────────┼──────────────────────┼─────────┤                                                                                           
│ put(Object key, Object value) │ Cache write        │ writeRedisOperations │ Primary │                                                                                           
├───────────────────────────────┼────────────────────┼──────────────────────┼─────────┤                                                                                           
│ evict(Object key)             │ Cache delete       │ writeRedisOperations │ Primary │                                                                                           
└───────────────────────────────┴────────────────────┴──────────────────────┴─────────┘                                                                                           
The put method extracts the TTL from the cache configuration and applies it:

Duration ttlDuration = getCacheConfiguration().getTtlFunction().getTimeToLive(key, value);                                                                                        
if (ttlDuration.isZero() || ttlDuration.isNegative()) {                                                                                                                           
writeRedisOperations.opsForValue().set(cacheKey, value);                                                                                                                      
} else {                                                                                                                                                                          
writeRedisOperations.opsForValue().set(cacheKey, value, ttlDuration);                                                                                                         
}
                                                                                                                                                                                    
---                                                                                                                                                                               
4. Request Flow

Method annotated with @Cacheable                                                                                                                                                  
│                                                                                                                                                                               
▼                                                                                                                                                                               
Spring AOP proxy intercepts the call                                                                                                                                              
│                                                                                                                                                                               
▼                                                                                                                                                                               
CacheManager.getCache("foodi-advanced-search")                                                                                                                                    
│                                                                                                                                                                               
▼                                                                                                                                                                               
ReadWriteRedisCacheManager.createRedisCache()   ← creates or reuses cache                                                                                                         
│                                                                                                                                                                               
▼                                                                                                                                                                               
ReadWriteRedisCache.get(key)                    ← reads from REPLICA                                                                                                              
│                                                                                                                                                                               
├── Cache HIT  → return cached value, skip method                                                                                                                               
│                                                                                                                                                                               
└── Cache MISS → execute the actual method                                                                                                                                      
│                                                                                                                                                                         
▼                                                                                                                                                                         
ReadWriteRedisCache.put(key, result)      ← writes to PRIMARY                                                                                                               
│                                                                                                                                                                         
▼                                                                                                                                                                         
Return result to caller

For @CacheEvict, the flow calls ReadWriteRedisCache.evict(key) which deletes from the primary.
                                                                                                                                                                                    
---                                                                                                                                                                               
5. Two Cache Manager Tiers                                                                                                                                                        
   ┌────────────────────┬───────────────────────┬────────────────────────────┬────────────────────────────────────────────────────────┐                                              
   │      Manager       │       Bean Name       │         TTL Source         │                        Used By                         │                                              
   ├────────────────────┼───────────────────────┼────────────────────────────┼────────────────────────────────────────────────────────┤                                              
   │ Default (@Primary) │ cacheManager          │ expiryInSeconds            │ All @Cacheable without explicit cacheManager attribute │                                              
   ├────────────────────┼───────────────────────┼────────────────────────────┼────────────────────────────────────────────────────────┤                                              
   │ Long-lived         │ longLivedCacheManager │ expiryInDays (currently 1) │ Only FilterSupport.getFilterOptions()                  │                                              
   └────────────────────┴───────────────────────┴────────────────────────────┴────────────────────────────────────────────────────────┘                                              
   Usage in code:

// Uses @Primary cacheManager (short TTL)                                                                                                                                         
@Cacheable(value = REDIS_NAMESPACE, keyGenerator = "restaurantKeyGenerator", unless = "...")

// Uses longLivedCacheManager (long TTL)                                                                                                                                          
@Cacheable(value = REDIS_NAMESPACE, keyGenerator = "filterOptionKeyGenerator",                                                                                                    
cacheManager = "longLivedCacheManager")
                                                                                                                                                                                    
---                                                                                                                                                                               
6. Current Usages of @Cacheable

All use value = REDIS_NAMESPACE (cache name) with custom key generators:                                                                                                          
┌──────────────────────────────────┬──────────────────────────┬───────────────────────┬────────────────────────────┐                                                              
│             Location             │      Key Generator       │     Cache Manager     │      unless Condition      │                                                              
├──────────────────────────────────┼──────────────────────────┼───────────────────────┼────────────────────────────┤                                                              
│ AutoCompleteFacadeSupport        │ autoCompleteKeyGenerator │ Default               │ result is null or empty    │                                                              
├──────────────────────────────────┼──────────────────────────┼───────────────────────┼────────────────────────────┤                                                              
│ SearchFacadeSupport (restaurant) │ restaurantKeyGenerator   │ Default               │ result.isShopIdsEmpty()    │                                                              
├──────────────────────────────────┼──────────────────────────┼───────────────────────┼────────────────────────────┤                                                              
│ SearchFacadeSupport (menu)       │ menuKeyGenerator         │ Default               │ result.isShopIdsEmpty()    │                                                              
├──────────────────────────────────┼──────────────────────────┼───────────────────────┼────────────────────────────┤                                                              
│ SearchFacadeSupport (shop)       │ shopKeyGenerator         │ Default               │ result.isShopIdsEmpty()    │                                                              
├──────────────────────────────────┼──────────────────────────┼───────────────────────┼────────────────────────────┤                                                              
│ SearchFacadeSupport (product)    │ productKeyGenerator      │ Default               │ result.isShopIdsEmpty()    │                                                              
├──────────────────────────────────┼──────────────────────────┼───────────────────────┼────────────────────────────┤                                                              
│ SearchFacadeSupport (suggestion) │ suggestionKeyGenerator   │ Default               │ result.isSuggestionEmpty() │                                                              
├──────────────────────────────────┼──────────────────────────┼───────────────────────┼────────────────────────────┤                                                              
│ FilterSupport                    │ filterOptionKeyGenerator │ longLivedCacheManager │ none                       │                                                              
└──────────────────────────────────┴──────────────────────────┴───────────────────────┴────────────────────────────┘
  ---                                                                                                                                                                               
7. Key Design Points

- Transparent to callers: Any @Cacheable annotation works without knowing about the read/write split. The split is handled entirely at the infrastructure level.
- Replication lag: Since reads hit the replica, there's a small window after a write where the replica may not yet have the data. For this codebase this is acceptable — cache    
  misses simply re-execute the method.
- Dual client libraries: Lettuce (LettuceConnectionFactory) is used for Spring Cache/Template operations. Jedis (JedisPooled) beans exist for direct Redis usage outside of       
  Spring's cache abstraction.
- defaultCacheWriter passed to the parent RedisCacheManager constructor is required by Spring but is effectively unused — ReadWriteRedisCache bypasses it by using RedisTemplate  
  operations directly in its overridden methods.   