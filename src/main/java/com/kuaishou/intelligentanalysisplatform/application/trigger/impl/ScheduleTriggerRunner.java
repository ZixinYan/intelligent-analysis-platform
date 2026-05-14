package com.kuaishou.intelligentanalysisplatform.application.trigger.impl;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncSubmitResponseDTO;
import com.kuaishou.intelligentanalysisplatform.domain.trigger.TriggerStatus;
import com.kuaishou.intelligentanalysisplatform.domain.trigger.TriggerType;
import com.kuaishou.intelligentanalysisplatform.domain.trigger.WorkflowTrigger;
import com.kuaishou.intelligentanalysisplatform.domain.trigger.WorkflowTriggerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleTriggerRunner {

    private final WorkflowTriggerRepository triggerRepository;
    private final DefaultTriggerApplicationService triggerApplicationService;

    /** 每分钟整点扫描一次，误差 < 1 分钟 */
    @Scheduled(cron = "0 * * * * *")
    public void scanAndFire() {
        long now = System.currentTimeMillis();
        List<WorkflowTrigger> due = triggerRepository.findDueScheduleTriggers(
                TriggerType.SCHEDULE, TriggerStatus.ACTIVE, now);

        for (WorkflowTrigger trigger : due) {
            try {
                AsyncSubmitResponseDTO resp = triggerApplicationService.submitWorkflow(trigger);
                trigger.setLastFireAt(System.currentTimeMillis());
                trigger.setLastRunId(resp.getTaskId());
                trigger.setLastStatus("SUBMITTED");
                triggerRepository.save(trigger);
                log.info("Schedule trigger fired: triggerId={} workflowId={} taskId={}",
                        trigger.getId(), trigger.getWorkflowId(), resp.getTaskId());
            } catch (Exception e) {
                log.error("Failed to fire schedule trigger id={}", trigger.getId(), e);
                // 仍然推进 next_fire_at，避免死循环重复触发
            } finally {
                Long next = trigger.calculateNextFireAt();
                triggerRepository.updateNextFireAt(trigger.getId(), next, now);
            }
        }
    }
}
