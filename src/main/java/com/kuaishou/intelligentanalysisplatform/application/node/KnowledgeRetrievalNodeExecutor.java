package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

/**
 * 知识库检索节点执行器。
 * 将 query 向量化后在 ES 中做 kNN 检索，结果拼成纯文本写入输出变量，
 * 供下游 SQL 生成节点或 LLM 节点引用。
 */
@Component
public class KnowledgeRetrievalNodeExecutor implements NodeExecutor<KnowledgeRetrievalNodeConfigDTO> {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeRetrievalNodeExecutor(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    public String supportType() {
        return "knowledge_retrieval";
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO ctx, KnowledgeRetrievalNodeConfigDTO config) {
        String query = resolveQuery(ctx, config);

        List<KnowledgeChunkDTO> chunks = knowledgeBaseService.retrieve(
                config.getKnowledgeBaseId(), query, config.getTopK());

        String context = chunks.stream()
                .map(c -> "【" + c.getDocTitle() + "】\n" + c.getContent())
                .collect(Collectors.joining("\n\n---\n\n"));

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

    /**
     * 解析查询文本：优先从上游节点变量取，其次使用 literal。
     * queryVariable 格式为 "{sourceNodeId}.variables.{variableName}" 或简写 "$.{variableName}"（取任意上游 variables）。
     */
    private String resolveQuery(NodeExecuteContextDTO ctx, KnowledgeRetrievalNodeConfigDTO config) {
        if (config.getQueryLiteral() != null && !config.getQueryLiteral().isBlank()) {
            return config.getQueryLiteral();
        }
        String varRef = config.getQueryVariable();
        if (varRef == null || varRef.isBlank()) {
            return "";
        }
        // 简写：$.variableName → 在所有上游 variables 中查找
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
        // 完整引用：{nodeId}.variables.{varName}
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
}
