package com.kuaishou.intelligentanalysisplatform.application.ai.foundation.prompt;

import java.util.Map;

public interface PromptTemplateService {

    String load(String templateName, Map<String, String> variables);
}
