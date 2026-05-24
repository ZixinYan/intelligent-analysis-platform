package com.kuaishou.intelligentanalysisplatform.application.ai.foundation.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class ClasspathPromptTemplateService implements PromptTemplateService {

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    @Override
    public String load(String templateName, Map<String, String> variables) {
        return renderTemplate(templateName, variables);
    }

    public static String renderTemplate(String templateName, Map<String, String> variables) {
        String template = CACHE.computeIfAbsent(templateName, ClasspathPromptTemplateService::loadTemplate);
        String result = template;
        if (variables == null || variables.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    private static String loadTemplate(String templateName) {
        try (InputStream is = ClasspathPromptTemplateService.class.getClassLoader()
                .getResourceAsStream("prompts/" + templateName)) {
            if (is == null) {
                throw new IllegalArgumentException("Prompt not found: " + templateName);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt: " + templateName, e);
        }
    }
}
