package com.hub.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hub.config.ClipProperties;
import com.hub.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClipEmbeddingClient {

    /**
     * CLIP 服务并发上限：GPU/CPU 推理串行时，限制同时 block 等待的 Java 线程数，
     * 避免高并发下积压大量线程打爆 Python CLIP 服务。
     */
    private static final Semaphore CLIP_SEMAPHORE = new Semaphore(4);

    private final ClipProperties clipProperties;
    private final WebClient.Builder webClientBuilder;

    /**
     * 调用 Python CLIP 服务 POST /embed，传入可公网访问的图片 URL。
     */
    public float[] embedImageUrl(String imageUrl) {
        ensureEnabled();
        if (!CLIP_SEMAPHORE.tryAcquire()) {
            throw new BizException(503, "CLIP 服务繁忙，请稍后重试");
        }
        try {
            return doEmbedImageUrl(imageUrl);
        } finally {
            CLIP_SEMAPHORE.release();
        }
    }

    private float[] doEmbedImageUrl(String imageUrl) {
        long start = System.nanoTime();
        try {
            ClipEmbedResponse response = webClient()
                    .post()
                    .uri("/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("image_url", imageUrl))
                    .retrieve()
                    .bodyToMono(ClipEmbedResponse.class)
                    .block();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.info("CLIP /embed 完成, elapsedMs={}, permits={}",
                    elapsedMs, CLIP_SEMAPHORE.availablePermits());
            if (response == null) {
                throw new IllegalStateException("CLIP 服务未返回向量");
            }
            return toVector(response);
        } catch (WebClientResponseException e) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.error("CLIP /embed 失败, elapsedMs={}", elapsedMs, e);
            throw new IllegalStateException("CLIP 服务调用失败: " + extractDetail(e), e);
        }
    }

    private WebClient webClient() {
        return webClientBuilder.baseUrl(clipProperties.getBaseUrl()).build();
    }

    private float[] toVector(ClipEmbedResponse response) {
        List<Double> embedding = response.embedding();
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalStateException("CLIP 返回空向量");
        }
        int expected = clipProperties.getDimension();
        int actualDim = response.embeddingDim() > 0 ? response.embeddingDim() : embedding.size();
        if (actualDim != expected || embedding.size() != expected) {
            throw new IllegalStateException(
                    "CLIP 向量维度不匹配，期望 " + expected + "，实际 embedding_dim="
                            + actualDim + " size=" + embedding.size());
        }
        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vector[i] = embedding.get(i).floatValue();
        }
        return vector;
    }

    private void ensureEnabled() {
        if (!clipProperties.isEnabled()) {
            throw new BizException(501, "图片向量服务未启用（lost-hub.clip.enabled=false）");
        }
    }

    private String extractDetail(WebClientResponseException e) {
        try {
            Map<?, ?> body = e.getResponseBodyAs(Map.class);
            if (body != null && body.get("detail") != null) {
                return String.valueOf(body.get("detail"));
            }
        } catch (Exception ignored) {
            // fall through
        }
        return e.getResponseBodyAsString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ClipEmbedResponse(
            @JsonProperty("embedding") List<Double> embedding,
            @JsonProperty("embedding_dim") int embeddingDim) {
    }
}
