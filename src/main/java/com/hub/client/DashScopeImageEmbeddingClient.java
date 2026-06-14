package com.hub.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hub.config.ImageEmbeddingProperties;
import com.hub.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * 阿里云 DashScope 多模态图片向量客户端。
 * 调用 qwen2.5-vl-embedding 模型，输入图片 URL，输出可配置维度的归一化向量。
 * <p>
 * API 文档：https://help.aliyun.com/zh/model-studio/multimodal-embedding-api-reference
 * qwen2.5-vl-embedding 默认维度 1024，支持 2048/1024/768/512。
 */
@Slf4j
@Component
public class DashScopeImageEmbeddingClient {

    private static final Semaphore EMBEDDING_SEMAPHORE = new Semaphore(4);

    private static final String DASHSCOPE_BASE = "https://dashscope.aliyuncs.com";
    private static final String EMBEDDING_PATH = "/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding";
    private static final String MODEL = "qwen2.5-vl-embedding";

    /** DashScope 多模态 embedding 调用超时（含 TLS 握手 + 响应等待）。 */
    private static final Duration EMBEDDING_TIMEOUT = Duration.ofSeconds(30);

    private final ImageEmbeddingProperties imageEmbeddingProperties;
    private final WebClient webClient;
    private final String apiKey;

    public DashScopeImageEmbeddingClient(ImageEmbeddingProperties imageEmbeddingProperties,
                                         WebClient.Builder webClientBuilder,
                                         @Value("${lost-hub.ai.api-key}") String apiKey) {
        this.imageEmbeddingProperties = imageEmbeddingProperties;
        this.apiKey = apiKey;
        this.webClient = webClientBuilder
                .baseUrl(DASHSCOPE_BASE)
                .build();
    }

    /**
     * 传入公开可访问的图片 URL，调用阿里云多模态 embedding，返回 512 维 float[]。
     */
    public float[] embedImageUrl(String imageUrl) {
        ensureEnabled();
        if (!EMBEDDING_SEMAPHORE.tryAcquire()) {
            throw new BizException(503, "图片向量服务繁忙，请稍后重试");
        }
        try {
            return doEmbedImageUrl(imageUrl);
        } finally {
            EMBEDDING_SEMAPHORE.release();
        }
    }

    private float[] doEmbedImageUrl(String imageUrl) {
        long start = System.nanoTime();
        try {
            // DashScope multimodal embedding API 使用 contents 格式（非 chat/messages 格式）。
            // qwen2.5-vl-embedding 始终输出融合向量，content 类型由 key 隐式决定。
            int dimension = imageEmbeddingProperties.getDimension();
            Map<String, Object> requestBody = Map.of(
                    "model", MODEL,
                    "input", Map.of(
                            "contents", List.of(
                                    Map.of("image", imageUrl)
                            )
                    ),
                    "parameters", Map.of("dimension", dimension)
            );

            MultimodalEmbedResponse response = webClient
                    .post()
                    .uri(EMBEDDING_PATH)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(MultimodalEmbedResponse.class)
                    .timeout(EMBEDDING_TIMEOUT)
                    .block();

            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.info("DashScope image embedding 完成, elapsedMs={}, permits={}",
                    elapsedMs, EMBEDDING_SEMAPHORE.availablePermits());

            if (response == null || response.output() == null || response.output().embeddings() == null
                    || response.output().embeddings().isEmpty()) {
                throw new IllegalStateException("DashScope 图片向量服务未返回向量");
            }

            List<Double> embedding = response.output().embeddings().get(0).embedding();
            if (embedding == null || embedding.isEmpty()) {
                throw new IllegalStateException("DashScope 返回空向量");
            }

            int expected = dimension;
            if (embedding.size() != expected) {
                throw new IllegalStateException(
                        "向量维度不匹配，期望 " + expected + "，实际 " + embedding.size());
            }

            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = embedding.get(i).floatValue();
            }
            return vector;

        } catch (WebClientResponseException e) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.error("DashScope image embedding 失败, elapsedMs={}", elapsedMs, e);
            throw new IllegalStateException("DashScope 图片向量服务调用失败: " + extractDetail(e), e);
        }
    }

    private void ensureEnabled() {
        if (!imageEmbeddingProperties.isEnabled()) {
            throw new BizException(501, "图片向量服务未启用（lost-hub.image-embedding.enabled=false）");
        }
    }

    private String extractDetail(WebClientResponseException e) {
        try {
            Map<?, ?> body = e.getResponseBodyAs(Map.class);
            if (body != null && body.get("message") != null) {
                return String.valueOf(body.get("message"));
            }
        } catch (Exception ignored) {
        }
        return e.getResponseBodyAsString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MultimodalEmbedResponse(
            @JsonProperty("output") EmbedOutput output) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbedOutput(
            @JsonProperty("embeddings") List<EmbedItem> embeddings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbedItem(
            @JsonProperty("embedding") List<Double> embedding,
            @JsonProperty("index") int index) {
    }
}
