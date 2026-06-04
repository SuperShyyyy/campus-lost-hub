package com.hub.service;

public interface ImageEmbeddingService {

    float[] embedImage(byte[] imageBytes, String contentType);

    float[] embedImageUrl(String imageUrl);
}
