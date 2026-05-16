package com.hub.cache;

/**
 * Redis 读取三态：未命中 / 空值短缓存命中 / 正常命中
 */
public record CacheResult<T>(boolean cacheMiss, boolean nullPlaceholder, T value) {

    public static <T> CacheResult<T> miss() {
        return new CacheResult<>(true, false, null);
    }

    public static <T> CacheResult<T> nullHit() {
        return new CacheResult<>(false, true, null);
    }

    public static <T> CacheResult<T> hit(T value) {
        return new CacheResult<>(false, false, value);
    }

    public boolean isMiss() {
        return cacheMiss;
    }

    public boolean isNullHit() {
        return nullPlaceholder;
    }
}
