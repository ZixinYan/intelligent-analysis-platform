package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.sql;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class AiSseStreamResponder {

    private static final Logger log = LoggerFactory.getLogger(AiSseStreamResponder.class);

    private final ObjectMapper objectMapper;

    public AiSseStreamResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void sendToken(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event().name("token").data(token));
        } catch (IOException e) {
            log.debug("SSE send token failed (client disconnected)");
        }
    }

    public void sendDoneWithConvId(SseEmitter emitter, String conversationId) {
        try {
            String data = objectMapper.writeValueAsString(Map.of("conversationId", conversationId));
            emitter.send(SseEmitter.event().name("done").data(data));
            emitter.complete();
        } catch (IOException e) {
            log.debug("SSE complete failed");
        }
    }

    public void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message != null ? message : "AI generation failed"));
            emitter.complete();
        } catch (IOException e) {
            log.debug("SSE error send failed");
        }
    }
}
