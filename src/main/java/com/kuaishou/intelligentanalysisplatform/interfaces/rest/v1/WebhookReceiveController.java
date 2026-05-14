package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.application.WorkflowApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.trigger.impl.WebhookVerifier;
import com.kuaishou.intelligentanalysisplatform.common.response.ApiResponse;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.AsyncExecutionService;
import com.kuaishou.intelligentanalysisplatform.domain.trigger.TriggerStatus;
import com.kuaishou.intelligentanalysisplatform.domain.trigger.WorkflowTrigger;
import com.kuaishou.intelligentanalysisplatform.domain.trigger.WorkflowTriggerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequiredArgsConstructor
public class WebhookReceiveController {

    private final WorkflowTriggerRepository triggerRepository;
    private final WebhookVerifier webhookVerifier;
    private final WorkflowApplicationService workflowApplicationService;
    private final AsyncExecutionService asyncExecutionService;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/webhook/{token}", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<ApiResponse<String>> receive(
            @PathVariable String token,
            @RequestBody(required = false) String rawBody,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature) {

        // 1. 查找触发器
        WorkflowTrigger trigger = triggerRepository.findByWebhookToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook not found"));

        if (trigger.getTriggerStatus() != TriggerStatus.ACTIVE) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(ApiResponse.failure("TRIGGER_INACTIVE", "Webhook trigger is not active", null, null));
        }

        // 2. 签名校验（可选，secretKey 非空时必须校验）
        if (trigger.getSecretKey() != null && !trigger.getSecretKey().isBlank()) {
            webhookVerifier.verify(trigger.getSecretKey(), rawBody, signature);
        }

        // 3. 解析 body 并与 defaultInputs 合并（webhook 输入优先级更高）
        Map<String, Object> inputs = new HashMap<>(parseJson(trigger.getDefaultInputs()));
        inputs.putAll(parseJson(rawBody));

        // 4. 加载工作流定义并触发
        RequestContextDTO context = RequestContextDTO.builder()
                .tenantId(trigger.getTenantId())
                .userId("system:webhook")
                .requestId(UUID.randomUUID().toString())
                .build();

        WorkflowDefinitionDTO definition = workflowApplicationService.getById(trigger.getWorkflowId(), context);

        WorkflowRunRequestDTO request = WorkflowRunRequestDTO.builder()
                .workflowId(trigger.getWorkflowId())
                .nodes(definition.getNodes())
                .edges(definition.getEdges())
                .inputs(inputs)
                .context(context)
                .build();

        String taskId = asyncExecutionService.submitWorkflow(request).getTaskId();

        log.info("Webhook trigger fired: token={} workflowId={} taskId={}",
                token, trigger.getWorkflowId(), taskId);

        return ResponseEntity.ok(ApiResponse.success(taskId));
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.debug("Failed to parse JSON body/inputs, ignoring: {}", e.getMessage());
            return Map.of();
        }
    }
}
