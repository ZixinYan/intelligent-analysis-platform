package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeDatasetResolver;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeResultFactory;
import com.kuaishou.intelligentanalysisplatform.application.security.FieldMasker;
import com.kuaishou.intelligentanalysisplatform.contract.enums.MaskingStrategy;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.MaskingNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.MaskingRuleDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.VariableRefDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MaskingNodeExecutorTest {

    private final MaskingNodeExecutor executor = new MaskingNodeExecutor(
            mock(NodeMetadataApplicationService.class),
            new ComputeDatasetResolver(),
            new ComputeResultFactory(),
            new FieldMasker()
    );

    private NodeExecuteContextDTO contextWithDataset(DatasetDTO dataset) {
        return NodeExecuteContextDTO.builder()
                .nodeId("masking1")
                .requestContext(RequestContextDTO.builder().tenantId("t1").build())
                .upstreamResults(Map.of("upstream1", StandardResultDTO.builder()
                        .kind(ResultKind.DATASET)
                        .dataset(dataset)
                        .build()))
                .build();
    }

    @Test
    void maskedFieldsAreTransformed_unmaskFieldsPreserved() {
        DatasetDTO input = DatasetDTO.builder().rows(List.of(
                Map.of("phone", "13812345678", "name", "Alice"),
                Map.of("phone", "13987654321", "name", "Bob")
        )).build();

        var result = executor.execute(contextWithDataset(input), MaskingNodeConfigDTO.builder()
                .datasetRef(VariableRefDTO.builder().sourceNodeId("upstream1").build())
                .rules(List.of(MaskingRuleDTO.builder()
                        .fieldName("phone")
                        .strategy(MaskingStrategy.PARTIAL)
                        .build()))
                .build());

        var rows = result.getResult().getDataset().getRows();
        assertEquals(2, rows.size());
        assertEquals("138****5678", rows.get(0).get("phone"));
        assertEquals("Alice", rows.get(0).get("name")); // unchanged
        assertEquals("139****4321", rows.get(1).get("phone"));
    }

    @Test
    void nullOutStrategy_setsFieldToNull() {
        DatasetDTO input = DatasetDTO.builder().rows(List.of(
                Map.of("secret", "sensitive_value", "id", "1")
        )).build();

        var result = executor.execute(contextWithDataset(input), MaskingNodeConfigDTO.builder()
                .datasetRef(VariableRefDTO.builder().sourceNodeId("upstream1").build())
                .rules(List.of(MaskingRuleDTO.builder()
                        .fieldName("secret")
                        .strategy(MaskingStrategy.NULL_OUT)
                        .build()))
                .build());

        var rows = result.getResult().getDataset().getRows();
        assertNull(rows.get(0).get("secret"));
        assertEquals("1", rows.get(0).get("id"));
    }

    @Test
    void hashStrategy_producesConsistentHash() {
        DatasetDTO input = DatasetDTO.builder().rows(List.of(
                Map.of("user_id", "abc123")
        )).build();

        var result = executor.execute(contextWithDataset(input), MaskingNodeConfigDTO.builder()
                .datasetRef(VariableRefDTO.builder().sourceNodeId("upstream1").build())
                .rules(List.of(MaskingRuleDTO.builder()
                        .fieldName("user_id")
                        .strategy(MaskingStrategy.HASH)
                        .build()))
                .build());

        var maskedId = result.getResult().getDataset().getRows().get(0).get("user_id").toString();
        assertNotEquals("abc123", maskedId);
        assertEquals(64, maskedId.length());
    }

    @Test
    void validate_missingDatasetRef_returnsInvalid() {
        ValidationResultDTO v = executor.validate(MaskingNodeConfigDTO.builder()
                .rules(List.of(MaskingRuleDTO.builder().fieldName("f").strategy(MaskingStrategy.NULL_OUT).build()))
                .build());
        assertFalse(v.isValid());
    }

    @Test
    void validate_emptyRules_returnsInvalid() {
        ValidationResultDTO v = executor.validate(MaskingNodeConfigDTO.builder()
                .datasetRef(VariableRefDTO.builder().sourceNodeId("n1").build())
                .rules(List.of())
                .build());
        assertFalse(v.isValid());
    }

    @Test
    void validate_regexWithoutPattern_returnsInvalid() {
        ValidationResultDTO v = executor.validate(MaskingNodeConfigDTO.builder()
                .datasetRef(VariableRefDTO.builder().sourceNodeId("n1").build())
                .rules(List.of(MaskingRuleDTO.builder()
                        .fieldName("f")
                        .strategy(MaskingStrategy.REGEX_REPLACE)
                        .build()))
                .build());
        assertFalse(v.isValid());
    }

    @Test
    void validate_validConfig_returnsValid() {
        ValidationResultDTO v = executor.validate(MaskingNodeConfigDTO.builder()
                .datasetRef(VariableRefDTO.builder().sourceNodeId("n1").build())
                .rules(List.of(MaskingRuleDTO.builder()
                        .fieldName("phone")
                        .strategy(MaskingStrategy.PARTIAL)
                        .build()))
                .build());
        assertTrue(v.isValid());
    }

    @Test
    void auditReflectsRules() {
        DatasetDTO input = DatasetDTO.builder().rows(List.of(Map.of("phone", "13812345678"))).build();

        var result = executor.execute(contextWithDataset(input), MaskingNodeConfigDTO.builder()
                .datasetRef(VariableRefDTO.builder().sourceNodeId("upstream1").build())
                .rules(List.of(MaskingRuleDTO.builder().fieldName("phone").strategy(MaskingStrategy.PARTIAL).build()))
                .build());

        assertEquals("masking", result.getMeta().getAudit().getCapabilityType());
        assertEquals("masking", result.getMeta().getAudit().getSteps().get(0).getStepName());
    }
}
