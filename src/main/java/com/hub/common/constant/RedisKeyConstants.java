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

    // ========== 用户鉴权 ==========

    /**
     * 用户封禁标记（值 "1"，TTL 与 JWT 过期时间一致）。
     * 管理员封禁用户时写入，Filter 每次请求校验。
     */
    public static String userBan(Long userId) {
        return PREFIX + "user:ban:" + userId;
    }

    /**
     * Token 版本号（INCR 递增，持久化不设 TTL）。
     * 登出/改密时 +1，校验时 JWT 内 ver 必须与此值一致。
     */
    public static String userTokenVersion(Long userId) {
        return PREFIX + "user:tokenVersion:" + userId;
    }

    /**
     * 当前活跃会话 ID（单设备登录控制）。
     * 登录时写入新 sessionId，踢掉旧设备；TTL 与 JWT 过期时间一致。
     */
    public static String userSession(Long userId) {
        return PREFIX + "user:session:" + userId;
    }

    // ========== 请求限流 ==========

    /**
     * 文本搜索滑动窗口计数（ZSET），60s 内最多 10 次
     */
    public static String textSearchRateLimit(Long userId) {
        return PREFIX + "rate:textSearch:" + userId;
    }

    /**
     * 图片搜索滑动窗口计数（ZSET），60s 内最多 5 次
     */
    public static String imageSearchRateLimit(Long userId) {
        return PREFIX + "rate:imageSearch:" + userId;
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