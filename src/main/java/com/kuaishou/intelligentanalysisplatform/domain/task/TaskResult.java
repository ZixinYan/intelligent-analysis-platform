package com.kuaishou.intelligentanalysisplatform.domain.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResult {
    private String taskId;
    private String resultJson;
    private Long createdAt;
}
