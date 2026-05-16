package com.hub.security;

import com.hub.exception.ForbiddenException;
import com.hub.exception.UnauthorizedException;
import com.hub.service.ItemChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String CHAT_TOPIC_PREFIX = "/topic/chat.session.";

    private final JwtTokenProvider jwtTokenProvider;
    private final ItemChatService itemChatService;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            String auth = accessor.getFirstNativeHeader("Authorization");
            long userId = jwtTokenProvider.parseUserId(auth);
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
