package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.ai.rag.AiRagContextFormatter;
import com.kuaishou.intelligentanalysisplatform.application.ai.rag.AiRagScene;
import com.kuaishou.intelligentanalysisplatform.application.knowledge.KnowledgeBaseService;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.NodeCategory;
import com.kuaishou.intelligentanalysisplatform.contract.schema.KnowledgeChunkDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.KnowledgeRetrievalNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeRetrievalNodeExecutor implements NodeExecutor<KnowledgeRetrievalNodeConfigDTO> {

    private static final int DEFAULT_TOP_K = 5;

    private final KnowledgeBaseService knowledgeBaseService;
    private final AiRagContextFormatter aiRagContextFormatter;

    public KnowledgeRetrievalNodeExecutor(KnowledgeBaseService knowledgeBaseService,
                                          AiRagContextFormatter aiRagContextFormatter) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.aiRagContextFormatter = aiRagContextFormatter;
    }

    @Override
    public String supportType() {
        return "knowledge_retrieval";
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO ctx, KnowledgeRetrievalNodeConfigDTO config) {
        String query = resolveQuery(ctx, config);
        String context = "";
        if (!query.isBlank()) {
            List<KnowledgeChunkDTO> chunks = knowledgeBaseService.retrieve(
                    config.getKnowledgeBaseId(), query, normalizeTopK(config.getTopK()));
            context = aiRagContextFormatter.format(chunks, AiRagScene.WORKFLOW_CONTEXT);
        }

        String outputVar = config.getOutputVariable() != null
                ? config.getOutputVariable()
                : "retrieved_context";

        return NodeResultDTO.builder()
                .nodeId(ctx.getNodeId())
                .nodeType(supportType())
                .status(ExecutionStatus.SUCCEEDED)
                .result(StandardResultDTO.builder()
                        .variables(Map.of(outputVar, context))
                        .build())
                .build();
    }

    @Override
    public ValidationResultDTO validate(KnowledgeRetrievalNodeConfigDTO config) {
        if (config == null || config.getKnowledgeBaseId() == null || config.getKnowledgeBaseId().isBlank()) {
            return ValidationResultDTO.builder().valid(false).errorMessage("knowledgeBaseId is required").build();
        }
        if ((config.getQueryVariable() == null || config.getQueryVariable().isBlank())
                && (config.getQueryLiteral() == null || config.getQueryLiteral().isBlank())) {
            return ValidationResultDTO.builder()
                    .valid(false)
                    .errorMessage("either queryVariable or queryLiteral must be set")
                    .build();
        }
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public NodeMetaDTO metadata() {
        return NodeMetaDTO.builder()
                .nodeType(supportType())
                .displayName("知识库检索")
                .category(NodeCategory.QUERY)
                .description("在知识库中进行语义向量检索，将结果注入下游节点 Prompt")
                .singleton(false)
                .startNode(false)
                .endNode(false)
                .build();
    }

    private String resolveQuery(NodeExecuteContextDTO ctx, KnowledgeRetrievalNodeConfigDTO config) {
        if (config.getQueryLiteral() != null && !config.getQueryLiteral().isBlank()) {
            return config.getQueryLiteral();
        }
        String varRef = config.getQueryVariable();
        if (varRef == null || varRef.isBlank()) {
            return "";
        }
        if (varRef.startsWith("$.")) {
            String varName = varRef.substring(2);
            if (ctx.getUpstreamResults() != null) {
                for (StandardResultDTO result : ctx.getUpstreamResults().values()) {
                    if (result.getVariables() != null && result.getVariables().containsKey(varName)) {
                        Object val = result.getVariables().get(varName);
                        return val != null ? val.toString() : "";
                    }
                }
            }
            return "";
        }
        String[] parts = varRef.split("\\.", 3);
        if (parts.length == 3 && "variables".equals(parts[1]) && ctx.getUpstreamResults() != null) {
            StandardResultDTO result = ctx.getUpstreamResults().get(parts[0]);
            if (result != null && result.getVariables() != null) {
                Object val = result.getVariables().get(parts[2]);
                return val != null ? val.toString() : "";
            }
        }
        return "";
    }

    private int normalizeTopK(Integer topK) {
        return topK == null || topK <= 0 ? DEFAULT_TOP_K : topK;
    }
}
