package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetReadNodeConfigDTO extends BaseNodeConfigDTO {
    /** 已保存数据集的 ID */
    private String datasetId;
    /** 读取行数限制（null = 全量） */
    private Integer rowLimit;
}
