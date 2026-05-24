package com.kuaishou.intelligentanalysisplatform.application.ai.foundation.parser;

import org.springframework.stereotype.Component;

@Component
public class DefaultLlmOutputParser implements LlmOutputParser {

    @Override
    public String extractJson(String rawText) {
        return extractJsonContent(rawText);
    }

    public static String extractJsonContent(String rawText) {
        if (rawText == null) {
            return "";
        }
        int start = rawText.indexOf('{');
        int end = rawText.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return rawText.substring(start, end + 1);
        }
        return rawText.trim();
    }
}
