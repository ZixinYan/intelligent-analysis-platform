package com.kuaishou.intelligentanalysisplatform.application.ai.impl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kuaishou.intelligentanalysisplatform.application.ai.AiProviderClient;
import com.kuaishou.intelligentanalysisplatform.config.AiProviderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容 API 客户端实现。
 * 支持所有兼容 OpenAI /chat/completions 接口的服务（包括内部代理）。
 */
@Component
@ConditionalOnProperty(name = "ai.provider.type", havingValue = "openai", matchIfMissing = true)
public class OpenAiProviderClient implements AiProviderClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProviderClient.class);
    private static final String DATA_PREFIX = "data: ";
    private static final String DONE_SIGNAL = "[DONE]";

    private final AiProviderProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ExecutorService streamExecutor;

    public OpenAiProviderClient(AiProviderProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.streamExecutor = Executors.newFixedThreadPool(props.getStreamThreads(), r -> {
            Thread t = new Thread(r, "ai-stream-thread");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void streamCompletion(String systemPrompt, String userMessage,
                                 Consumer<String> onToken,
                                 Runnable onComplete,
                                 Consumer<Throwable> onError) {
        streamExecutor.submit(() -> {
            try {
                String requestBody = buildRequestBody(systemPrompt, userMessage, true);
                HttpRequest request = buildHttpRequest(requestBody);
                HttpResponse<java.io.InputStream> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    String errorBody = new String(response.body().readAllBytes());
                    onError.accept(new RuntimeException("AI API error " + response.statusCode() + ": " + errorBody));
                    return;
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith(DATA_PREFIX)) continue;
                        String data = line.substring(DATA_PREFIX.length()).trim();
                        if (DONE_SIGNAL.equals(data)) break;
                        if (data.isEmpty()) continue;
                        try {
                            JsonNode chunk = objectMapper.readTree(data);
                            String token = extractDeltaContent(chunk);
                            if (token != null && !token.isEmpty()) {
                                onToken.accept(token);
                            }
                        } catch (Exception e) {
                            log.debug("skip malformed SSE chunk: {}", data);
                        }
                    }
                }
                onComplete.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                onError.accept(e);
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }

    @Override
    public String completion(String systemPrompt, String userMessage) {
        try {
            String requestBody = buildRequestBody(systemPrompt, userMessage, false);
            HttpRequest request = buildHttpRequest(requestBody);
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("AI API error " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            return root.path("choices").get(0).path("message").path("content").asText("");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("AI completion interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("AI completion failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String providerType() {
        return "openai";
    }

    private String buildRequestBody(String systemPrompt, String userMessage, boolean stream) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", props.getModel());
            body.put("stream", stream);
            body.put("max_tokens", props.getMaxTokens());
            ArrayNode messages = body.putArray("messages");
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.addObject().put("role", "system").put("content", systemPrompt);
            }
            messages.addObject().put("role", "user").put("content", userMessage);
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build AI request body", e);
        }
    }

    private HttpRequest buildHttpRequest(String body) {
        String url = props.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private String extractDeltaContent(JsonNode chunk) {
        try {
            return chunk.path("choices").get(0).path("delta").path("content").asText(null);
        } catch (Exception e) {
            return null;
        }
    }
}
