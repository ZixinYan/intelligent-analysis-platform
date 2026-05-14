package com.kuaishou.intelligentanalysisplatform.contract.schema.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiSqlRequestDTO {
    @NotBlank
    private String datasourceId;
    @NotBlank
    private String tableName;
    @NotBlank
    private String description;
    /** 多轮对话 ID，首次调用不传，后续调用携带上一次返回的值 */
    private String conversationId;
    /** 可选：知识库 ID，非空时启用 RAG 增强 */
    private String knowledgeBaseId;
}
