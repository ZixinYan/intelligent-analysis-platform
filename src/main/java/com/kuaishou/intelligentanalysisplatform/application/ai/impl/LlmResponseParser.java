package com.kuaishou.intelligentanalysisplatform.application.ai.impl;

/**
 * Shared utility for parsing raw LLM text responses.
 */
public final class LlmResponseParser {

    private LlmResponseParser() {}

    /**
     * Extracts the first JSON object from an LLM response that may contain surrounding text.
     */
    public static String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text.trim();
    }
}
