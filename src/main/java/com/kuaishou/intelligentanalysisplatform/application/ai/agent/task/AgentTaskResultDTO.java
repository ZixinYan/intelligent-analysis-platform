package com.kuaishou.intelligentanalysisplatform.application.ai.agent.task;

import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiClarificationQuestionDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTaskResultDTO {
    private String agentTaskId;
    private AiClarificationQuestionDTO clarification;
    private Map<String, Object> trace;
    private Double confidence;
    private String summary;
}
