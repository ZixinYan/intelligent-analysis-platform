package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeCapabilityRegistry;
import com.kuaishou.intelligentanalysisplatform.common.response.GlobalExceptionHandler;
import com.kuaishou.intelligentanalysisplatform.infra.stub.StubNodeMetadataApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NodeDefinitionControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NodeDefinitionController(new StubNodeMetadataApplicationService(), new ComputeCapabilityRegistry(new ObjectMapper())))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldListNodeDefinitions() throws Exception {
        mockMvc.perform(get("/api/v1/node-definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].nodeType").value("sql_query"));
    }

    @Test
    void shouldReturnSqlQueryDefinitionWithPanelSchema() throws Exception {
        mockMvc.perform(get("/api/v1/node-definitions/sql_query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.protocolVersion").value("1.0"))
                .andExpect(jsonPath("$.data.configSchema.panelId").value("analysis.sql-query"))
                .andExpect(jsonPath("$.data.configSchema.sections[0].fields[0].field").value("datasourceId"))
                .andExpect(jsonPath("$.data.configSchema.sections[0].fields[1].field").value("sqlTemplate"))
                .andExpect(jsonPath("$.data.configSchema.sections[1].fields[0].field").value("timeoutMs"));
    }

    @Test
    void shouldReturnChartOutputDefinitionWithPanelSchema() throws Exception {
        mockMvc.perform(get("/api/v1/node-definitions/chart_output"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configSchema.panelId").value("analysis.chart-output"))
                .andExpect(jsonPath("$.data.configSchema.sections[0].fields[0].field").value("chartType"))
                .andExpect(jsonPath("$.data.configSchema.sections[0].fields[2].field").value("xField"))
                .andExpect(jsonPath("$.data.configSchema.sections[0].fields[3].field").value("yField"));
    }

    @Test
    void shouldReturnTableOutputDefinitionWithPanelSchema() throws Exception {
        mockMvc.perform(get("/api/v1/node-definitions/table_output"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configSchema.panelId").value("analysis.table-output"))
                .andExpect(jsonPath("$.data.configSchema.sections[0].fields[1].field").value("columns"))
                .andExpect(jsonPath("$.data.configSchema.sections[0].fields[3].field").value("pageSize"));
    }

    @Test
    void shouldReturnSchemaInferForSqlQuery() throws Exception {
        mockMvc.perform(get("/api/v1/node-definitions/sql_query/schema-infer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schemaId").value("schema_sql_query_demo"))
                .andExpect(jsonPath("$.data.fields[0].fieldId").value("order_date"))
                .andExpect(jsonPath("$.data.mappingHints.chart.xCandidates[0]").value("order_date"));
    }

    @Test
    void shouldReturnLineChartMappingCandidates() throws Exception {
        mockMvc.perform(get("/api/v1/node-definitions/chart_output/mapping-candidates").param("renderer", "line"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].slot").value("xField"))
                .andExpect(jsonPath("$.data[1].slot").value("yField"))
                .andExpect(jsonPath("$.data[1].candidates[0].fieldId").value("sales_amount"));
    }

    @Test
    void shouldReturnPieChartMappingCandidates() throws Exception {
        mockMvc.perform(get("/api/v1/node-definitions/chart_output/mapping-candidates").param("renderer", "pie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].slot").value("categoryField"))
                .andExpect(jsonPath("$.data[1].slot").value("valueField"));
    }

    @Test
    void shouldReturnComputeCapabilities() throws Exception {
        mockMvc.perform(get("/api/v1/node-definitions/compute-capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("aggregate"));
    }

    @Test
    void shouldReturnFormulaCapability() throws Exception {
        mockMvc.perform(get("/api/v1/node-definitions/compute-capabilities/formula"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("formula"))
                .andExpect(jsonPath("$.data.params.derivedMetric").value(true));
    }

    @Test
    void shouldRequireRendererForChartOutput() throws Exception {
        mockMvc.perform(get("/api/v1/node-definitions/chart_output/mapping-candidates").param("renderer", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void shouldRejectUnsupportedRendererForChartOutput() throws Exception {
        mockMvc.perform(get("/api/v1/node-definitions/chart_output/mapping-candidates").param("renderer", "radar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void shouldRejectUnsupportedRendererForTableOutput() throws Exception {
        mockMvc.perform(get("/api/v1/node-definitions/table_output/mapping-candidates").param("renderer", "line"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void shouldReturnNotFoundWhenNodeDefinitionMissing() throws Exception {
        mockMvc.perform(get("/api/v1/node-definitions/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NODE_NOT_FOUND"));
    }

    @Test
    void shouldReturnNotFoundWhenCapabilityMissing() throws Exception {
        mockMvc.perform(get("/api/v1/node-definitions/compute-capabilities/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NODE_NOT_FOUND"));
    }
}
