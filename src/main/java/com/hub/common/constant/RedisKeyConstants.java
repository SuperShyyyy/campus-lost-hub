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

    // ========== 封禁标记 ==========

    /**
     * 用户封禁标记（存在即表示已封禁，TTL 与 JWT 过期时间一致）
     */
    public static String banUser(Long userId) {
        return PREFIX + "ban:user:" + userId;
    }

    // ========== 请求限流 ==========

    /**
     * 文本搜索冷却标记，TTL 15s
     */
    public static String textEmbeddingRateLimit(Long userId) {
        return PREFIX + "rate:textEmbedding:" + userId;
    }

    /**
     * 图片搜索冷却标记，TTL 30s
     */
    public static String imageEmbeddingRateLimit(Long userId) {
        return PREFIX + "rate:imageEmbedding:" + userId;
    }

    /**
     * 物品发布冷却标记，TTL 10s
     */
    public static String itemCreateRateLimit(Long userId) {
        return PREFIX + "rate:itemCreate:" + userId;
    }

    /**
     * 聊天消息发送冷却标记，TTL 3s
     */
    public static String chatSendRateLimit(Long userId) {
        return PREFIX + "rate:chatSend:" + userId;
    }

    /**
     * 用户注册冷却标记，TTL 60s
     */
    public static String userRegisterRateLimit(String ip) {
        return PREFIX + "rate:userRegister:" + ip;
    }

    /**
     * 认领提交冷却标记，TTL 10s
     */
    public static String claimCreateRateLimit(Long userId) {
        return PREFIX + "rate:claimCreate:" + userId;
    }
}