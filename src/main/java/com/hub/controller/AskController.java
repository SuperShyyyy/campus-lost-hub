package com.hub.controller;

import com.hub.security.AuthContext;
import com.hub.security.RateLimitService;
import com.hub.service.ConsultantBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api/ask")
@RequiredArgsConstructor
@Validated
public class AskController {

    private final ConsultantBizService consultantBizService;
    private final RateLimitService rateLimitService;
    private final com.hub.domain.repository.RedisChatMemoryStore redisChatMemoryStore;

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(
            @RequestParam @NotBlank(message = "memoryId不能为空") @Size(max = 100) String memoryId,
            @RequestParam @NotBlank(message = "message不能为空") @Size(max = 500, message = "消息不能超过500字符") String message) {
        long userId = AuthContext.requireUserId();
        rateLimitService.checkChatSendRateLimit(userId);
        return consultantBizService.chat(memoryId, message)
                .timeout(Duration.ofSeconds(60))
                .onErrorResume(TimeoutException.class,
                        e -> Flux.just("[超时] AI 响应超时，请稍后重试"));
    }

    /**
     * 清理当前用户的 AI 对话记忆（热数据）。
     */
    @DeleteMapping("/memory")
    public Mono<String> clearMemory(
            @RequestParam @NotBlank @Size(max = 100) String memoryId) {
        long userId = AuthContext.requireUserId();
        redisChatMemoryStore.deleteMessages(memoryId);
        return Mono.just("记忆已清理");
    }
}
