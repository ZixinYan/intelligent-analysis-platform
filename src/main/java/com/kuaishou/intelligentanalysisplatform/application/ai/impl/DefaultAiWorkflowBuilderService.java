package com.kuaishou.intelligentanalysisplatform.application.ai.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.application.DatasourceApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.AiProviderClient;
import com.kuaishou.intelligentanalysisplatform.application.ai.AiWorkflowBuilderService;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RawNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowEdgeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowNodeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowPositionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowBuildRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.TableSchemaDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultAiWorkflowBuilderService implements AiWorkflowBuilderService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiWorkflowBuilderService.class);

    private final AiProviderClient aiProviderClient;
    private final DatasourceApplicationService datasourceService;
    private final ObjectMapper objectMapper;

    public DefaultAiWorkflowBuilderService(AiProviderClient aiProviderClient,
                                           DatasourceApplicationService datasourceService,
                                           ObjectMapper objectMapper) {
        this.aiProviderClient = aiProviderClient;
        this.datasourceService = datasourceService;
        this.objectMapper = objectMapper;
    }

    @Override
    public WorkflowDefinitionDTO buildDraft(AiWorkflowBuildRequestDTO request, RequestContextDTO context) {
        // 1. 获取所有表的 Schema
        List<TableSchemaDTO> schemas = datasourceService.introspectAllTableSchemas(
                request.getDatasourceId(), context);

        // 2. 构建 Schema 描述字符串
        String schemaDesc = schemas.stream()
                .map(t -> "Table: " + t.getTableName() + "\n" +
                        t.getFields().stream()
                                .map(f -> "  - " + f.getName() + " (" + (f.getValueType() != null ? f.getValueType().name() : "?") + ")")
                                .collect(Collectors.joining("\n")))
                .collect(Collectors.joining("\n\n"));

        // 3. 调用 LLM 生成工作流 JSON
        String systemPrompt = PromptLoader.load("workflow-build.txt", Map.of(
                "SCHEMA", schemaDesc,
                "DESCRIPTION", request.getDescription(),
                "DATASOURCE_ID", request.getDatasourceId()
        ));
        String response = aiProviderClient.completion("", systemPrompt);

        // 4. 解析 LLM 响应为 WorkflowDefinitionDTO
        return parseWorkflowDraft(response, request);
    }

    private WorkflowDefinitionDTO parseWorkflowDraft(String llmResponse, AiWorkflowBuildRequestDTO request) {
        try {
            String json = LlmResponseParser.extractJson(llmResponse);
            JsonNode root = objectMapper.readTree(json);

            String workflowName = request.getWorkflowName() != null && !request.getWorkflowName().isBlank()
                    ? request.getWorkflowName()
                    : root.path("workflowName").asText("AI 生成工作流");

            List<WorkflowNodeDTO> nodes = parseNodes(root.path("nodes"));
            List<WorkflowEdgeDTO> edges = parseEdges(root.path("edges"));
            Map<String, WorkflowPositionDTO> positions = parsePositions(root.path("positions"), nodes);

            return WorkflowDefinitionDTO.builder()
                    .workflowId(UUID.randomUUID().toString())
                    .workflowName(workflowName)
                    .nodes(nodes)
                    .edges(edges)
                    .positions(positions)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse LLM workflow response, returning minimal draft", e);
            return buildFallbackDraft(request);
        }
    }

    private List<WorkflowNodeDTO> parseNodes(JsonNode nodesJson) {
        if (nodesJson == null || nodesJson.isMissingNode() || !nodesJson.isArray()) return List.of();
        List<WorkflowNodeDTO> nodes = new java.util.ArrayList<>();
        for (JsonNode nodeJson : nodesJson) {
            String nodeId = nodeJson.path("nodeId").asText(UUID.randomUUID().toString());
            String nodeType = nodeJson.path("nodeType").asText("sql_query");
            JsonNode configJson = nodeJson.path("config");
            RawNodeConfigDTO config = new RawNodeConfigDTO();
            if (configJson.isObject()) {
                configJson.fields().forEachRemaining(e -> config.putExtraProperty(e.getKey(),
                        e.getValue().isTextual() ? e.getValue().asText() : e.getValue()));
            }
            nodes.add(WorkflowNodeDTO.builder()
                    .nodeId(nodeId)
                    .nodeType(nodeType)
                    .config(config)
                    .build());
        }
        return nodes;
    }

    private List<WorkflowEdgeDTO> parseEdges(JsonNode edgesJson) {
        if (edgesJson == null || edgesJson.isMissingNode() || !edgesJson.isArray()) return List.of();
        List<WorkflowEdgeDTO> edges = new java.util.ArrayList<>();
        for (JsonNode edgeJson : edgesJson) {
            edges.add(WorkflowEdgeDTO.builder()
                    .id(edgeJson.path("id").asText(UUID.randomUUID().toString()))
                    .source(edgeJson.path("source").asText())
                    .target(edgeJson.path("target").asText())
                    .build());
        }
        return edges;
    }

    private Map<String, WorkflowPositionDTO> parsePositions(JsonNode positionsJson, List<WorkflowNodeDTO> nodes) {
        Map<String, WorkflowPositionDTO> positions = new java.util.LinkedHashMap<>();
        if (positionsJson != null && positionsJson.isObject()) {
            positionsJson.fields().forEachRemaining(entry -> {
                JsonNode pos = entry.getValue();
                positions.put(entry.getKey(), WorkflowPositionDTO.builder()
                        .x(pos.path("x").asDouble(100))
                        .y(pos.path("y").asDouble(150))
                        .build());
            });
        }
        // Ensure all nodes have positions
        for (int i = 0; i < nodes.size(); i++) {
            String nodeId = nodes.get(i).getNodeId();
            if (!positions.containsKey(nodeId)) {
                positions.put(nodeId, WorkflowPositionDTO.builder()
                        .x(100 + i * 350.0)
                        .y(Double.valueOf(150))
                        .build());
            }
        }
        return positions;
    }

    private WorkflowDefinitionDTO buildFallbackDraft(AiWorkflowBuildRequestDTO request) {
        String nodeId = "sql_query-" + UUID.randomUUID().toString().substring(0, 6);
        RawNodeConfigDTO config = new RawNodeConfigDTO();
        config.putExtraProperty("datasourceId", request.getDatasourceId());
        config.putExtraProperty("sql", "-- AI 生成失败，请手动填写 SQL");
        WorkflowNodeDTO sqlNode = WorkflowNodeDTO.builder()
                .nodeId(nodeId)
                .nodeType("sql_query")
                .config(config)
                .build();
        return WorkflowDefinitionDTO.builder()
                .workflowId(UUID.randomUUID().toString())
                .workflowName(request.getWorkflowName() != null ? request.getWorkflowName() : "AI 草稿工作流")
                .nodes(List.of(sqlNode))
                .edges(List.of())
                .positions(Map.of(nodeId, WorkflowPositionDTO.builder().x(100.0).y(150.0).build()))
                .build();
    }
}
