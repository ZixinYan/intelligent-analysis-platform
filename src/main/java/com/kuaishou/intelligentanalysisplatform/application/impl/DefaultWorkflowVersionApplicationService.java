package com.kuaishou.intelligentanalysisplatform.application.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.application.WorkflowVersionApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.common.response.PageResult;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowEdgeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowNodeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowPositionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowVersionDiffDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowVersionDTO;
import com.kuaishou.intelligentanalysisplatform.domain.workflow.WorkflowDefinition;
import com.kuaishou.intelligentanalysisplatform.domain.workflow.WorkflowDefinitionRepository;
import com.kuaishou.intelligentanalysisplatform.domain.workflow.WorkflowVersion;
import com.kuaishou.intelligentanalysisplatform.domain.workflow.WorkflowVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultWorkflowVersionApplicationService implements WorkflowVersionApplicationService {

    /** 自动快照限频：5 分钟内重复 update 只创建一个版本 */
    private static final long AUTO_SNAPSHOT_THROTTLE_MS = 5 * 60 * 1000L;

    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final ObjectMapper objectMapper;

    public DefaultWorkflowVersionApplicationService(WorkflowDefinitionRepository workflowDefinitionRepository,
                                                    WorkflowVersionRepository workflowVersionRepository,
                                                    ObjectMapper objectMapper) {
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.workflowVersionRepository = workflowVersionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public WorkflowVersionDTO snapshot(String workflowId, String changeSummary, RequestContextDTO context) {
        WorkflowDefinition definition = requireDefinition(workflowId, context);

        // 自动快照限频：5 分钟内仅创建一次，直接返回已有版本
        if ("auto-save".equals(changeSummary)) {
            Optional<WorkflowVersion> latest = workflowVersionRepository.findLatestByWorkflowId(workflowId);
            if (latest.isPresent()
                    && System.currentTimeMillis() - latest.get().getCreatedAt() < AUTO_SNAPSHOT_THROTTLE_MS) {
                return toDto(latest.get());
            }
        }

        int nextVersion = workflowVersionRepository.getMaxVersionNumber(workflowId) + 1;
        long now = System.currentTimeMillis();

        WorkflowVersion version = WorkflowVersion.builder()
                .versionId(UUID.randomUUID().toString())
                .workflowId(workflowId)
                .tenantId(definition.getTenantId())
                .versionNumber(nextVersion)
                .definitionJson(definition.getDefinitionJson())
                .changeSummary(changeSummary)
                .published(false)
                .createdBy(context == null ? null : context.getUserId())
                .createdAt(now)
                .build();

        workflowVersionRepository.save(version);

        // 更新 workflow_definition.current_version_id
        definition.setCurrentVersionId(version.getVersionId());
        workflowDefinitionRepository.updateVersionRef(definition);

        return toDto(version);
    }

    @Override
    public PageResult<WorkflowVersionDTO> listVersions(String workflowId, int page, int pageSize,
                                                        RequestContextDTO context) {
        requireDefinition(workflowId, context);
        int safePage = page < 1 ? 1 : page;
        int safeSize = pageSize < 1 ? 20 : pageSize;
        int offset = (safePage - 1) * safeSize;

        List<WorkflowVersionDTO> items = workflowVersionRepository.findByWorkflowId(workflowId, offset, safeSize)
                .stream()
                .map(this::toDto)
                .toList();

        return PageResult.<WorkflowVersionDTO>builder()
                .items(items)
                .total(workflowVersionRepository.countByWorkflowId(workflowId))
                .page(safePage)
                .pageSize(safeSize)
                .build();
    }

    @Override
    public WorkflowDefinitionDTO getVersion(String workflowId, int versionNumber, RequestContextDTO context) {
        WorkflowDefinition definition = requireDefinition(workflowId, context);
        WorkflowVersion version = requireVersion(workflowId, versionNumber);
        return toDefinitionDto(definition, version);
    }

    @Override
    @Transactional
    public void publish(String workflowId, int versionNumber, RequestContextDTO context) {
        WorkflowDefinition definition = requireDefinition(workflowId, context);
        WorkflowVersion target = requireVersion(workflowId, versionNumber);

        // 先清除旧的已发布版本
        workflowVersionRepository.clearPublishedByWorkflowId(workflowId);
        // 设置新版本为已发布
        workflowVersionRepository.setPublished(workflowId, versionNumber, true);

        // 更新 workflow_definition.published_version_id
        definition.setPublishedVersionId(target.getVersionId());
        workflowDefinitionRepository.updateVersionRef(definition);
    }

    @Override
    @Transactional
    public WorkflowVersionDTO rollback(String workflowId, int versionNumber, RequestContextDTO context) {
        WorkflowDefinition definition = requireDefinition(workflowId, context);
        WorkflowVersion source = requireVersion(workflowId, versionNumber);

        int nextVersion = workflowVersionRepository.getMaxVersionNumber(workflowId) + 1;
        long now = System.currentTimeMillis();

        WorkflowVersion rolled = WorkflowVersion.builder()
                .versionId(UUID.randomUUID().toString())
                .workflowId(workflowId)
                .tenantId(definition.getTenantId())
                .versionNumber(nextVersion)
                .definitionJson(source.getDefinitionJson())
                .changeSummary("rollback from v" + versionNumber)
                .published(false)
                .createdBy(context == null ? null : context.getUserId())
                .createdAt(now)
                .build();

        workflowVersionRepository.save(rolled);

        // 将回滚内容写回 workflow_definition（成为新草稿）
        definition.setDefinitionJson(source.getDefinitionJson());
        definition.setCurrentVersionId(rolled.getVersionId());
        definition.setUpdatedAt(now);
        workflowDefinitionRepository.updateDefinitionAndVersionRef(definition);

        return toDto(rolled);
    }

    @Override
    public WorkflowVersionDiffDTO diff(String workflowId, int fromVersion, int toVersion,
                                        RequestContextDTO context) {
        requireDefinition(workflowId, context);
        WorkflowVersion from = requireVersion(workflowId, fromVersion);
        WorkflowVersion to = requireVersion(workflowId, toVersion);

        VersionDoc fromDoc = parseDoc(from.getDefinitionJson());
        VersionDoc toDoc = parseDoc(to.getDefinitionJson());

        // 节点 diff
        Map<String, String> fromNodes = fromDoc.nodes().stream()
                .collect(Collectors.toMap(WorkflowNodeDTO::getNodeId, this::serializeNode));
        Map<String, String> toNodes = toDoc.nodes().stream()
                .collect(Collectors.toMap(WorkflowNodeDTO::getNodeId, this::serializeNode));

        Set<String> fromNodeIds = fromNodes.keySet();
        Set<String> toNodeIds = toNodes.keySet();

        List<String> addedNodeIds = diff(toNodeIds, fromNodeIds);
        List<String> removedNodeIds = diff(fromNodeIds, toNodeIds);
        List<String> modifiedNodeIds = fromNodeIds.stream()
                .filter(toNodeIds::contains)
                .filter(id -> !fromNodes.get(id).equals(toNodes.get(id)))
                .toList();

        // 边 diff
        Set<String> fromEdgeIds = fromDoc.edges().stream().map(WorkflowEdgeDTO::getId).collect(Collectors.toSet());
        Set<String> toEdgeIds = toDoc.edges().stream().map(WorkflowEdgeDTO::getId).collect(Collectors.toSet());

        return WorkflowVersionDiffDTO.builder()
                .fromVersion(fromVersion)
                .toVersion(toVersion)
                .addedNodeIds(addedNodeIds)
                .removedNodeIds(removedNodeIds)
                .modifiedNodeIds(modifiedNodeIds)
                .addedEdgeIds(diff(toEdgeIds, fromEdgeIds))
                .removedEdgeIds(diff(fromEdgeIds, toEdgeIds))
                .build();
    }

    // ---- helpers ----

    private WorkflowDefinition requireDefinition(String workflowId, RequestContextDTO context) {
        if (context == null || context.getTenantId() == null || context.getTenantId().isBlank()) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "context.tenantId is required");
        }
        if (workflowId == null || workflowId.isBlank()) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "workflowId is required");
        }
        return workflowDefinitionRepository.findByIdAndTenantId(workflowId, context.getTenantId())
                .orElseThrow(() -> new BaseBusinessException(ErrorCode.WORKFLOW_NOT_FOUND, "workflow not found"));
    }

    private WorkflowVersion requireVersion(String workflowId, int versionNumber) {
        return workflowVersionRepository.findByWorkflowIdAndVersionNumber(workflowId, versionNumber)
                .orElseThrow(() -> new BaseBusinessException(ErrorCode.WORKFLOW_VERSION_NOT_FOUND,
                        "version " + versionNumber + " not found"));
    }

    private WorkflowVersionDTO toDto(WorkflowVersion v) {
        return WorkflowVersionDTO.builder()
                .versionId(v.getVersionId())
                .workflowId(v.getWorkflowId())
                .versionNumber(v.getVersionNumber())
                .changeSummary(v.getChangeSummary())
                .published(v.isPublished())
                .createdBy(v.getCreatedBy())
                .createdAt(v.getCreatedAt())
                .build();
    }

    private WorkflowDefinitionDTO toDefinitionDto(WorkflowDefinition definition, WorkflowVersion version) {
        VersionDoc doc = parseDoc(version.getDefinitionJson());
        return WorkflowDefinitionDTO.builder()
                .workflowId(definition.getWorkflowId())
                .workflowName(definition.getWorkflowName())
                .nodes(doc.nodes())
                .edges(doc.edges())
                .positions(doc.positions())
                .createdAt(definition.getCreatedAt())
                .updatedAt(definition.getUpdatedAt())
                .currentVersionId(definition.getCurrentVersionId())
                .publishedVersionId(definition.getPublishedVersionId())
                .build();
    }

    private VersionDoc parseDoc(String definitionJson) {
        try {
            var tree = objectMapper.readTree(definitionJson);
            List<WorkflowNodeDTO> nodes = objectMapper.convertValue(
                    tree.get("nodes"), new TypeReference<List<WorkflowNodeDTO>>() {});
            List<WorkflowEdgeDTO> edges = objectMapper.convertValue(
                    tree.get("edges"), new TypeReference<List<WorkflowEdgeDTO>>() {});
            Map<String, WorkflowPositionDTO> positions = objectMapper.convertValue(
                    tree.get("positions"), new TypeReference<Map<String, WorkflowPositionDTO>>() {});
            return new VersionDoc(
                    nodes == null ? List.of() : nodes,
                    edges == null ? List.of() : edges,
                    positions == null ? Map.of() : positions);
        } catch (JsonProcessingException e) {
            throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR, "version definition deserialize failed");
        }
    }

    private String serializeNode(WorkflowNodeDTO node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return node.getNodeId();
        }
    }

    /** 返回 a 中有但 b 中没有的元素列表 */
    private List<String> diff(Set<String> a, Set<String> b) {
        Set<String> result = new HashSet<>(a);
        result.removeAll(b);
        return new ArrayList<>(result);
    }

    private record VersionDoc(List<WorkflowNodeDTO> nodes, List<WorkflowEdgeDTO> edges,
                               Map<String, WorkflowPositionDTO> positions) {}
}
