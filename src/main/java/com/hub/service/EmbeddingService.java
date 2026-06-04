package com.hub.service;

public interface EmbeddingService {

    float[] embedQuery(String text);

    float[] embedItemText(String title, String description);
}
