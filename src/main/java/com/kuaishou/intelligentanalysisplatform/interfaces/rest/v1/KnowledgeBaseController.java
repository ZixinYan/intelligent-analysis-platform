package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.application.knowledge.KnowledgeBaseService;
import com.kuaishou.intelligentanalysisplatform.common.response.ApiResponse;
import com.kuaishou.intelligentanalysisplatform.contract.schema.KnowledgeChunkDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.knowledge.CreateKnowledgeBaseRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.knowledge.IngestDocumentRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.knowledge.KnowledgeBaseDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.knowledge.RetrieveRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库管理接口：支持创建、上传文档、删除、语义检索。
 */
@RestController
@RequestMapping("/api/v1/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    /** 创建知识库 */
    @PostMapping
    public ApiResponse<KnowledgeBaseDTO> create(
            @Valid @RequestBody CreateKnowledgeBaseRequestDTO request,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        KnowledgeBaseDTO dto = knowledgeBaseService.createKnowledgeBase(
                tenantId, request.getName(), request.getDescription());
        return ApiResponse.success(dto);
    }

    /** 知识库列表 */
    @GetMapping
    public ApiResponse<List<KnowledgeBaseDTO>> list(
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ApiResponse.success(knowledgeBaseService.listKnowledgeBases(tenantId));
    }

    /** 删除知识库及其所有文档 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable("id") String kbId,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        knowledgeBaseService.deleteKnowledgeBase(kbId, tenantId);
        return ApiResponse.success();
    }

    /** 上传/替换文档（文本或 Markdown） */
    @PostMapping("/{id}/documents")
    public ApiResponse<Void> ingestDocument(
            @PathVariable("id") String kbId,
            @Valid @RequestBody IngestDocumentRequestDTO request,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        knowledgeBaseService.ingestDocument(
                kbId, request.getDocId(), request.getDocTitle(), request.getContent(), tenantId);
        return ApiResponse.success();
    }

    /** 删除单个文档及其 chunks */
    @DeleteMapping("/{id}/documents/{docId}")
    public ApiResponse<Void> deleteDocument(
            @PathVariable("id") String kbId,
            @PathVariable("docId") String docId,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        knowledgeBaseService.deleteDocument(kbId, docId, tenantId);
        return ApiResponse.success();
    }

    /** 调试检索：返回 top-K 相关片段 */
    @PostMapping("/{id}/retrieve")
    public ApiResponse<List<KnowledgeChunkDTO>> retrieve(
            @PathVariable("id") String kbId,
            @Valid @RequestBody RetrieveRequestDTO request,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        List<KnowledgeChunkDTO> chunks = knowledgeBaseService.retrieve(
                kbId, request.getQuery(), request.getTopK());
        return ApiResponse.success(chunks);
    }
}
