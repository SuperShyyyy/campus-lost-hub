package com.hub.common.constant;

/**
 * Redis 缓存 key 前缀（与业务 key 隔离）
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {
    }

    public static final String PREFIX = "losthub:";

    public static String userMe(Long userId) {
        return PREFIX + "user:me:" + userId;
    }

    public static String adminClaims(int page, int size) {
        return PREFIX + "admin:claims:p:" + page + ":s:" + size;
    }

    public static String adminClaimsPattern() {
        return PREFIX + "admin:claims:*";
    }

    public static String itemList(Integer type, Integer status, int page, int size) {
        String t = type == null ? "all" : String.valueOf(type);
        String st = status == null ? "all" : String.valueOf(status);
        return PREFIX + "item:list:t:" + t + ":st:" + st + ":p:" + page + ":s:" + size;
    }

    public static String itemListPattern() {
        return PREFIX + "item:list:*";
    }

    public static String itemDetail(Long id) {
        return PREFIX + "item:detail:" + id;
    }

    public static String claimMy(Long userId) {
        return PREFIX + "claim:my:" + userId;
    }

    public static String claimDetail(Long id) {
        return PREFIX + "claim:detail:" + id;
    }
}
