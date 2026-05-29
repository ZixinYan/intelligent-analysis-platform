package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.kuaishou.intelligentanalysisplatform.domain.trigger.TriggerStatus;
import com.kuaishou.intelligentanalysisplatform.domain.trigger.TriggerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
