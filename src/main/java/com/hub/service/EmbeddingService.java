package com.hub.service;

public interface EmbeddingService {

    float[] embedQuery(String text);

    float[] embedItem(Integer type, String title, String description, String location);
}
