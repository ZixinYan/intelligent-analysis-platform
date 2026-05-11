package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeDatasetResolver;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeResultFactory;
import com.kuaishou.intelligentanalysisplatform.application.security.FieldMasker;
import com.kuaishou.intelligentanalysisplatform.contract.enums.NodeType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ComputeAuditDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ComputeStepDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.MaskingNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.MaskingRuleDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.enums.MaskingStrategy;
import org.springframework.stereotype.Component;

@Component
public class MaskingNodeExecutor implements NodeExecutor<MaskingNodeConfigDTO> {

    private final NodeMetadataApplicationService nodeMetadataApplicationService;
    private final ComputeDatasetResolver computeDatasetResolver;
    private final ComputeResultFactory computeResultFactory;
    private final FieldMasker fieldMasker;

    public MaskingNodeExecutor(NodeMetadataApplicationService nodeMetadataApplicationService,
                               ComputeDatasetResolver computeDatasetResolver,
                               ComputeResultFactory computeResultFactory,
                               FieldMasker fieldMasker) {
        this.nodeMetadataApplicationService = nodeMetadataApplicationService;
        this.computeDatasetResolver = computeDatasetResolver;
        this.computeResultFactory = computeResultFactory;
        this.fieldMasker = fieldMasker;
    }

    @Override
    public String supportType() {
        return NodeType.MASKING.getCode();
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO context, MaskingNodeConfigDTO config) {
        long start = System.currentTimeMillis();
        DatasetDTO input = computeDatasetResolver.resolve(config.getDatasetRef(), context.getUpstreamResults());
        DatasetDTO output = applyMasking(input, config.getRules());
        return computeResultFactory.success(
                context.getNodeId(),
                supportType(),
                output,
                false,
                supportType(),
                System.currentTimeMillis() - start,
                buildAudit(config, input, output)
        );
    }

    @Override
    public ValidationResultDTO validate(MaskingNodeConfigDTO config) {
        if (config == null || config.getDatasetRef() == null) {
            return ValidationResultDTO.builder().valid(false).errorMessage("datasetRef is required").build();
        }
        if (config.getRules() == null || config.getRules().isEmpty()) {
            return ValidationResultDTO.builder().valid(false).errorMessage("at least one masking rule is required").build();
        }
        for (MaskingRuleDTO rule : config.getRules()) {
            if (rule.getFieldName() == null || rule.getFieldName().isBlank()) {
                return ValidationResultDTO.builder().valid(false).errorMessage("masking rule fieldName cannot be blank").build();
            }
            if (rule.getStrategy() == null) {
                return ValidationResultDTO.builder().valid(false)
                        .errorMessage("masking strategy is required for field: " + rule.getFieldName()).build();
            }
            if (rule.getStrategy() == MaskingStrategy.REGEX_REPLACE && rule.getRegexPattern() == null) {
                return ValidationResultDTO.builder().valid(false)
                        .errorMessage("regexPattern is required for REGEX_REPLACE strategy").build();
            }
        }
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public NodeMetaDTO metadata() {
        return nodeMetadataApplicationService.getNodeDefinition(supportType());
    }

    private DatasetDTO applyMasking(DatasetDTO input, List<MaskingRuleDTO> rules) {
        if (input == null) return null;
        List<Map<String, Object>> rows = input.getRows();
        if (rows == null || rules == null || rules.isEmpty()) {
            return input;
        }
        Map<String, MaskingRuleDTO> ruleMap = rules.stream()
                .collect(Collectors.toMap(MaskingRuleDTO::getFieldName, r -> r, (a, b) -> a));
        List<Map<String, Object>> maskedRows = rows.stream()
                .map(row -> applyRowMasking(row, ruleMap))
                .toList();
        return DatasetDTO.builder()
                .schema(input.getSchema())
                .rows(maskedRows)
                .page(input.getPage())
                .stat(input.getStat())
                .build();
    }

    private Map<String, Object> applyRowMasking(Map<String, Object> row, Map<String, MaskingRuleDTO> ruleMap) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        ruleMap.forEach((field, rule) -> {
            if (result.containsKey(field)) {
                result.put(field, fieldMasker.mask(result.get(field), rule));
            }
        });
        return result;
    }

    private ComputeAuditDTO buildAudit(MaskingNodeConfigDTO config, DatasetDTO input, DatasetDTO output) {
        List<Map<String, Object>> rulesSummary = List.of();
        if (config.getRules() != null) {
            rulesSummary = config.getRules().stream().map(rule -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("fieldName", rule.getFieldName());
                item.put("strategy", rule.getStrategy() == null ? null : rule.getStrategy().name());
                return item;
            }).toList();
        }
        return ComputeAuditDTO.builder()
                .capabilityType(supportType())
                .steps(List.of(ComputeStepDTO.builder()
                        .stepName("masking")
                        .description("按规则脱敏字段值")
                        .params(Map.of("rules", rulesSummary))
                        .build()))
                .inputRowCount(rowCount(input))
                .outputRowCount(rowCount(output))
                .build();
    }

    private Integer rowCount(DatasetDTO dataset) {
        if (dataset == null) return 0;
        if (dataset.getStat() != null && dataset.getStat().getRowCount() != null) {
            return dataset.getStat().getRowCount();
        }
        return dataset.getRows() == null ? 0 : dataset.getRows().size();
    }
}
