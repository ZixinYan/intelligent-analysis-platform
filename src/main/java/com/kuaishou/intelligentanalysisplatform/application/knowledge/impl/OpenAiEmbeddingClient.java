package com.kuaishou.intelligentanalysisplatform.application.knowledge.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kuaishou.intelligentanalysisplatform.config.AiProviderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 调用 OpenAI /embeddings 接口获取文本向量。
 * 复用 ai.provider 的 baseUrl 和 apiKey，model 通过 ai.provider.embedding-model 配置。
 */
@Component
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingClient.class);

    private final AiProviderProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiEmbeddingClient(AiProviderProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        try {
            String requestBody = buildRequestBody(texts);
            HttpRequest request = buildHttpRequest(requestBody);
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Embedding API error " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.path("data");
            List<float[]> results = new ArrayList<>(texts.size());
            for (JsonNode item : data) {
                JsonNode embeddingNode = item.path("embedding");
                float[] vec = new float[embeddingNode.size()];
                for (int i = 0; i < vec.length; i++) {
                    vec[i] = (float) embeddingNode.get(i).asDouble();
                }
                results.add(vec);
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Embedding interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Embedding failed: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(List<String> texts) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", props.getEmbeddingModel());
            ArrayNode input = body.putArray("input");
            texts.forEach(input::add);
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build embedding request", e);
        }
    }

    private HttpRequest buildHttpRequest(String body) {
        String url = props.getBaseUrl().replaceAll("/+$", "") + "/embeddings";
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }
}
