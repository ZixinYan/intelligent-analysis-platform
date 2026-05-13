package com.kuaishou.intelligentanalysisplatform.application.ai.impl;

import java.util.function.Consumer;

import com.kuaishou.intelligentanalysisplatform.application.ai.AiProviderClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock 实现，用于开发/测试。ai.provider.type=mock 时生效。
 */
@Component
@ConditionalOnProperty(name = "ai.provider.type", havingValue = "mock")
public class MockAiProviderClient implements AiProviderClient {

    private static final String MOCK_SQL = "SELECT city, COUNT(*) AS user_count FROM users GROUP BY city ORDER BY user_count DESC LIMIT 10";

    @Override
    public void streamCompletion(String systemPrompt, String userMessage,
                                 Consumer<String> onToken, Runnable onComplete, Consumer<Throwable> onError) {
        String[] words = MOCK_SQL.split("(?<=\\s)|(?=\\s)");
        Thread t = new Thread(() -> {
            try {
                for (String word : words) {
                    onToken.accept(word);
                    Thread.sleep(30);
                }
                onComplete.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                onError.accept(e);
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @Override
    public String completion(String systemPrompt, String userMessage) {
        return """
            {
              "chartType": "BAR",
              "confidence": 0.85,
              "reason": "1 个维度字段 + 1 个指标字段，适合用柱状图对比",
              "fieldMapping": {"x": "city", "y": "user_count"}
            }
            """;
    }

    @Override
    public String providerType() {
        return "mock";
    }
}
