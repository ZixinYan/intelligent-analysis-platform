package com.kuaishou.intelligentanalysisplatform.application.ai;

import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiSqlRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI SQL 生成服务。
 * 根据自然语言描述和表 Schema，流式生成 SQL 查询语句。
 */
public interface AiSqlGenerationService {
    /**
     * 流式生成 SQL，结果通过 SseEmitter 推送给前端。
     */
    void generateSql(AiSqlRequestDTO request, RequestContextDTO context, SseEmitter emitter);
}
