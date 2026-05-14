package com.kuaishou.intelligentanalysisplatform.infra.es;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kuaishou.intelligentanalysisplatform.config.KnowledgeEsProperties;
import com.kuaishou.intelligentanalysisplatform.contract.schema.KnowledgeChunkDTO;
import com.kuaishou.intelligentanalysisplatform.domain.knowledge.KnowledgeChunk;
import com.kuaishou.intelligentanalysisplatform.domain.knowledge.KnowledgeChunkRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * 使用 Elasticsearch dense_vector 实现知识库 chunk 的向量存储与 kNN 检索。
 * 通过 Java HttpClient 调用 ES REST API，无需额外依赖。
 */
@Repository
public class EsKnowledgeChunkRepository implements KnowledgeChunkRepository {

    private static final Logger log = LoggerFactory.getLogger(EsKnowledgeChunkRepository.class);

    private final KnowledgeEsProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public EsKnowledgeChunkRepository(KnowledgeEsProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.getConnectTimeoutSeconds()))
                .build();
    }

    @PostConstruct
    public void ensureIndexExists() {
        try {
            HttpRequest checkReq = buildRequest("HEAD", indexUrl(), null);
            HttpResponse<Void> checkResp = httpClient.send(checkReq, HttpResponse.BodyHandlers.discarding());
            if (checkResp.statusCode() == 200) {
                log.debug("ES index {} already exists", props.getIndexName());
                return;
            }

            // Create index with dense_vector mapping
            String mapping = buildIndexMapping();
            HttpRequest createReq = buildRequest("PUT", indexUrl(), mapping);
            HttpResponse<String> createResp = httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());
            if (createResp.statusCode() == 200 || createResp.statusCode() == 201) {
                log.info("Created ES index {} for knowledge chunks", props.getIndexName());
            } else {
                log.error("Failed to create ES index {}: {} {}", props.getIndexName(),
                        createResp.statusCode(), createResp.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while ensuring ES index exists", e);
        } catch (Exception e) {
            log.warn("Failed to ensure ES index exists, continuing anyway: {}", e.getMessage());
        }
    }

    @Override
    public void saveAll(List<KnowledgeChunk> chunks) {
        if (chunks.isEmpty()) return;
        try {
            StringBuilder bulk = new StringBuilder();
            for (KnowledgeChunk chunk : chunks) {
                // Action line
                ObjectNode action = objectMapper.createObjectNode();
                action.putObject("index")
                        .put("_index", props.getIndexName())
                        .put("_id", chunk.getId());
                bulk.append(objectMapper.writeValueAsString(action)).append('\n');

                // Document line
                ObjectNode doc = objectMapper.createObjectNode();
                doc.put("id", chunk.getId());
                doc.put("knowledge_base_id", chunk.getKnowledgeBaseId());
                doc.put("doc_id", chunk.getDocId());
                doc.put("doc_title", chunk.getDocTitle());
                doc.put("content", chunk.getContent());
                doc.put("chunk_index", chunk.getChunkIndex());
                doc.put("created_at", chunk.getCreatedAt());
                // embedding as array
                ArrayNode embedding = doc.putArray("embedding");
                for (float v : chunk.getEmbedding()) {
                    embedding.add(v);
                }
                bulk.append(objectMapper.writeValueAsString(doc)).append('\n');
            }

            HttpRequest req = buildRequest("POST", props.getUrl() + "/_bulk", bulk.toString());
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new RuntimeException("ES bulk index failed: " + resp.statusCode() + " " + resp.body());
            }
            JsonNode root = objectMapper.readTree(resp.body());
            if (root.path("errors").asBoolean(false)) {
                log.error("ES bulk index had errors: {}", resp.body());
                throw new RuntimeException("ES bulk index had errors, check logs for details");
            }
            log.debug("Bulk indexed {} chunks to ES", chunks.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("ES bulk index interrupted", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("ES bulk index failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteByKbIdAndDocId(String knowledgeBaseId, String docId) {
        deleteByQuery(buildDeleteByKbAndDocQuery(knowledgeBaseId, docId));
    }

    @Override
    public void deleteByKnowledgeBaseId(String knowledgeBaseId) {
        deleteByQuery(buildDeleteByKbQuery(knowledgeBaseId));
    }

    @Override
    public List<KnowledgeChunkDTO> findTopKByCosine(String knowledgeBaseId, float[] queryEmbedding, int topK) {
        try {
            ObjectNode searchBody = objectMapper.createObjectNode();
            searchBody.put("size", topK);

            // kNN query with filter
            ObjectNode knn = searchBody.putObject("knn");
            knn.put("field", "embedding");
            ArrayNode queryVector = knn.putArray("query_vector");
            for (float v : queryEmbedding) {
                queryVector.add(v);
            }
            knn.put("k", topK);
            knn.put("num_candidates", props.getNumCandidates());
            // filter by knowledge base
            knn.putObject("filter")
                    .putObject("term")
                    .put("knowledge_base_id", knowledgeBaseId);

            String body = objectMapper.writeValueAsString(searchBody);
            HttpRequest req = buildRequest("POST", indexUrl() + "/_search", body);
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new RuntimeException("ES kNN search failed: " + resp.statusCode() + " " + resp.body());
            }

            return parseSearchResults(resp.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("ES kNN search interrupted", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("ES kNN search failed: " + e.getMessage(), e);
        }
    }

    private void deleteByQuery(String queryBody) {
        try {
            HttpRequest req = buildRequest("POST", indexUrl() + "/_delete_by_query", queryBody);
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.error("ES delete by query failed: {} {}", resp.statusCode(), resp.body());
                throw new RuntimeException("ES delete by query failed: " + resp.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("ES delete by query interrupted", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("ES delete by query failed: " + e.getMessage(), e);
        }
    }

    private List<KnowledgeChunkDTO> parseSearchResults(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode hits = root.path("hits").path("hits");
        List<KnowledgeChunkDTO> results = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            double score = hit.path("_score").asDouble(0.0);
            results.add(KnowledgeChunkDTO.builder()
                    .id(source.path("id").asText())
                    .docId(source.path("doc_id").asText())
                    .docTitle(source.path("doc_title").asText())
                    .content(source.path("content").asText())
                    .chunkIndex(source.path("chunk_index").asInt())
                    .score(score)
                    .build());
        }
        return results;
    }

    private String buildDeleteByKbAndDocQuery(String kbId, String docId) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode filter = body.putObject("query").putObject("bool").putArray("filter");
            filter.addObject().putObject("term").put("knowledge_base_id", kbId);
            filter.addObject().putObject("term").put("doc_id", docId);
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build delete query", e);
        }
    }

    private String buildDeleteByKbQuery(String kbId) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.putObject("query").putObject("term").put("knowledge_base_id", kbId);
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build delete query", e);
        }
    }

    private String buildIndexMapping() {
        try {
            ObjectNode mapping = objectMapper.createObjectNode();
            ObjectNode properties = mapping.putObject("mappings").putObject("properties");
            properties.putObject("id").put("type", "keyword");
            properties.putObject("knowledge_base_id").put("type", "keyword");
            properties.putObject("doc_id").put("type", "keyword");
            properties.putObject("doc_title").put("type", "text");
            properties.putObject("content").put("type", "text");
            properties.putObject("chunk_index").put("type", "integer");
            properties.putObject("created_at").put("type", "long");
            // dense_vector for kNN
            ObjectNode embeddingField = properties.putObject("embedding");
            embeddingField.put("type", "dense_vector");
            embeddingField.put("dims", props.getVectorDimension());
            embeddingField.put("index", true);
            embeddingField.put("similarity", "cosine");
            return objectMapper.writeValueAsString(mapping);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build index mapping", e);
        }
    }

    private HttpRequest buildRequest(String method, String url, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(props.getReadTimeoutSeconds()))
                .header("Content-Type", "application/json");

        if (!props.getUsername().isBlank()) {
            String credentials = props.getUsername() + ":" + props.getPassword();
            String encoded = Base64.getEncoder().encodeToString(
                    credentials.getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + encoded);
        }

        if ("HEAD".equals(method)) {
            builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
        } else if ("GET".equals(method)) {
            builder.GET();
        } else if ("PUT".equals(method)) {
            builder.PUT(body != null
                    ? HttpRequest.BodyPublishers.ofString(body)
                    : HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, body != null
                    ? HttpRequest.BodyPublishers.ofString(body)
                    : HttpRequest.BodyPublishers.noBody());
        }
        return builder.build();
    }

    private String indexUrl() {
        return props.getUrl().replaceAll("/+$", "") + "/" + props.getIndexName();
    }
}
