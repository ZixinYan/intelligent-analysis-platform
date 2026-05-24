package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration;

import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.ConversationalStreamExecutor;

/**
 * AI 流式输出的回调接口（传输无关）。
 *
 * <p>orchestration 层通过此接口将 token 流推送给调用方，
 * 调用方（Controller）负责将其桥接到具体的传输协议（SSE、WebSocket 等）。
 */
public interface AiStreamOutputHandler extends ConversationalStreamExecutor.StreamHandler {
}
