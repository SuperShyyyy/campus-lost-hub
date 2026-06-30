package com.hub.config;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.ObjectMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

@Component
public class AliyunConfig {

    @Value("${lost-hub.aliyun.access-key-id}")
    private String accessKeyId;

    @Value("${lost-hub.aliyun.access-key-secret}")
    private String accessKeySecret;

    @Value("${lost-hub.aliyun.end-point}")
    private String endPoint;

    @Value("${lost-hub.aliyun.region}")
    private String region;

    @Value("${lost-hub.aliyun.bucket-name}")
    private String bucketName;

    private volatile OSS ossClient;

    /**
     * 获取单例 OSS 客户端（懒初始化 + 双重检查锁），避免每次请求新建/销毁导致连接池耗尽。
     */
    private OSS getOssClient() {
        if (ossClient == null) {
            synchronized (this) {
                if (ossClient == null) {
                    validateConfig();
                    ClientBuilderConfiguration cfg = new ClientBuilderConfiguration();
                    cfg.setSignatureVersion(SignVersion.V4);
                    ossClient = OSSClientBuilder.create()
                            .endpoint(endPoint)
                            .credentialsProvider(new DefaultCredentialProvider(accessKeyId, accessKeySecret))
                            .clientConfiguration(cfg)
                            .region(region)
                            .build();
                }
            }
        }
        return ossClient;
    }

    public String uploadItemImage(byte[] content, String originalFileName) {
        String dir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        return uploadImage(content, originalFileName, dir);
    }

    /**
     * 图搜查询用临时图片，上传后交给 CLIP 服务通过 URL 算向量。
     */
    public String uploadTemporaryQueryImage(byte[] content, String originalFileName) {
        String dir = "clip-query/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        return uploadImage(content, originalFileName, dir);
    }

    /**
     * 上传用户头像到 avatar/ 目录。
     */
    public String uploadUserAvatar(byte[] content, String originalFileName) {
        return uploadImage(content, originalFileName, "avatar");
    }

    public void deleteByFileUrl(String fileUrl) {
        String objectName = extractObjectName(fileUrl);
        if (objectName == null) {
            return;
        }
        getOssClient().deleteObject(bucketName, objectName);
    }

    private String uploadImage(byte[] content, String originalFileName, String prefix) {
        String dir = prefix + "/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String extension = getExtension(originalFileName);
        String newFileName = UUID.randomUUID() + extension;
        String objectName = dir + "/" + newFileName;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        metadata.setContentType(resolveContentType(extension));
        getOssClient().putObject(bucketName, objectName, new ByteArrayInputStream(content), metadata);

        return buildFileUrl(objectName);
    }

    private void validateConfig() {
        if (!StringUtils.hasText(accessKeyId)
                || !StringUtils.hasText(accessKeySecret)
                || !StringUtils.hasText(endPoint)
                || !StringUtils.hasText(region)
                || !StringUtils.hasText(bucketName)) {
            throw new IllegalStateException("阿里云 OSS 配置不完整");
        }
    }

    /**
     * 允许上传的安全图片扩展名（白名单）。
     * 不允许 .svg（可嵌入 JS 脚本）、.html 等可执行类型。
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"
    );

    private String getExtension(String originalFileName) {
        if (!StringUtils.hasText(originalFileName) || !originalFileName.contains(".")) {
            return ".bin";
        }
        String ext = originalFileName.substring(originalFileName.lastIndexOf(".")).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            return ".bin";
        }
        return ext;
    }

    private String resolveContentType(String extension) {
        return switch (extension) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            case ".bmp" -> "image/bmp";
            default -> "application/octet-stream";
        };
    }

    private String buildFileUrl(String objectName) {
        String normalizedEndpoint = endPoint.trim();
        if (normalizedEndpoint.startsWith("http://")) {
            return "http://" + bucketName + "." + normalizedEndpoint.substring("http://".length()) + "/" + objectName;
        }
        if (normalizedEndpoint.startsWith("https://")) {
            return "https://" + bucketName + "." + normalizedEndpoint.substring("https://".length()) + "/" + objectName;
        }
        return "https://" + bucketName + "." + normalizedEndpoint + "/" + objectName;
    }

    private String extractObjectName(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return null;
        }
        String prefix = buildFileUrl("");
        if (fileUrl.startsWith(prefix)) {
            return fileUrl.substring(prefix.length());
        }
        int schemeIndex = fileUrl.indexOf("://");
        if (schemeIndex < 0) {
            return null;
        }
        int pathStart = fileUrl.indexOf('/', schemeIndex + 3);
        if (pathStart < 0 || pathStart + 1 >= fileUrl.length()) {
            return null;
        }
        return fileUrl.substring(pathStart + 1);
    }
}
