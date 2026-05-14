package com.kuaishou.intelligentanalysisplatform.application.trigger.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.application.WorkflowApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.trigger.TriggerApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncSubmitResponseDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.CreateTriggerRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.TriggerDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.AsyncExecutionService;
import com.kuaishou.intelligentanalysisplatform.domain.trigger.TriggerStatus;
import com.kuaishou.intelligentanalysisplatform.domain.trigger.TriggerType;
import com.kuaishou.intelligentanalysisplatform.domain.trigger.WorkflowTrigger;
import com.kuaishou.intelligentanalysisplatform.domain.trigger.WorkflowTriggerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTriggerApplicationService implements TriggerApplicationService {

    private final WorkflowTriggerRepository triggerRepository;
    private final WorkflowApplicationService workflowApplicationService;
    private final AsyncExecutionService asyncExecutionService;
    private final ObjectMapper objectMapper;

    @Override
    public TriggerDTO createTrigger(String workflowId, String tenantId, CreateTriggerRequestDTO request) {
        validateWorkflowExists(workflowId, tenantId);

        long now = System.currentTimeMillis();
        WorkflowTrigger.WorkflowTriggerBuilder builder = WorkflowTrigger.builder()
                .id("trg-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .workflowId(workflowId)
                .tenantId(tenantId)
                .triggerType(request.getTriggerType())
                .triggerStatus(TriggerStatus.ACTIVE)
                .defaultInputs(serializeInputs(request.getDefaultInputs()))
                .createdAt(now)
                .updatedAt(now);

        if (request.getTriggerType() == TriggerType.SCHEDULE) {
            if (request.getCronExpr() == null || request.getCronExpr().isBlank()) {
                throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "cronExpr is required for SCHEDULE trigger");
            }
            builder.cronExpr(request.getCronExpr());
        } else if (request.getTriggerType() == TriggerType.WEBHOOK) {
            builder.webhookToken(UUID.randomUUID().toString().replace("-", ""));
            if (request.getSecretKey() != null && !request.getSecretKey().isBlank()) {
                builder.secretKey(request.getSecretKey());
            }
        }

        WorkflowTrigger trigger = builder.build();
        if (trigger.getTriggerType() == TriggerType.SCHEDULE) {
            trigger.setNextFireAt(trigger.calculateNextFireAt());
        }

        triggerRepository.save(trigger);
        return toDTO(trigger);
    }

    @Override
    public List<TriggerDTO> listTriggers(String workflowId, String tenantId) {
        return triggerRepository.findByWorkflowId(workflowId).stream()
                .filter(t -> tenantId.equals(t.getTenantId()) && t.getTriggerStatus() != TriggerStatus.DELETED)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public TriggerDTO updateStatus(String triggerId, TriggerStatus status, String tenantId) {
        WorkflowTrigger trigger = findTrigger(triggerId, tenantId);
        triggerRepository.updateStatus(triggerId, status, System.currentTimeMillis());
        trigger.setTriggerStatus(status);
        return toDTO(trigger);
    }

    @Override
    public void deleteTrigger(String triggerId, String tenantId) {
        findTrigger(triggerId, tenantId);
        triggerRepository.updateStatus(triggerId, TriggerStatus.DELETED, System.currentTimeMillis());
    }

    @Override
    public AsyncSubmitResponseDTO fireTrigger(String triggerId, String tenantId) {
        WorkflowTrigger trigger = findTrigger(triggerId, tenantId);
        return submitWorkflow(trigger);
    }

    AsyncSubmitResponseDTO submitWorkflow(WorkflowTrigger trigger) {
        RequestContextDTO context = RequestContextDTO.builder()
                .tenantId(trigger.getTenantId())
                .userId("system:trigger")
                .requestId(UUID.randomUUID().toString())
                .build();

        WorkflowDefinitionDTO definition = workflowApplicationService.getById(trigger.getWorkflowId(), context);
        Map<String, Object> inputs = parseInputs(trigger.getDefaultInputs());

        WorkflowRunRequestDTO request = WorkflowRunRequestDTO.builder()
                .workflowId(trigger.getWorkflowId())
                .nodes(definition.getNodes())
                .edges(definition.getEdges())
                .inputs(inputs)
                .context(context)
                .build();

        return asyncExecutionService.submitWorkflow(request);
    }

    private WorkflowTrigger findTrigger(String triggerId, String tenantId) {
        WorkflowTrigger trigger = triggerRepository.findById(triggerId)
                .orElseThrow(() -> new BaseBusinessException(ErrorCode.TRIGGER_NOT_FOUND, "Trigger not found: " + triggerId));
        if (!tenantId.equals(trigger.getTenantId())) {
            throw new BaseBusinessException(ErrorCode.TRIGGER_NOT_FOUND, "Trigger not found: " + triggerId);
        }
        if (trigger.getTriggerStatus() == TriggerStatus.DELETED) {
            throw new BaseBusinessException(ErrorCode.TRIGGER_NOT_FOUND, "Trigger not found: " + triggerId);
        }
        return trigger;
    }

    private void validateWorkflowExists(String workflowId, String tenantId) {
        RequestContextDTO ctx = RequestContextDTO.builder()
                .tenantId(tenantId).userId("system").requestId(UUID.randomUUID().toString()).build();
        workflowApplicationService.getById(workflowId, ctx);
    }

    private String serializeInputs(Map<String, Object> inputs) {
        if (inputs == null || inputs.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(inputs);
        } catch (Exception e) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "Invalid defaultInputs: " + e.getMessage());
        }
    }

    Map<String, Object> parseInputs(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse defaultInputs: {}", e.getMessage());
            return Map.of();
        }
    }

    private TriggerDTO toDTO(WorkflowTrigger t) {
        return TriggerDTO.builder()
                .triggerId(t.getId())
                .workflowId(t.getWorkflowId())
                .triggerType(t.getTriggerType())
                .triggerStatus(t.getTriggerStatus())
                .cronExpr(t.getCronExpr())
                .nextFireAt(t.getNextFireAt())
                .webhookToken(t.getWebhookToken())
                .webhookUrl(t.getWebhookToken() != null ? t.webhookPath() : null)
                .defaultInputs(t.getDefaultInputs())
                .lastFireAt(t.getLastFireAt())
                .lastRunId(t.getLastRunId())
                .lastStatus(t.getLastStatus())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
