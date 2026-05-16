package com.hub.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ApiConfig {
    @Value("${langchain4j.open-ai.chat-model.api-key}")
    String apiKey;
    @Value("${langchain4j.open-ai.chat-model.base-url}")
    String baseUrl;
    @Value("${langchain4j.open-ai.chat-model.model-name}")
    String modelName;
    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return modelName;
    }
}