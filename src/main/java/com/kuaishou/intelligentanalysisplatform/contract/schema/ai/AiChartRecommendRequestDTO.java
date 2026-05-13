package com.kuaishou.intelligentanalysisplatform.contract.schema.ai;

import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AiChartRecommendRequestDTO {
    @NotEmpty
    private List<FieldSchemaDTO> fields;
    /** 可选：上下文描述，辅助 LLM 推荐 */
    private String context;
}
