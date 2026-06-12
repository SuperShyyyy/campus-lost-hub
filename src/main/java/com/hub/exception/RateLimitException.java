package com.hub.exception;

/**
 * 请求频率超限异常（HTTP 429 Too Many Requests）
 */
public class RateLimitException extends BizException {

    public RateLimitException(String message) {
        super(429, message);
    }
}
