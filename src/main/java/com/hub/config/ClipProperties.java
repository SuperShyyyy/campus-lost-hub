package com.hub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "lost-hub.clip")
public class ClipProperties {

    /**
     * 是否启用 Python CLIP 图片向量服务。
     */
    private boolean enabled = false;

    /**
     * CLIP 服务根地址，例如 http://localhost:8000
     */
    private String baseUrl = "http://localhost:8000";

    /**
     * 图片向量维度（openai/clip-vit-base-patch32），需与 item.image_embedding 列一致。
     */
    private int dimension = 512;
}
