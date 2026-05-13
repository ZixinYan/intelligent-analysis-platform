package com.kuaishou.intelligentanalysisplatform.application.ai.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 从 classpath resources/prompts/ 加载 Prompt 模板，支持 {{KEY}} 占位符替换。
 */
public class PromptLoader {

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private PromptLoader() {}

    public static String load(String filename, Map<String, String> vars) {
        String template = CACHE.computeIfAbsent(filename, f -> {
            try (InputStream is = PromptLoader.class.getClassLoader()
                    .getResourceAsStream("prompts/" + f)) {
                if (is == null) throw new IllegalArgumentException("Prompt not found: " + f);
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load prompt: " + f, e);
            }
        });
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
