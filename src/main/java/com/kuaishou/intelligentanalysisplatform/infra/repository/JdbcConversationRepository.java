package com.kuaishou.intelligentanalysisplatform.infra.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import com.kuaishou.intelligentanalysisplatform.domain.conversation.Conversation;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.ConversationRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Persists conversation header (metadata only).
 * Messages are stored separately in ai_conversation_message via JdbcConversationMessageRepository.
 */
@Repository
public class JdbcConversationRepository implements ConversationRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcConversationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Conversation save(Conversation conversation) {
        int updated = jdbcTemplate.update("""
                UPDATE ai_conversation
                SET tenant_id  = :tenantId,
                    user_id    = :userId,
                    topic      = :topic,
                    updated_at = :updatedAt
                WHERE conversation_id = :conversationId
                """, toHeaderParams(conversation));

        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO ai_conversation (conversation_id, tenant_id, user_id, topic, created_at, updated_at)
                    VALUES (:conversationId, :tenantId, :userId, :topic, :createdAt, :updatedAt)
                    """, toHeaderParams(conversation));
        }
        return conversation;
    }

    @Override
    public Optional<Conversation> findById(String conversationId) {
        return jdbcTemplate.query("""
                SELECT conversation_id, tenant_id, user_id, topic, created_at, updated_at
                FROM ai_conversation
                WHERE conversation_id = :conversationId
                """, Map.of("conversationId", conversationId), rs -> {
            if (!rs.next()) return Optional.empty();
            return Optional.of(mapRow(rs));
        });
    }

    @Override
    public void deleteById(String conversationId) {
        jdbcTemplate.update(
                "DELETE FROM ai_conversation WHERE conversation_id = :conversationId",
                Map.of("conversationId", conversationId));
    }

    private MapSqlParameterSource toHeaderParams(Conversation conv) {
        return new MapSqlParameterSource()
                .addValue("conversationId", conv.getConversationId())
                .addValue("tenantId", conv.getTenantId())
                .addValue("userId", conv.getUserId())
                .addValue("topic", conv.getTopic())
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
                .messages(new ArrayList<>())  // populated by ConversationMessageRepository
                .createdAt(rs.getLong("created_at"))
                .updatedAt(rs.getLong("updated_at"))
                .build();
    }
}
