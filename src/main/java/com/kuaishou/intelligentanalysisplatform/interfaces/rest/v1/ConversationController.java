package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import java.util.Objects;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.common.response.ApiResponse;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.Conversation;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationRepository conversationRepository;

    @GetMapping("/{id}")
    public ApiResponse<Conversation> getConversation(
            @PathVariable("id") String conversationId,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        return ApiResponse.success(loadAuthorizedConversation(conversationId, tenantId, userId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteConversation(
            @PathVariable("id") String conversationId,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        loadAuthorizedConversation(conversationId, tenantId, userId);
        conversationRepository.deleteById(conversationId);
        return ApiResponse.success(null);
    }

    private Conversation loadAuthorizedConversation(String conversationId, String tenantId, String userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BaseBusinessException(
                        ErrorCode.CONVERSATION_NOT_FOUND, "conversationId not found: " + conversationId));
        if (!Objects.equals(conversation.getTenantId(), tenantId)
                || !Objects.equals(conversation.getUserId(), userId)) {
            throw new BaseBusinessException(
                    ErrorCode.CONVERSATION_NOT_FOUND, "conversationId not found: " + conversationId);
        }
        return conversation;
    }
}
