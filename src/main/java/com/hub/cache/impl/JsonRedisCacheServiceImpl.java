package com.hub.cache.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.cache.CacheResult;
import com.hub.cache.JsonRedisCacheService;
import com.hub.common.constant.CacheNullConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
public class JsonRedisCacheServiceImpl implements JsonRedisCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public JsonRedisCacheServiceImpl(
            StringRedisTemplate stringRedisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> CacheResult<T> get(String key, Class<T> clazz) {
        String raw = stringRedisTemplate.opsForValue().get(key);
        if (raw == null) {
            return CacheResult.miss();
        }
        if (CacheNullConstants.PLACEHOLDER.equals(raw)) {
            return CacheResult.nullHit();
        }
        try {
            return CacheResult.hit(objectMapper.readValue(raw, clazz));
        } catch (JsonProcessingException e) {
            log.warn("Redis 缓存反序列化失败，已删除 key={}, targetClass={}", key, clazz.getName(), e);
            stringRedisTemplate.delete(key);
            return CacheResult.miss();
        }
    }

    @Override
    public <T> CacheResult<T> get(String key, TypeReference<T> typeReference) {
        String raw = stringRedisTemplate.opsForValue().get(key);
        if (raw == null) {
            return CacheResult.miss();
        }
        if (CacheNullConstants.PLACEHOLDER.equals(raw)) {
            return CacheResult.nullHit();
        }
        try {
            return CacheResult.hit(objectMapper.readValue(raw, typeReference));
        } catch (JsonProcessingException e) {
            log.warn("Redis 缓存反序列化失败，已删除 key={}, targetType={}", key, typeReference.getType(), e);
            stringRedisTemplate.delete(key);
            return CacheResult.miss();
        }
    }

    @Override
    public void put(String key, Object value, Duration ttl) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(value);
        if (ttl != null && !ttl.isNegative() && !ttl.isZero()) {
            stringRedisTemplate.opsForValue().set(key, json, ttl);
        } else {
            stringRedisTemplate.opsForValue().set(key, json);
        }
    }

    @Override
    public void putNull(String key, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key, CacheNullConstants.PLACEHOLDER, ttl);
    }

    @Override
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    public void deleteByPattern(String pattern) {
        Set<String> keys = new HashSet<>();
        try (var cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(100).build())) {
            cursor.forEachRemaining(key -> keys.add(key));
        }
        if (!keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }
}
