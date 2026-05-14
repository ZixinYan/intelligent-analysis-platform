package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import com.kuaishou.intelligentanalysisplatform.application.ai.ConversationContextService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.common.response.ApiResponse;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.Conversation;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话历史管理接口。
 */
@RestController
@RequestMapping("/api/v1/ai/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationRepository conversationRepository;

    /**
     * 获取会话历史消息列表。
     */
    @GetMapping("/{id}")
    public ApiResponse<Conversation> getConversation(@PathVariable("id") String conversationId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BaseBusinessException(
                        ErrorCode.CONVERSATION_NOT_FOUND, "conversationId not found: " + conversationId));
        return ApiResponse.success(conv);
    }

    /**
     * 清空/删除会话。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteConversation(@PathVariable("id") String conversationId) {
        conversationRepository.deleteById(conversationId);
        return ApiResponse.success(null);
    }
}
