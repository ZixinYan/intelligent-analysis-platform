package com.kuaishou.intelligentanalysisplatform.application.knowledge.impl;

import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

@Component
public class SpringAiEmbeddingClient implements EmbeddingClient {

    private final EmbeddingModel embeddingModel;

    public SpringAiEmbeddingClient(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        return embeddingModel.embed(texts);
    }
}
