package com.hub.service.impl;

import com.hub.client.DashScopeImageEmbeddingClient;
import com.hub.config.AliyunConfig;
import com.hub.service.ImageEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 图片向量：Java 负责 OSS 上传与 DB，阿里云 qwen2.5-vl-embedding 通过 URL 计算 512 维 embedding。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageEmbeddingServiceImpl implements ImageEmbeddingService {

    private final DashScopeImageEmbeddingClient dashScopeClient;
    private final AliyunConfig aliyunConfig;

    /**
     * 图搜场景：用户上传图片 bytes → 上传 OSS 临时文件 → 调用多模态 embedding → 删除临时文件。
     */
    @Override
    public float[] embedImage(byte[] imageBytes, String contentType) {
        String filename = guessFilename(contentType);
        String tempUrl = aliyunConfig.uploadTemporaryQueryImage(imageBytes, filename);
        try {
            return dashScopeClient.embedImageUrl(tempUrl);
        } finally {
            try {
                aliyunConfig.deleteByFileUrl(tempUrl);
            } catch (Exception e) {
                log.warn("删除图搜临时图片失败 url={}", tempUrl, e);
            }
        }
    }

    /**
     * 物品发布场景：已有永久 OSS URL，直接调用多模态 embedding。
     */
    @Override
    public float[] embedImageUrl(String imageUrl) {
        return dashScopeClient.embedImageUrl(imageUrl);
    }

    private String guessFilename(String contentType) {
        if (contentType == null) {
            return "query.jpg";
        }
        return switch (contentType.toLowerCase()) {
            case "image/png" -> "query.png";
            case "image/webp" -> "query.webp";
            case "image/gif" -> "query.gif";
            case "image/bmp" -> "query.bmp";
            default -> "query.jpg";
        };
    }
}
