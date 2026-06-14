package com.hub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 图片向量服务配置（阿里云 qwen2.5-vl-embedding 多模态模型）。
 * qwen2.5-vl-embedding 支持维度：2048 / 1024（默认）/ 768 / 512。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lost-hub.image-embedding")
public class ImageEmbeddingProperties {

    /**
     * 是否启用图片向量服务。
     */
    private boolean enabled = false;

    /**
     * 图片向量维度。qwen2.5-vl-embedding 支持 2048 / 1024 / 768 / 512。
     */
    private int dimension = 512;
}
