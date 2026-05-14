package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.domain.trigger.TriggerType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateTriggerRequestDTO {
    @NotNull
    private TriggerType triggerType;
    // SCHEDULE
    private String cronExpr;
    // WEBHOOK
    private String secretKey;
    // 公共：合并到 WorkflowRunRequestDTO.inputs
    private Map<String, Object> defaultInputs;
}
