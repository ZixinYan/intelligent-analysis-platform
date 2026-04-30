package com.kuaishou.intelligentanalysisplatform.contract.spi;

import com.kuaishou.intelligentanalysisplatform.contract.schema.BaseNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;

public interface NodeExecutor<C extends BaseNodeConfigDTO> {
    String supportType();
    NodeResultDTO execute(NodeExecuteContextDTO context, C config);
    ValidationResultDTO validate(C config);
    NodeMetaDTO metadata();
}
