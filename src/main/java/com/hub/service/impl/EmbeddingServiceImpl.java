package com.hub.service.impl;

import com.hub.exception.BizException;
import com.hub.service.EmbeddingService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.Semaphore;

/**
 * 文本向量服务，调用 qwen text-embedding-v4。
 * 通过 Semaphore 控制并发，避免高并发下打爆外部 API 配额。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    /**
     * qwen embedding API 并发上限：与 CLIP 的 4 保持对称，避免外部 API 过载。
     */
    private static final Semaphore EMBEDDING_SEMAPHORE = new Semaphore(4);

    private final EmbeddingModel embeddingModel;

    @Override
    public float[] embedQuery(String text) {
        String content = text == null ? "" : text.trim();
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("搜索内容不能为空");
        }
        return embed("""
                场景：校园失物招领搜索
                任务：根据用户描述召回最相关的失物或招领物品
                用户描述：%s
                """.formatted(content));
    }

    @Override
    public float[] embedItemText(String title, String description) {
        String content = """
                场景：校园失物招领物品建档
                标题：%s
                描述：%s
                """.formatted(
                safeText(title),
                safeText(description)
        );
        return embed(content);
    }

    private float[] embed(String text) {
        if (!EMBEDDING_SEMAPHORE.tryAcquire()) {
            throw new BizException(503, "文本向量服务繁忙，请稍后重试");
        }
        long start = System.nanoTime();
        try {
            return embeddingModel.embed(text).content().vector();
        } catch (Exception e) {
            throw new IllegalStateException("生成向量失败", e);
        } finally {
            EMBEDDING_SEMAPHORE.release();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.debug("qwen embedding 完成, elapsedMs={}, permits={}",
                    elapsedMs, EMBEDDING_SEMAPHORE.availablePermits());
        }
    }

    private String safeText(String text) {
        return StringUtils.hasText(text) ? text.trim() : "未填写";
    }
}
