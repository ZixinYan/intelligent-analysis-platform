package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KnowledgeRetrievalNodeConfigDTO extends BaseNodeConfigDTO {
    /** 知识库 ID */
    private String knowledgeBaseId;
    /** 从上游变量取查询文本，如 "$.user_question"，与 queryLiteral 互斥 */
    private String queryVariable;
    /** 直接写死的查询文本 */
    private String queryLiteral;
    /** 返回的最大片段数 */
    private int topK = 5;
    /** 输出变量名，下游节点通过此名称引用 */
    private String outputVariable = "retrieved_context";
}
