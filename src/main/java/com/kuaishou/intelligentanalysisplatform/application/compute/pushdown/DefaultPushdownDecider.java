package com.kuaishou.intelligentanalysisplatform.application.compute.pushdown;

import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeCapabilityDTO;
import org.springframework.stereotype.Component;

@Component
public class DefaultPushdownDecider implements PushdownDecider {

    static final int PUSHDOWN_THRESHOLD = 10_000;

    @Override
    public boolean canPushdown(NodeCapabilityDTO capability, DatasetDTO upstream, DatasourceType dsType) {
        // 1. 算子必须声明支持 SQL 下推
        if (capability == null
                || capability.getCapabilityConfig() == null
                || !Boolean.TRUE.equals(capability.getCapabilityConfig().getSqlPushdownSupported())) {
            return false;
        }
        // 2. 上游数据集必须携带 sourceSql 和 sourceDatasourceId（来自同一 DB 未经内存变换）
        if (upstream == null
                || upstream.getSourceSql() == null || upstream.getSourceSql().isBlank()
                || upstream.getSourceDatasourceId() == null || upstream.getSourceDatasourceId().isBlank()) {
            return false;
        }
        // 3. 行数超过阈值才值得下推
        if (upstream.getStat() == null
                || upstream.getStat().getRowCount() == null
                || upstream.getStat().getRowCount() <= PUSHDOWN_THRESHOLD) {
            return false;
        }
        // 4. 数据源类型必须已解析
        return dsType != null;
    }
}
