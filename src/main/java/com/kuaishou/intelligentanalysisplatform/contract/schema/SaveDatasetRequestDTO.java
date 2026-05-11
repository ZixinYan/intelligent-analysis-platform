package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveDatasetRequestDTO {
    /** 数据集名称，不能为空 */
    private String name;
    /** 来源描述（自由文本） */
    private String description;
    /** 直接传入要保存的 dataset */
    private DatasetDTO dataset;
    /** 来源工作流 ID（可选，用于追溯） */
    private String sourceWorkflowId;
    /** 来源节点 ID（可选） */
    private String sourceNodeId;
}
