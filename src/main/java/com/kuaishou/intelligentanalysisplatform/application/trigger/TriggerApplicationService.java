package com.kuaishou.intelligentanalysisplatform.application.trigger;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncSubmitResponseDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.CreateTriggerRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.TriggerDTO;
import com.kuaishou.intelligentanalysisplatform.domain.trigger.TriggerStatus;

public interface TriggerApplicationService {
    TriggerDTO createTrigger(String workflowId, String tenantId, CreateTriggerRequestDTO request);
    List<TriggerDTO> listTriggers(String workflowId, String tenantId);
    TriggerDTO updateStatus(String triggerId, TriggerStatus status, String tenantId);
    void deleteTrigger(String triggerId, String tenantId);
    AsyncSubmitResponseDTO fireTrigger(String triggerId, String tenantId);
}
