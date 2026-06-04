package com.hub.service.impl;

import com.hub.service.EmbeddingService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

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
        try {
            return embeddingModel.embed(text).content().vector();
        } catch (Exception e) {
            throw new IllegalStateException("生成向量失败", e);
        }
    }

    private String safeText(String text) {
        return StringUtils.hasText(text) ? text.trim() : "未填写";
    }
}
