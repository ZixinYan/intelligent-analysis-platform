package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.kuaishou.intelligentanalysisplatform.domain.trigger.TriggerStatus;
import com.kuaishou.intelligentanalysisplatform.domain.trigger.TriggerType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TriggerDTO {
    private String        triggerId;
    private String        workflowId;
    private TriggerType   triggerType;
    private TriggerStatus triggerStatus;
    private String        cronExpr;
    private Long          nextFireAt;
    private String        webhookToken;
    private String        webhookUrl;
    private String        defaultInputs;
    private Long          lastFireAt;
    private String        lastRunId;
    private String        lastStatus;
    private Long          createdAt;
    private Long          updatedAt;
}
