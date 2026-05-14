package com.kuaishou.intelligentanalysisplatform.domain.conversation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {
    private String conversationId;   // UUID
    private String tenantId;
    private String userId;
    private String topic;            // 可选，如 "SQL生成-订单分析"
    private List<ConversationMessage> messages;
    private Long createdAt;
    private Long updatedAt;

    /**
     * 按 Token 窗口裁剪，保留 system 消息 + 最近 N 条，总 token ≤ maxTokens。
     */
    public List<ConversationMessage> getWindowedHistory(int maxTokens) {
        List<ConversationMessage> system = messages.stream()
                .filter(m -> m.getRole() == ConversationRole.SYSTEM)
                .toList();
        List<ConversationMessage> rest = messages.stream()
                .filter(m -> m.getRole() != ConversationRole.SYSTEM)
                .toList();

        int budget = maxTokens;
        for (ConversationMessage m : system) budget -= m.getEstimatedTokens();

        Deque<ConversationMessage> window = new ArrayDeque<>();
        for (int i = rest.size() - 1; i >= 0 && budget > 0; i--) {
            ConversationMessage m = rest.get(i);
            budget -= m.getEstimatedTokens();
            window.addFirst(m);
        }

        List<ConversationMessage> result = new ArrayList<>(system);
        result.addAll(window);
        return result;
    }

    public void appendUser(String content) {
        append(ConversationRole.USER, content);
    }

    public void appendAssistant(String content) {
        append(ConversationRole.ASSISTANT, content);
    }

    private void append(ConversationRole role, String content) {
        if (messages == null) messages = new ArrayList<>();
        messages.add(ConversationMessage.builder()
                .role(role)
                .content(content)
                .timestamp(System.currentTimeMillis())
                .estimatedTokens(content.length() / 4 + 1)
                .build());
        this.updatedAt = System.currentTimeMillis();
    }
}
