package com.kuaishou.intelligentanalysisplatform.contract.schema.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiChatRequestDTO {
    @NotBlank
    private String prompt;
    /** 多轮对话历史（可选），每条格式为 {"role": "user"|"assistant", "content": "..."} */
    private java.util.List<java.util.Map<String, String>> history;
}
