package com.hub.security;

import com.hub.common.constant.RedisKeyConstants;
import com.hub.exception.ForbiddenException;
import com.hub.exception.UnauthorizedException;
import com.hub.service.ItemChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * STOMP WebSocket 鉴权拦截器，校验逻辑与 HTTP Filter 一致：
 * 封禁 → Token 版本 → 会话（单设备） → 放行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String CHAT_TOPIC_PREFIX = "/topic/chat.session.";

    private final JwtTokenProvider jwtTokenProvider;
    private final ItemChatService itemChatService;
    private final StringRedisTemplate stringRedisTemplate;
    private final BanCheckService banCheckService;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            String auth = accessor.getFirstNativeHeader("Authorization");
            JwtTokenProvider.TokenInfo token = jwtTokenProvider.parseUserToken(auth);
            long userId = token.userId();

            // Step 1: 封禁检查
            banCheckService.checkNotBanned(userId);

            // Step 2: Token 版本校验（Redis 故障 → fail-open）
            try {
                String versionKey = RedisKeyConstants.userTokenVersion(userId);
                String redisVersion = stringRedisTemplate.opsForValue().get(versionKey);
                if (redisVersion != null && token.tokenVersion() != Long.parseLong(redisVersion)) {
                    throw new UnauthorizedException("令牌已失效");
                }
            } catch (UnauthorizedException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Redis 不可用，跳过 WS Token 版本校验，userId={}", userId, e);
            }

            // Step 3: 会话校验（单设备互踢）（Redis 故障 → fail-open）
            try {
                String sessionKey = RedisKeyConstants.userSession(userId);
                String activeSession = stringRedisTemplate.opsForValue().get(sessionKey);
                if (activeSession != null && !activeSession.equals(token.sessionId())) {
                    throw new UnauthorizedException("账号已在其他设备登录");
                }
            } catch (UnauthorizedException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Redis 不可用，跳过 WS 会话校验，userId={}", userId, e);
            }

            accessor.setUser(new StompPrincipal(userId));
            return message;
        }

        Principal principal = accessor.getUser();
        if (principal == null) {
            throw new UnauthorizedException("未登录或登录已失效");
        }

        if (StompCommand.SUBSCRIBE.equals(command)) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith(CHAT_TOPIC_PREFIX)) {
                long sessionId = parseSessionId(destination);
                long userId = Long.parseLong(principal.getName());
                if (!itemChatService.canAccessSession(userId, sessionId)) {
                    throw new ForbiddenException("无权订阅该会话");
                }
            }
        }

        return message;
    }

    private long parseSessionId(String destination) {
        String value = destination.substring(CHAT_TOPIC_PREFIX.length()).trim();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的会话订阅地址");
        }
    }
}
