package com.kuaishou.intelligentanalysisplatform.application.ai.foundation.parser;

public interface LlmOutputParser {

    String extractJson(String rawText);
}
