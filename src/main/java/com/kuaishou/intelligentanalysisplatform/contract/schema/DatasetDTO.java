package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetDTO {
    private DatasetSchemaDTO schema;
    private List<Map<String, Object>> rows;
    private DatasetPageDTO page;
    private DatasetStatDTO stat;
    /** 产生此数据集的原始 SQL，下推时作为子查询使用 */
    private String sourceSql;
    /** 来源数据源 ID，下推时必须与下游算子一致 */
    private String sourceDatasourceId;
}
