package com.hub.common.constant;

/**
 * Redis 中空值占位（表示“已查库无此数据”，短 TTL）
 */
public final class CacheNullConstants {

    private CacheNullConstants() {
    }

    public static final String PLACEHOLDER = "\u0000CACHE_NULL\u0000";
}
