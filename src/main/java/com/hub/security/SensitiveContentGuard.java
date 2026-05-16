package com.hub.security;

import com.hub.common.constant.ChatConstants;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class SensitiveContentGuard {

    private static final List<Pattern> BLOCKED_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(password|passwd|pwd|token|access[_-]?token|refresh[_-]?token|api[_-]?key|secret|验证码|校验码|动态码)\\b"),
            Pattern.compile("(?<!\\d)1\\d{10}(?!\\d)"),
            Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)"),
            Pattern.compile("(?<!\\d)\\d{12,19}(?!\\d)")
    );

    public String normalizeAndValidate(String rawContent) {
        if (rawContent == null) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        String content = rawContent.trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        if (content.length() > ChatConstants.MESSAGE_CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("消息内容过长");
        }
        for (Pattern pattern : BLOCKED_PATTERNS) {
            if (pattern.matcher(content).find()) {
                throw new IllegalArgumentException("消息包含敏感信息，发送失败");
            }
        }
        return content;
    }
}
