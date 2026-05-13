package com.kuaishou.intelligentanalysisplatform.application.ai;

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
}
