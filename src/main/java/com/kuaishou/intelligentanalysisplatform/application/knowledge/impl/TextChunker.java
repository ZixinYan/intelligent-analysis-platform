package com.kuaishou.intelligentanalysisplatform.application.knowledge.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 固定窗口分块器，支持重叠以保留上下文。
 */
@Component
public class TextChunker {

    private static final int DEFAULT_CHUNK_SIZE = 512;
    private static final int DEFAULT_OVERLAP = 64;

    public List<String> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public List<String> chunk(String text, int size, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + size, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) break;
            start += (size - overlap);
        }
        return chunks;
    }
}
