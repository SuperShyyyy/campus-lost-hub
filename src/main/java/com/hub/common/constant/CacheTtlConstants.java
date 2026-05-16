package com.hub.common.constant;

import java.time.Duration;

/**
 * 缓存 TTL：正常命中与空值短缓存（防穿透）
 */
public final class CacheTtlConstants {

    private CacheTtlConstants() {
    }

    /** 常规 GET 缓存 */
    public static final Duration DEFAULT_POSITIVE = Duration.ofMinutes(5);

    /** 数据库无数据时的短 TTL */
    public static final Duration NULL_ENTRY = Duration.ofSeconds(30);

    /** 列表类可略短，降低与写操作不一致窗口 */
    public static final Duration LIST_POSITIVE = Duration.ofMinutes(2);
}
