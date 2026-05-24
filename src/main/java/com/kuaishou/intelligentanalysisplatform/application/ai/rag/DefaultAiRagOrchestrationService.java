package com.kuaishou.intelligentanalysisplatform.application.ai.rag;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.schema.KnowledgeChunkDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultAiRagOrchestrationService implements AiRagOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiRagOrchestrationService.class);
    private static final int DEFAULT_TOP_K = 5;

    private final AiRagRetrievalAdapter retrievalAdapter;
    private final AiRagContextFormatter contextFormatter;

    public DefaultAiRagOrchestrationService(AiRagRetrievalAdapter retrievalAdapter,
                                            AiRagContextFormatter contextFormatter) {
        this.retrievalAdapter = retrievalAdapter;
        this.contextFormatter = contextFormatter;
    }

    @Override
    public AiRagResult retrieve(AiRagQuery query) {
        if (query == null || isBlank(query.getKnowledgeBaseId()) || isBlank(query.getQueryText())) {
            return AiRagResult.empty();
        }
        try {
            List<KnowledgeChunkDTO> chunks = retrievalAdapter.retrieve(
                    query.getKnowledgeBaseId(),
                    query.getQueryText(),
                    normalizeTopK(query.getTopK()));
            if (chunks == null || chunks.isEmpty()) {
                return AiRagResult.empty();
            }
            return AiRagResult.builder()
                    .chunks(chunks)
                    .formattedContext(contextFormatter.format(chunks, normalizeScene(query.getScene())))
                    .degraded(false)
                    .build();
        } catch (Exception e) {
            log.warn("RAG retrieval failed for kbId={}, continuing without context", query.getKnowledgeBaseId(), e);
            return AiRagResult.degraded();
        }
    }

    private int normalizeTopK(Integer topK) {
        return topK == null || topK <= 0 ? DEFAULT_TOP_K : topK;
    }

    private AiRagScene normalizeScene(AiRagScene scene) {
        return scene == null ? AiRagScene.SQL_PROMPT : scene;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
