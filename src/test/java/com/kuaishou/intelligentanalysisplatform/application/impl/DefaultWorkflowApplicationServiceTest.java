package com.kuaishou.intelligentanalysisplatform.application.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.application.WorkflowVersionApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.common.response.PageResult;
import com.kuaishou.intelligentanalysisplatform.contract.enums.NodeCategory;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RawNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SqlQueryNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowEdgeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowNodeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowPositionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowQueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowSaveRequestDTO;
import com.kuaishou.intelligentanalysisplatform.domain.workflow.WorkflowDefinition;
import com.kuaishou.intelligentanalysisplatform.domain.workflow.WorkflowDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultWorkflowApplicationServiceTest {
    @Test
    void shouldCreateAndReadWorkflow() {
        WorkflowDefinitionRepository repository = mock(WorkflowDefinitionRepository.class);
        WorkflowVersionApplicationService workflowVersionApplicationService = mock(WorkflowVersionApplicationService.class);
        DefaultWorkflowApplicationService service = new DefaultWorkflowApplicationService(
                repository, workflowVersionApplicationService, new ObjectMapper());
        WorkflowSaveRequestDTO request = buildRequest();

        WorkflowDefinitionDTO created = service.create(request);
        WorkflowDefinition stored = captureSaved(repository);
        when(repository.findByIdAndTenantId(stored.getWorkflowId(), "tenant-a")).thenReturn(Optional.of(stored));

        WorkflowDefinitionDTO loaded = service.getById(stored.getWorkflowId(), request.getContext());

        assertEquals(created.getWorkflowId(), loaded.getWorkflowId());
        assertEquals("分析流程", loaded.getWorkflowName());
        assertEquals(1, loaded.getNodes().size());
        assertEquals(1, loaded.getEdges().size());
        assertEquals(80.0, loaded.getPositions().get("node-1").getX());
        assertInstanceOf(SqlQueryNodeConfigDTO.class, loaded.getNodes().get(0).getConfig());
        verify(workflowVersionApplicationService, never()).snapshot(any(), any(), any());
    }

    @Test
    void shouldSerializeAndListWorkflow() {
        WorkflowDefinitionRepository repository = mock(WorkflowDefinitionRepository.class);
        WorkflowVersionApplicationService workflowVersionApplicationService = mock(WorkflowVersionApplicationService.class);
        DefaultWorkflowApplicationService service = new DefaultWorkflowApplicationService(
                repository, workflowVersionApplicationService, new ObjectMapper());
        WorkflowSaveRequestDTO request = buildRequest();

        service.create(request);
        WorkflowDefinition stored = captureSaved(repository);
        when(repository.findByTenantId("tenant-a", 0, 20)).thenReturn(List.of(stored));
        when(repository.countByTenantId("tenant-a")).thenReturn(1L);

        PageResult<WorkflowDefinitionDTO> pageResult = service.list(WorkflowQueryRequestDTO.builder()
                .context(request.getContext())
                .build());

        assertEquals(1, pageResult.getItems().size());
        assertEquals("分析流程", pageResult.getItems().get(0).getWorkflowName());
    }

    @Test
    void shouldUpdateWorkflow() {
        WorkflowDefinitionRepository repository = mock(WorkflowDefinitionRepository.class);
        WorkflowVersionApplicationService workflowVersionApplicationService = mock(WorkflowVersionApplicationService.class);
        DefaultWorkflowApplicationService service = new DefaultWorkflowApplicationService(
                repository, workflowVersionApplicationService, new ObjectMapper());
        WorkflowSaveRequestDTO request = buildRequest();

        service.create(request);
        WorkflowDefinition stored = captureSaved(repository);
        WorkflowDefinition updatedStored = WorkflowDefinition.builder()
                .workflowId(stored.getWorkflowId())
                .tenantId(stored.getTenantId())
                .workflowName("分析流程")
                .definitionJson(stored.getDefinitionJson())
                .operatorId(stored.getOperatorId())
                .createdAt(stored.getCreatedAt())
                .updatedAt(stored.getUpdatedAt() + 1)
                .currentVersionId(stored.getCurrentVersionId())
                .publishedVersionId(stored.getPublishedVersionId())
                .build();
        when(repository.findByIdAndTenantId(stored.getWorkflowId(), "tenant-a"))
                .thenReturn(Optional.of(stored))
                .thenReturn(Optional.of(updatedStored));

        WorkflowDefinitionDTO updated = service.update(stored.getWorkflowId(), buildRequest());

        verify(repository).update(any(WorkflowDefinition.class));
        verify(workflowVersionApplicationService).snapshot(eq(stored.getWorkflowId()), eq("auto-save"), eq(request.getContext()));
        assertEquals("分析流程", updated.getWorkflowName());
        assertInstanceOf(SqlQueryNodeConfigDTO.class, updated.getNodes().get(0).getConfig());
    }

    @Test
    void shouldReadWorkflowWithUnknownNodeType() {
        WorkflowDefinitionRepository repository = mock(WorkflowDefinitionRepository.class);
        WorkflowVersionApplicationService workflowVersionApplicationService = mock(WorkflowVersionApplicationService.class);
        DefaultWorkflowApplicationService service = new DefaultWorkflowApplicationService(
                repository, workflowVersionApplicationService, new ObjectMapper());
        WorkflowDefinition stored = WorkflowDefinition.builder()
                .workflowId("wf-legacy")
                .tenantId("tenant-a")
                .workflowName("遗留流程")
                .definitionJson("""
                        {
                          "nodes": [
                            {
                              "nodeId": "legacy-node",
                              "nodeType": "approval_gate",
                              "category": "GOVERNANCE",
                              "version": "v1",
                              "config": {
                                "title": "审批节点",
                                "approverGroup": "ops"
                              }
                            }
                          ],
                          "edges": [],
                          "positions": {}
                        }
                        """)
                .operatorId("user-a")
                .createdAt(1L)
                .updatedAt(2L)
                .build();
        when(repository.findByIdAndTenantId("wf-legacy", "tenant-a")).thenReturn(Optional.of(stored));

        WorkflowDefinitionDTO loaded = assertDoesNotThrow(() -> service.getById("wf-legacy",
                RequestContextDTO.builder().tenantId("tenant-a").userId("user-a").build()));

        assertEquals(1, loaded.getNodes().size());
        assertInstanceOf(RawNodeConfigDTO.class, loaded.getNodes().get(0).getConfig());
        RawNodeConfigDTO config = (RawNodeConfigDTO) loaded.getNodes().get(0).getConfig();
        assertEquals("ops", config.getExtraProperties().get("approverGroup"));
    }

    @Test
    void shouldRejectUnknownWorkflow() {
        WorkflowDefinitionRepository repository = mock(WorkflowDefinitionRepository.class);
        WorkflowVersionApplicationService workflowVersionApplicationService = mock(WorkflowVersionApplicationService.class);
        DefaultWorkflowApplicationService service = new DefaultWorkflowApplicationService(
                repository, workflowVersionApplicationService, new ObjectMapper());
        when(repository.findByIdAndTenantId("wf-404", "tenant-a")).thenReturn(Optional.empty());
        BaseBusinessException exception = assertThrows(BaseBusinessException.class,
                () -> service.getById("wf-404", RequestContextDTO.builder().tenantId("tenant-a").userId("user-a").build()));
        assertEquals(ErrorCode.WORKFLOW_NOT_FOUND, exception.getErrorCode());
    }

    private WorkflowDefinition captureSaved(WorkflowDefinitionRepository repository) {
        ArgumentCaptor<WorkflowDefinition> captor = ArgumentCaptor.forClass(WorkflowDefinition.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private WorkflowSaveRequestDTO buildRequest() {
        return WorkflowSaveRequestDTO.builder()
                .workflowName("分析流程")
                .nodes(List.of(WorkflowNodeDTO.builder()
                        .nodeId("node-1")
                        .nodeType("sql_query")
                        .category(NodeCategory.QUERY)
                        .version("v1")
                        .metadata(null)
                        .config(SqlQueryNodeConfigDTO.builder()
                                .datasourceId("ds-1")
                                .sqlTemplate("select 1")
                                .build())
                        .build()))
                .edges(List.of(WorkflowEdgeDTO.builder()
                        .id("edge-1")
                        .source("node-1")
                        .target("node-2")
                        .build()))
                .positions(Map.of("node-1", WorkflowPositionDTO.builder().x(80.0).y(120.0).build()))
                .context(RequestContextDTO.builder().tenantId("tenant-a").userId("user-a").build())
                .build();
    }
}
