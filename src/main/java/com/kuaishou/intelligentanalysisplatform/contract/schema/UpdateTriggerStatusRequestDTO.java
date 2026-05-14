package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.kuaishou.intelligentanalysisplatform.domain.trigger.TriggerStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateTriggerStatusRequestDTO {
    @NotNull
    private TriggerStatus status;
}
