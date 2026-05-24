package com.kuaishou.intelligentanalysisplatform.application.ai.rag;

import java.util.List;
import java.util.stream.Collectors;

import com.kuaishou.intelligentanalysisplatform.contract.schema.KnowledgeChunkDTO;
import org.springframework.stereotype.Component;

@Component
public class AiRagContextFormatter {

    private static final String SQL_CONTEXT_HEADER = "Additional business context (use only if relevant):";

    public String format(List<KnowledgeChunkDTO> chunks, AiRagScene scene) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        if (scene == AiRagScene.WORKFLOW_CONTEXT) {
            return chunks.stream()
                    .map(chunk -> "【" + safeTitle(chunk) + "】\n" + safeContent(chunk))
                    .collect(Collectors.joining("\n\n---\n\n"));
        }
        String ragContext = chunks.stream()
                .map(chunk -> safeTitle(chunk) + ": " + safeContent(chunk))
                .collect(Collectors.joining("\n"));
        return SQL_CONTEXT_HEADER + "\n" + ragContext;
    }

    private String safeTitle(KnowledgeChunkDTO chunk) {
        return chunk.getDocTitle() == null || chunk.getDocTitle().isBlank() ? "Untitled" : chunk.getDocTitle();
    }

    private String safeContent(KnowledgeChunkDTO chunk) {
        return chunk.getContent() == null ? "" : chunk.getContent();
    }
}
