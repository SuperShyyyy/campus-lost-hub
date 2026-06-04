package com.hub.service.impl;

import com.hub.client.ClipEmbeddingClient;
import com.hub.config.AliyunConfig;
import com.hub.service.ImageEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 图片向量：Java 负责 OSS 上传与 DB，Python CLIP 仅通过 URL 计算 embedding。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageEmbeddingServiceImpl implements ImageEmbeddingService {

    private final ClipEmbeddingClient clipEmbeddingClient;
    private final AliyunConfig aliyunConfig;

    @Override
    public float[] embedImage(byte[] imageBytes, String contentType) {
        String filename = guessFilename(contentType);
        String tempUrl = aliyunConfig.uploadTemporaryQueryImage(imageBytes, filename);
        try {
            return clipEmbeddingClient.embedImageUrl(tempUrl);
        } finally {
            try {
                aliyunConfig.deleteByFileUrl(tempUrl);
            } catch (Exception e) {
                log.warn("删除图搜临时图片失败 url={}", tempUrl, e);
            }
        }
    }

    @Override
    public float[] embedImageUrl(String imageUrl) {
        return clipEmbeddingClient.embedImageUrl(imageUrl);
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
