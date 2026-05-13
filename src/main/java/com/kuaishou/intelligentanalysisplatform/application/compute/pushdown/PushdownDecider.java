package com.kuaishou.intelligentanalysisplatform.application.compute.pushdown;

import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeCapabilityDTO;

public interface PushdownDecider {
    /**
     * 判断能否将当前算子下推到数据库执行。
     *
     * @param capability 算子能力描述（含 sqlPushdownSupported 标志）
     * @param upstream   上游数据集（含 sourceSql 和 sourceDatasourceId 信息）
     * @param dsType     数据源类型（MYSQL / CLICKHOUSE / POSTGRES）
     * @return true = 走 SQL 下推路径
     */
    boolean canPushdown(NodeCapabilityDTO capability, DatasetDTO upstream, DatasourceType dsType);
}
