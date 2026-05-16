package com.hub.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;

public interface JsonRedisCacheService {

    <T> CacheResult<T> get(String key, Class<T> clazz);

    <T> CacheResult<T> get(String key, TypeReference<T> typeReference);

    void put(String key, Object value, Duration ttl) throws JsonProcessingException;

    void putNull(String key, Duration ttl);

    void delete(String key);

    void deleteByPattern(String pattern);
}
