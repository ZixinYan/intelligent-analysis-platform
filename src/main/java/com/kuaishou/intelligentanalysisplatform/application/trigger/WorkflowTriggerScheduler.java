package com.kuaishou.intelligentanalysisplatform.application.trigger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.application.WorkflowApplicationService;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.SyncExecutionService;
import com.kuaishou.intelligentanalysisplatform.domain.trigger.WorkflowTrigger;
import com.kuaishou.intelligentanalysisplatform.domain.trigger.WorkflowTriggerRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 工作流 Cron 调度器。
 *
 * <p>每分钟扫描一次 next_fire_at ≤ now 的 SCHEDULE/ACTIVE 触发器，异步执行工作流后更新
 * last_fire_at / last_status / next_fire_at。每个触发器提交到独立线程执行，避免慢工作流
 * 阻塞同批次其他触发器。
 */
@Component
@RequiredArgsConstructor
public class WorkflowTriggerScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTriggerScheduler.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /** Dedicated thread pool for async trigger execution (daemon threads, won't block JVM shutdown). */
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);
    private static final Executor TRIGGER_EXECUTOR = Executors.newFixedThreadPool(
            4, r -> {
                Thread t = new Thread(r, "trigger-exec-" + THREAD_COUNTER.incrementAndGet());
                t.setDaemon(true);
                return t;
            });

    private final WorkflowTriggerRepository triggerRepository;
    private final WorkflowApplicationService workflowApplicationService;
    private final SyncExecutionService syncExecutionService;
    private final ObjectMapper objectMapper;

    /** Scans and fires all due SCHEDULE triggers asynchronously, with a 10-second startup delay. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void fireReadyTriggers() {
        long now = System.currentTimeMillis();
        List<WorkflowTrigger> due = triggerRepository.findDueCronTriggers(now);
        for (WorkflowTrigger trigger : due) {
            TRIGGER_EXECUTOR.execute(() -> {
                try {
                    fireTrigger(trigger, now);
                } catch (Exception e) {
                    log.error("Failed to fire trigger [{}] for workflow [{}]",
                            trigger.getId(), trigger.getWorkflowId(), e);
                    // Advance next_fire_at even on failure to avoid infinite retry loop
                    Long next = trigger.calculateNextFireAt();
                    triggerRepository.updateLastFire(trigger.getId(), now,
                            "FAILED", next != null ? next : now + 60_000L);
                }
            });
        }
    }

    private void fireTrigger(WorkflowTrigger trigger, long now) {
        RequestContextDTO context = RequestContextDTO.builder()
                .tenantId(trigger.getTenantId())
                .userId("trigger-scheduler")
                .build();
        WorkflowDefinitionDTO workflow =
                workflowApplicationService.getById(trigger.getWorkflowId(), context);
        WorkflowRunRequestDTO runRequest = WorkflowRunRequestDTO.builder()
                .workflowId(workflow.getWorkflowId())
                .nodes(workflow.getNodes())
                .edges(workflow.getEdges())
                .inputs(parseDefaultInputs(trigger.getDefaultInputs()))
                .context(context)
                .async(Boolean.FALSE)
                .build();
        WorkflowRunResultDTO result = syncExecutionService.runWorkflow(runRequest);
        String status = result.getStatus() == null ? "UNKNOWN" : result.getStatus().name();
        Long next = trigger.calculateNextFireAt();
        triggerRepository.updateLastFire(trigger.getId(), now, status,
                next != null ? next : now + 60_000L);
        log.info("Trigger [{}] fired for workflow [{}], status={}",
                trigger.getId(), trigger.getWorkflowId(), status);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDefaultInputs(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            log.warn("Failed to parse defaultInputs JSON for trigger: {}", json, e);
            return Collections.emptyMap();
        }
    }
}
