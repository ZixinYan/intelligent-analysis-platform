package com.kuaishou.intelligentanalysisplatform.application.knowledge.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.kuaishou.intelligentanalysisplatform.application.knowledge.KnowledgeBaseService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.schema.KnowledgeChunkDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.knowledge.KnowledgeBaseDTO;
import com.kuaishou.intelligentanalysisplatform.domain.knowledge.KnowledgeBase;
import com.kuaishou.intelligentanalysisplatform.domain.knowledge.KnowledgeBaseRepository;
import com.kuaishou.intelligentanalysisplatform.domain.knowledge.KnowledgeChunk;
import com.kuaishou.intelligentanalysisplatform.domain.knowledge.KnowledgeChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultKnowledgeBaseService implements KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(DefaultKnowledgeBaseService.class);

    private final KnowledgeBaseRepository kbRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final EmbeddingClient embeddingClient;
    private final TextChunker chunker;

    public DefaultKnowledgeBaseService(KnowledgeBaseRepository kbRepository,
                                       KnowledgeChunkRepository chunkRepository,
                                       EmbeddingClient embeddingClient,
                                       TextChunker chunker) {
        this.kbRepository = kbRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingClient = embeddingClient;
        this.chunker = chunker;
    }

    @Override
    public KnowledgeBaseDTO createKnowledgeBase(String tenantId, String name, String description) {
        long now = System.currentTimeMillis();
        KnowledgeBase kb = KnowledgeBase.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .name(name)
                .description(description)
                .createdAt(now)
                .updatedAt(now)
                .build();
        kbRepository.save(kb);
        return toDTO(kb);
    }

    @Override
    public List<KnowledgeBaseDTO> listKnowledgeBases(String tenantId) {
        return kbRepository.findByTenantId(tenantId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteKnowledgeBase(String kbId, String tenantId) {
        kbRepository.findById(kbId).ifPresent(kb -> {
            chunkRepository.deleteByKnowledgeBaseId(kbId);
            kbRepository.deleteById(kbId);
        });
    }

    @Override
    public void ingestDocument(String kbId, String docId, String docTitle, String content, String tenantId) {
        kbRepository.findById(kbId).orElseThrow(() ->
                new BaseBusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "Knowledge base not found: " + kbId));

        // 先删旧版本
        chunkRepository.deleteByKbIdAndDocId(kbId, docId);

        // 分块 → 批量向量化
        List<String> chunkTexts = chunker.chunk(content);
        if (chunkTexts.isEmpty()) {
            log.warn("Document {} has no chunks after splitting, skipped", docId);
            return;
        }

        List<float[]> vectors = embeddingClient.embedBatch(chunkTexts);

        List<KnowledgeChunk> chunks = new ArrayList<>(chunkTexts.size());
        for (int i = 0; i < chunkTexts.size(); i++) {
            chunks.add(KnowledgeChunk.builder()
                    .id(UUID.randomUUID().toString())
                    .knowledgeBaseId(kbId)
                    .docId(docId)
                    .docTitle(docTitle)
                    .content(chunkTexts.get(i))
                    .embedding(vectors.get(i))
                    .chunkIndex(i)
                    .createdAt(System.currentTimeMillis())
                    .build());
        }
        chunkRepository.saveAll(chunks);
        log.info("Ingested document {} into kb {} with {} chunks", docId, kbId, chunks.size());
    }

    @Override
    public void deleteDocument(String kbId, String docId, String tenantId) {
        kbRepository.findById(kbId).orElseThrow(() ->
                new BaseBusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "Knowledge base not found: " + kbId));
        chunkRepository.deleteByKbIdAndDocId(kbId, docId);
    }

    @Override
    public List<KnowledgeChunkDTO> retrieve(String kbId, String query, int topK) {
        float[] queryVec = embeddingClient.embed(query);
        return chunkRepository.findTopKByCosine(kbId, queryVec, topK);
    }

    private KnowledgeBaseDTO toDTO(KnowledgeBase kb) {
        return KnowledgeBaseDTO.builder()
                .id(kb.getId())
                .tenantId(kb.getTenantId())
                .name(kb.getName())
                .description(kb.getDescription())
                .createdAt(kb.getCreatedAt())
                .updatedAt(kb.getUpdatedAt())
                .build();
    }
}
