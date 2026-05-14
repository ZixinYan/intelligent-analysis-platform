package com.kuaishou.intelligentanalysisplatform.domain.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessage {
    private ConversationRole role;
    private String content;
    private Long timestamp;          // epoch millis
    private Integer estimatedTokens; // 粗略估算：content.length() / 4 + 1
}
