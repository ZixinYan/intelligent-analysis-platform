package com.kuaishou.intelligentanalysisplatform.infra.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.Conversation;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.ConversationMessage;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcConversationRepository implements ConversationRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcConversationRepository.class);
    private static final TypeReference<List<ConversationMessage>> MESSAGE_LIST_TYPE =
            new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcConversationRepository(NamedParameterJdbcTemplate jdbcTemplate,
                                       ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Conversation save(Conversation conversation) {
        int updated = jdbcTemplate.update("""
                UPDATE ai_conversation
                SET tenant_id    = :tenantId,
                    user_id      = :userId,
                    topic        = :topic,
                    messages_json = :messagesJson,
                    updated_at   = :updatedAt
                WHERE conversation_id = :conversationId
                """, toParams(conversation));

        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO ai_conversation (
                        conversation_id, tenant_id, user_id, topic, messages_json, created_at, updated_at
                    ) VALUES (
                        :conversationId, :tenantId, :userId, :topic, :messagesJson, :createdAt, :updatedAt
                    )
                    """, toParams(conversation));
        }
        return conversation;
    }

    @Override
    public Optional<Conversation> findById(String conversationId) {
        return jdbcTemplate.query("""
                SELECT conversation_id, tenant_id, user_id, topic, messages_json, created_at, updated_at
                FROM ai_conversation
                WHERE conversation_id = :conversationId
                """, Map.of("conversationId", conversationId), rs -> {
            if (!rs.next()) return Optional.empty();
            return Optional.of(mapRow(rs));
        });
    }

    @Override
    public void deleteById(String conversationId) {
        jdbcTemplate.update("""
                DELETE FROM ai_conversation WHERE conversation_id = :conversationId
                """, Map.of("conversationId", conversationId));
    }

    private MapSqlParameterSource toParams(Conversation conv) {
        return new MapSqlParameterSource()
                .addValue("conversationId", conv.getConversationId())
                .addValue("tenantId", conv.getTenantId())
                .addValue("userId", conv.getUserId())
                .addValue("topic", conv.getTopic())
                .addValue("messagesJson", serializeMessages(conv.getMessages()))
                .addValue("createdAt", conv.getCreatedAt())
                .addValue("updatedAt", conv.getUpdatedAt() != null
                        ? conv.getUpdatedAt() : conv.getCreatedAt());
    }

    private Conversation mapRow(ResultSet rs) throws SQLException {
        return Conversation.builder()
                .conversationId(rs.getString("conversation_id"))
                .tenantId(rs.getString("tenant_id"))
                .userId(rs.getString("user_id"))
                .topic(rs.getString("topic"))
                .messages(deserializeMessages(rs.getString("messages_json")))
                .createdAt(rs.getLong("created_at"))
                .updatedAt(rs.getLong("updated_at"))
                .build();
    }

    private String serializeMessages(List<ConversationMessage> messages) {
        try {
            return objectMapper.writeValueAsString(
                    messages != null ? messages : new ArrayList<>());
        } catch (Exception e) {
            log.error("Failed to serialize conversation messages", e);
            return "[]";
        }
    }

    private List<ConversationMessage> deserializeMessages(String json) {
        try {
            if (json == null || json.isBlank()) return new ArrayList<>();
            return objectMapper.readValue(json, MESSAGE_LIST_TYPE);
        } catch (Exception e) {
            log.error("Failed to deserialize conversation messages", e);
            return new ArrayList<>();
        }
    }
}
