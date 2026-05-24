package com.kuaishou.intelligentanalysisplatform.contract.schema.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiChatRequestDTO {
    @NotBlank
    private String prompt;
    private String conversationId;
    private java.util.List<java.util.Map<String, String>> history;
}
