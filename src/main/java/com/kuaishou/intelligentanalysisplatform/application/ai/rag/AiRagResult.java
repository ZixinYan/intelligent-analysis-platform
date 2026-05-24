package com.kuaishou.intelligentanalysisplatform.application.ai.rag;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.schema.KnowledgeChunkDTO;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiRagResult {

    private final List<KnowledgeChunkDTO> chunks;
    private final String formattedContext;
    private final boolean degraded;

    public boolean hasResults() {
        return chunks != null && !chunks.isEmpty() && formattedContext != null && !formattedContext.isBlank();
    }

    public static AiRagResult empty() {
        return AiRagResult.builder()
                .chunks(List.of())
                .formattedContext("")
                .degraded(false)
                .build();
    }

    public static AiRagResult degraded() {
        return AiRagResult.builder()
                .chunks(List.of())
                .formattedContext("")
                .degraded(true)
                .build();
    }
}
