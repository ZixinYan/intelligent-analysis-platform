package com.kuaishou.intelligentanalysisplatform.application.ai;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 可扩展的 AI 服务客户端抽象。
 * 支持 OpenAI 兼容 API、Dify Chat/Workflow、Mock 等多种实现，
 * 通过 ai.provider.type 配置选择实现。
 */
public interface AiProviderClient {

    /**
     * 流式生成文本。每个 token 回调 onToken，完成后调 onComplete，出错时调 onError。
     */
    void streamCompletion(String systemPrompt, String userMessage,
                          Consumer<String> onToken,
                          Runnable onComplete,
                          Consumer<Throwable> onError);

    /**
     * 阻塞式生成文本，返回完整响应。
     */
    String completion(String systemPrompt, String userMessage);

    /**
     * 标识符，用于工厂选择或日志。
     */
    String providerType();

    /**
     * 带历史记录的流式生成。
     * history 格式：[{role, content}, ...]，直接映射到 OpenAI messages。
     * 默认实现忽略历史，退化为单轮调用（兼容 MockAiProviderClient）。
     */
    default void streamCompletionWithHistory(
            List<Map<String, String>> history,
            String userMessage,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError) {
        String systemFromHistory = history.stream()
                .filter(m -> "system".equals(m.get("role")))
                .map(m -> m.get("content"))
                .findFirst().orElse("");
        streamCompletion(systemFromHistory, userMessage, onToken, onComplete, onError);
    }

    /**
     * 带历史记录的同步生成。
     * 默认实现忽略历史，退化为单轮调用。
     */
    default String completionWithHistory(List<Map<String, String>> history, String userMessage) {
        String systemFromHistory = history.stream()
                .filter(m -> "system".equals(m.get("role")))
                .map(m -> m.get("content"))
                .findFirst().orElse("");
        return completion(systemFromHistory, userMessage);
    }
}
