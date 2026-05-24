package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.sql;

import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.AiSqlOrchestrationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.AiStreamOutputHandler;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiSqlRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultAiSqlOrchestrationService implements AiSqlOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiSqlOrchestrationService.class);

    private final AiSqlPromptContextAssembler promptContextAssembler;
    private final AiSqlStreamExecutor streamExecutor;

    public DefaultAiSqlOrchestrationService(AiSqlPromptContextAssembler promptContextAssembler,
                                            AiSqlStreamExecutor streamExecutor) {
        this.promptContextAssembler = promptContextAssembler;
        this.streamExecutor = streamExecutor;
    }

    @Override
    public void generateSql(AiSqlRequestDTO request, RequestContextDTO context, AiStreamOutputHandler handler) {
        try {
            AiSqlPromptContext promptContext = promptContextAssembler.assemble(request, context);
            streamExecutor.stream(promptContext, context.getTenantId(), context.getUserId(), handler);
        } catch (Exception e) {
            log.error("Failed to start AI SQL generation", e);
            handler.onError(e.getMessage());
        }
    }
}
