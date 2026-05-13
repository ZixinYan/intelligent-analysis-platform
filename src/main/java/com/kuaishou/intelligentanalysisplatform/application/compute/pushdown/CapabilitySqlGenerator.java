package com.kuaishou.intelligentanalysisplatform.application.compute.pushdown;

import com.kuaishou.intelligentanalysisplatform.contract.schema.BaseNodeConfigDTO;

public interface CapabilitySqlGenerator<C extends BaseNodeConfigDTO> {
    /**
     * 生成下推 SQL，将算子语义转化为 SQL，以 baseQuery 为子查询数据源。
     *
     * @param baseQuery 上游产生此数据集的 SQL（作为 FROM 子查询）
     * @param config    当前算子配置
     * @param dialect   目标数据库方言
     * @return 下推 SQL 字符串
     */
    String generate(String baseQuery, C config, DatasourceDialect dialect);

    /** 返回对应的算子能力码，与 ComputeCapabilityRegistry 中的 code 一致 */
    String capabilityCode();
}
