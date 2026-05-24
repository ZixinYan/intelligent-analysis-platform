package com.kuaishou.intelligentanalysisplatform.infra.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kuaishou.intelligentanalysisplatform.domain.conversation.ConversationMessage;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.ConversationMessageRepository;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.ConversationRole;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcConversationMessageRepository implements ConversationMessageRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcConversationMessageRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void append(String conversationId, ConversationMessage message) {
        jdbcTemplate.update("""
                INSERT INTO ai_conversation_message (message_id, conversation_id, role, content, estimated_tokens, created_at)
                VALUES (:messageId, :conversationId, :role, :content, :tokens, :createdAt)
                """,
                new MapSqlParameterSource()
                        .addValue("messageId", UUID.randomUUID().toString())
                        .addValue("conversationId", conversationId)
                        .addValue("role", message.getRole().name())
                        .addValue("content", message.getContent())
                        .addValue("tokens", message.getEstimatedTokens())
                        .addValue("createdAt", message.getTimestamp() != null
                                ? message.getTimestamp() : System.currentTimeMillis()));
    }

    @Override
    public void upsertSystemMessage(String conversationId, String content, long now) {
        // Use a deterministic message_id (conversationId + ":system") so concurrent
        // calls are idempotent: the primary-key constraint prevents duplicate inserts
        // and any duplicate INSERT races fall back to an UPDATE.
        String systemMessageId = conversationId + ":system";
        int updated = jdbcTemplate.update("""
                UPDATE ai_conversation_message
                SET content = :content, estimated_tokens = :tokens
                WHERE message_id = :messageId
                """,
                new MapSqlParameterSource()
                        .addValue("content", content != null ? content : "")
                        .addValue("tokens", content != null ? content.length() / 4 + 1 : 0)
                        .addValue("messageId", systemMessageId));

        if (updated == 0) {
            try {
                jdbcTemplate.update("""
                        INSERT INTO ai_conversation_message (message_id, conversation_id, role, content, estimated_tokens, created_at)
                        VALUES (:messageId, :conversationId, 'SYSTEM', :content, :tokens, :now)
                        """,
                        new MapSqlParameterSource()
                                .addValue("messageId", systemMessageId)
                                .addValue("conversationId", conversationId)
                                .addValue("content", content != null ? content : "")
                                .addValue("tokens", content != null ? content.length() / 4 + 1 : 0)
                                .addValue("now", now));
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // Concurrent insert won the race — retry the update
                jdbcTemplate.update("""
                        UPDATE ai_conversation_message
                        SET content = :content, estimated_tokens = :tokens
                        WHERE message_id = :messageId
                        """,
                        new MapSqlParameterSource()
                                .addValue("content", content != null ? content : "")
                                .addValue("tokens", content != null ? content.length() / 4 + 1 : 0)
                                .addValue("messageId", systemMessageId));
            }
        }
    }

    @Override
    public List<ConversationMessage> findByConversationId(String conversationId) {
        return jdbcTemplate.query("""
                SELECT role, content, estimated_tokens, created_at
                FROM ai_conversation_message
                WHERE conversation_id = :conversationId
                ORDER BY created_at ASC
                """,
                Map.of("conversationId", conversationId),
                (rs, rowNum) -> mapRow(rs));
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        jdbcTemplate.update(
                "DELETE FROM ai_conversation_message WHERE conversation_id = :conversationId",
                Map.of("conversationId", conversationId));
    }

    private ConversationMessage mapRow(ResultSet rs) throws SQLException {
        return ConversationMessage.builder()
                .role(ConversationRole.valueOf(rs.getString("role")))
                .content(rs.getString("content"))
                .estimatedTokens(rs.getInt("estimated_tokens"))
                .timestamp(rs.getLong("created_at"))
                .build();
    }
}
