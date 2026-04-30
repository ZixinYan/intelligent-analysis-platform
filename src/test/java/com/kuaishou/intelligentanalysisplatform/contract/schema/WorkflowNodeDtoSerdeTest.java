package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowNodeDtoSerdeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeSqlQueryConfigByNodeType() throws Exception {
        String json = """
                {
                  "nodeId": "node-1",
                  "nodeType": "sql_query",
                  "category": "QUERY",
                  "version": "v1",
                  "config": {
                    "title": "SQL节点",
                    "datasourceId": "ds-1",
                    "sqlTemplate": "select 1"
                  }
                }
                """;

        WorkflowNodeDTO dto = objectMapper.readValue(json, WorkflowNodeDTO.class);

        assertInstanceOf(SqlQueryNodeConfigDTO.class, dto.getConfig());
        SqlQueryNodeConfigDTO config = (SqlQueryNodeConfigDTO) dto.getConfig();
        assertEquals("ds-1", config.getDatasourceId());
        assertEquals("select 1", config.getSqlTemplate());
    }

    @Test
    void shouldDeserializeAggregateConfigByNodeType() throws Exception {
        String json = """
                {
                  "nodeId": "node-2",
                  "nodeType": "aggregate",
                  "category": "COMPUTE",
                  "version": "v1",
                  "config": {
                    "groupByFields": ["product"],
                    "metrics": [{"field": "amount", "agg": "SUM", "alias": "total_amount"}]
                  }
                }
                """;

        WorkflowNodeDTO dto = objectMapper.readValue(json, WorkflowNodeDTO.class);
        assertInstanceOf(AggregateNodeConfigDTO.class, dto.getConfig());
    }

    @Test
    void shouldDeserializeTimeSeriesConfigByNodeType() throws Exception {
        String json = """
                {
                  "nodeId": "node-3",
                  "nodeType": "time_series_compute",
                  "category": "COMPUTE",
                  "version": "v1",
                  "config": {
                    "timeField": "dt",
                    "granularity": "MONTH",
                    "metrics": [{"metricField": "amount", "computeType": "MOM", "alias": "amount_mom"}]
                  }
                }
                """;

        WorkflowNodeDTO dto = objectMapper.readValue(json, WorkflowNodeDTO.class);
        assertInstanceOf(TimeSeriesComputeNodeConfigDTO.class, dto.getConfig());
    }

    @Test
    void shouldDeserializeOtherComputeConfigsByNodeType() throws Exception {
        assertInstanceOf(PivotNodeConfigDTO.class, objectMapper.readValue("""
                {"nodeId":"node-4","nodeType":"pivot","category":"COMPUTE","version":"v1","config":{"columnField":"month","valueField":"amount"}}
                """, WorkflowNodeDTO.class).getConfig());
        assertInstanceOf(FilterNodeConfigDTO.class, objectMapper.readValue("""
                {"nodeId":"node-5","nodeType":"filter","category":"COMPUTE","version":"v1","config":{"conditions":[]}}
                """, WorkflowNodeDTO.class).getConfig());
        assertInstanceOf(SortNodeConfigDTO.class, objectMapper.readValue("""
                {"nodeId":"node-6","nodeType":"sort","category":"COMPUTE","version":"v1","config":{"sortFields":[]}}
                """, WorkflowNodeDTO.class).getConfig());
        assertInstanceOf(FormulaNodeConfigDTO.class, objectMapper.readValue("""
                {"nodeId":"node-7","nodeType":"formula","category":"COMPUTE","version":"v1","config":{"formulas":[]}}
                """, WorkflowNodeDTO.class).getConfig());
    }

    @Test
    void shouldFallbackToRawConfigForUnknownNodeType() throws Exception {
        String json = """
                {
                  "nodeId": "node-8",
                  "nodeType": "approval_gate",
                  "category": "GOVERNANCE",
                  "version": "v1",
                  "config": {
                    "title": "审批节点",
                    "approverGroup": "ops-team",
                    "requireAll": true
                  }
                }
                """;

        WorkflowNodeDTO dto = objectMapper.readValue(json, WorkflowNodeDTO.class);

        assertInstanceOf(RawNodeConfigDTO.class, dto.getConfig());
        RawNodeConfigDTO config = (RawNodeConfigDTO) dto.getConfig();
        assertEquals("审批节点", config.getTitle());
        assertEquals("ops-team", config.getExtraProperties().get("approverGroup"));
        assertEquals(Boolean.TRUE, config.getExtraProperties().get("requireAll"));
    }

    @Test
    void shouldRoundTripUnknownConfigWithoutDataLoss() throws Exception {
        String originalJson = """
                {
                  "nodeId": "node-9",
                  "nodeType": "future_node",
                  "category": "COMPUTE",
                  "version": "v1",
                  "config": {
                    "title": "未来节点",
                    "futureField": "future-value",
                    "futureSwitch": true
                  }
                }
                """;

        WorkflowNodeDTO dto = objectMapper.readValue(originalJson, WorkflowNodeDTO.class);
        String serialized = objectMapper.writeValueAsString(dto);
        WorkflowNodeDTO roundTrip = objectMapper.readValue(serialized, WorkflowNodeDTO.class);

        assertInstanceOf(RawNodeConfigDTO.class, roundTrip.getConfig());
        RawNodeConfigDTO config = (RawNodeConfigDTO) roundTrip.getConfig();
        assertEquals("future-value", config.getExtraProperties().get("futureField"));
        assertEquals(Boolean.TRUE, config.getExtraProperties().get("futureSwitch"));
        assertTrue(serialized.contains("futureField"));
    }
}
