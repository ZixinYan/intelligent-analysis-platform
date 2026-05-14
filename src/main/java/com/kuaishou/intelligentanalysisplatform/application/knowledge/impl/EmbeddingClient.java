package com.kuaishou.intelligentanalysisplatform.application.knowledge.impl;

import java.util.List;

/**
 * 文本向量化客户端接口。
 */
public interface EmbeddingClient {
    /** 单文本 → 向量 */
    float[] embed(String text);
    /** 批量向量化（减少 HTTP 往返） */
    List<float[]> embedBatch(List<String> texts);
}
