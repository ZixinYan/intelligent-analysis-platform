package com.kuaishou.intelligentanalysisplatform.domain.trigger;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import lombok.Builder;
import lombok.Data;
import org.springframework.scheduling.support.CronExpression;

@Data
@Builder
public class WorkflowTrigger {
    private String        id;
    private String        workflowId;
    private String        tenantId;
    private TriggerType   triggerType;
    private TriggerStatus triggerStatus;
    // 定时
    private String        cronExpr;
    private Long          nextFireAt;
    // Webhook
    private String        webhookToken;
    private String        secretKey;
    // 公共
    private String        defaultInputs;  // JSON
    private Long          lastFireAt;
    private String        lastRunId;
    private String        lastStatus;
    private Long          createdAt;
    private Long          updatedAt;

    public Long calculateNextFireAt() {
        if (cronExpr == null) return null;
        CronExpression cron = CronExpression.parse(cronExpr);
        ZonedDateTime next = cron.next(ZonedDateTime.now(ZoneId.systemDefault()));
        return next != null ? next.toInstant().toEpochMilli() : null;
    }

    public String webhookPath() {
        return "/webhook/" + webhookToken;
    }
}
