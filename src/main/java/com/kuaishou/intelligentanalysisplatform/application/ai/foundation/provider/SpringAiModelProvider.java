package com.kuaishou.intelligentanalysisplatform.application.ai.foundation.provider;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

@Component
public class SpringAiModelProvider implements AiModelProvider {

    private static final Logger log = LoggerFactory.getLogger(SpringAiModelProvider.class);

    private final ChatModel chatModel;

    public SpringAiModelProvider(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public void streamChat(AiChatRequest request, AiStreamCallbacks callbacks) {
        try {
            Prompt prompt = buildPrompt(request);
            chatModel.stream(prompt)
                    .doOnNext(response -> {
                        String content = response.getResult().getOutput().getText();
                        if (content != null && !content.isEmpty()) {
                            callbacks.onToken().accept(content);
                        }
                    })
                    .doOnComplete(callbacks.onComplete()::run)
                    .doOnError(callbacks.onError()::accept)
                    .subscribe();
        } catch (Exception e) {
            callbacks.onError().accept(e);
        }
    }

    @Override
    public String completeChat(AiChatRequest request) {
        Prompt prompt = buildPrompt(request);
        return chatModel.call(prompt).getResult().getOutput().getText();
    }

    @Override
    public String providerType() {
        return "spring-ai";
    }

    private Prompt buildPrompt(AiChatRequest request) {
        List<Message> messages = new ArrayList<>();
        if (request.history() != null && !request.history().isEmpty()) {
            for (AiMessage msg : request.history()) {
                messages.add(toSpringMessage(msg));
            }
        } else {
            if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
                messages.add(new SystemMessage(request.systemPrompt()));
            }
            if (request.userMessage() != null) {
                messages.add(new UserMessage(request.userMessage()));
            }
        }
        return new Prompt(messages);
    }

    private Message toSpringMessage(AiMessage msg) {
        String role = msg.role() == null ? "user" : msg.role().toLowerCase();
        String content = msg.content() == null ? "" : msg.content();
        return switch (role) {
            case "system" -> new SystemMessage(content);
            case "assistant" -> new AssistantMessage(content);
            default -> new UserMessage(content);
        };
    }
}
