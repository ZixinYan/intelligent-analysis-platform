package com.kuaishou.intelligentanalysisplatform.application.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.application.WorkflowApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.common.response.PageResult;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowEdgeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowNodeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowPositionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowQueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowSaveRequestDTO;
import com.kuaishou.intelligentanalysisplatform.domain.workflow.WorkflowDefinition;
import com.kuaishou.intelligentanalysisplatform.domain.workflow.WorkflowDefinitionRepository;
import org.springframework.stereotype.Service;

@Service
public class DefaultWorkflowApplicationService implements WorkflowApplicationService {
    private static final Class<WorkflowDocument> WORKFLOW_DOCUMENT_CLASS = WorkflowDocument.class;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final ObjectMapper objectMapper;

    public DefaultWorkflowApplicationService(WorkflowDefinitionRepository workflowDefinitionRepository,
                                             ObjectMapper objectMapper) {
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public WorkflowDefinitionDTO create(WorkflowSaveRequestDTO request) {
        validateRequest(request);
        long now = System.currentTimeMillis();
        WorkflowDefinition definition = WorkflowDefinition.builder()
                .workflowId(UUID.randomUUID().toString())
                .tenantId(request.getContext().getTenantId())
                .workflowName(request.getWorkflowName().trim())
                .definitionJson(toDefinitionJson(request))
                .operatorId(request.getContext().getUserId())
                .createdAt(now)
                .updatedAt(now)
                .build();
        workflowDefinitionRepository.save(definition);
        return toDto(definition);
    }

    @Override
    public WorkflowDefinitionDTO update(String workflowId, WorkflowSaveRequestDTO request) {
        validateRequest(request);
        WorkflowDefinition current = getDefinition(workflowId, request.getContext());
        WorkflowDefinition definition = WorkflowDefinition.builder()
                .workflowId(current.getWorkflowId())
                .tenantId(current.getTenantId())
                .workflowName(request.getWorkflowName().trim())
                .definitionJson(toDefinitionJson(request))
                .operatorId(request.getContext().getUserId())
                .createdAt(current.getCreatedAt())
                .updatedAt(System.currentTimeMillis())
                .build();
        workflowDefinitionRepository.update(definition);
        return toDto(definition);
    }

    @Override
    public WorkflowDefinitionDTO getById(String workflowId, RequestContextDTO context) {
        return toDto(getDefinition(workflowId, context));
    }

    @Override
    public PageResult<WorkflowDefinitionDTO> list(WorkflowQueryRequestDTO request) {
        RequestContextDTO context = requireContext(request == null ? null : request.getContext());
        int page = request.getPage() == null || request.getPage() < 1 ? 1 : request.getPage();
        int pageSize = request.getPageSize() == null || request.getPageSize() < 1 ? 20 : request.getPageSize();
        int offset = (page - 1) * pageSize;
        List<WorkflowDefinitionDTO> items = workflowDefinitionRepository.findByTenantId(context.getTenantId(), offset, pageSize)
                .stream()
                .map(this::toDto)
                .toList();
        return PageResult.<WorkflowDefinitionDTO>builder()
                .items(items)
                .total(workflowDefinitionRepository.countByTenantId(context.getTenantId()))
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    private WorkflowDefinition getDefinition(String workflowId, RequestContextDTO context) {
        RequestContextDTO requestContext = requireContext(context);
        if (workflowId == null || workflowId.isBlank()) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "workflowId is required");
        }
        return workflowDefinitionRepository.findByIdAndTenantId(workflowId, requestContext.getTenantId())
                .orElseThrow(() -> new BaseBusinessException(ErrorCode.WORKFLOW_NOT_FOUND, "workflow not found"));
    }

    private WorkflowDefinitionDTO toDto(WorkflowDefinition definition) {
        WorkflowDocument document = fromDefinitionJson(definition.getDefinitionJson());
        return WorkflowDefinitionDTO.builder()
                .workflowId(definition.getWorkflowId())
                .workflowName(definition.getWorkflowName())
                .nodes(document.getNodes())
                .edges(document.getEdges())
                .positions(document.getPositions())
                .createdAt(definition.getCreatedAt())
                .updatedAt(definition.getUpdatedAt())
                .build();
    }

    private String toDefinitionJson(WorkflowSaveRequestDTO request) {
        try {
            return objectMapper.writeValueAsString(WorkflowDocument.builder()
                    .nodes(request.getNodes() == null ? List.of() : request.getNodes())
                    .edges(request.getEdges() == null ? List.of() : request.getEdges())
                    .positions(request.getPositions() == null ? Map.of() : request.getPositions())
                    .build());
        } catch (JsonProcessingException exception) {
            throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR, "workflow serialize failed");
        }
    }

    private WorkflowDocument fromDefinitionJson(String definitionJson) {
        try {
            return objectMapper.readValue(definitionJson, WORKFLOW_DOCUMENT_CLASS);
        } catch (JsonProcessingException exception) {
            throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR, "workflow deserialize failed");
        }
    }

    private void validateRequest(WorkflowSaveRequestDTO request) {
        RequestContextDTO context = requireContext(request == null ? null : request.getContext());
        if (request.getWorkflowName() == null || request.getWorkflowName().isBlank()) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "workflowName is required");
        }
        if (context.getTenantId() == null || context.getTenantId().isBlank()) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "tenantId is required");
        }
    }

    private RequestContextDTO requireContext(RequestContextDTO context) {
        if (context == null) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "context is required");
        }
        return context;
    }

    private static class WorkflowDocument {
        private List<WorkflowNodeDTO> nodes;
        private List<WorkflowEdgeDTO> edges;
        private Map<String, WorkflowPositionDTO> positions;

        public static WorkflowDocumentBuilder builder() {
            return new WorkflowDocumentBuilder();
        }

        public List<WorkflowNodeDTO> getNodes() {
            return nodes == null ? List.of() : nodes;
        }

        public void setNodes(List<WorkflowNodeDTO> nodes) {
            this.nodes = nodes;
        }

        public List<WorkflowEdgeDTO> getEdges() {
            return edges == null ? List.of() : edges;
        }

        public void setEdges(List<WorkflowEdgeDTO> edges) {
            this.edges = edges;
        }

        public Map<String, WorkflowPositionDTO> getPositions() {
            return positions == null ? Map.of() : positions;
        }

        public void setPositions(Map<String, WorkflowPositionDTO> positions) {
            this.positions = positions;
        }
    }

    private static class WorkflowDocumentBuilder {
        private final WorkflowDocument document = new WorkflowDocument();

        public WorkflowDocumentBuilder nodes(List<WorkflowNodeDTO> nodes) {
            document.setNodes(nodes);
            return this;
        }

        public WorkflowDocumentBuilder edges(List<WorkflowEdgeDTO> edges) {
            document.setEdges(edges);
            return this;
        }

        public WorkflowDocumentBuilder positions(Map<String, WorkflowPositionDTO> positions) {
            document.setPositions(positions);
            return this;
        }

        public WorkflowDocument build() {
            return document;
        }
    }
}
