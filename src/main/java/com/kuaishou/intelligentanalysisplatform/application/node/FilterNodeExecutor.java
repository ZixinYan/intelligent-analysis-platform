package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeDatasetResolver;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeResultFactory;
import com.kuaishou.intelligentanalysisplatform.application.compute.InMemoryFilterComputeService;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ComputeAuditDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ComputeStepDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FilterConditionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FilterNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.springframework.stereotype.Component;

@Component
public class FilterNodeExecutor implements NodeExecutor<FilterNodeConfigDTO> {
    private final NodeMetadataApplicationService nodeMetadataApplicationService;
    private final ComputeDatasetResolver computeDatasetResolver;
    private final InMemoryFilterComputeService filterComputeService;
    private final ComputeResultFactory computeResultFactory;

    public FilterNodeExecutor(NodeMetadataApplicationService nodeMetadataApplicationService,
                              ComputeDatasetResolver computeDatasetResolver,
                              InMemoryFilterComputeService filterComputeService,
                              ComputeResultFactory computeResultFactory) {
        this.nodeMetadataApplicationService = nodeMetadataApplicationService;
        this.computeDatasetResolver = computeDatasetResolver;
        this.filterComputeService = filterComputeService;
        this.computeResultFactory = computeResultFactory;
    }

    @Override
    public String supportType() {
        return "filter";
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO context, FilterNodeConfigDTO config) {
        long start = System.currentTimeMillis();
        DatasetDTO input = computeDatasetResolver.resolve(config.getDatasetRef(), context.getUpstreamResults());
        DatasetDTO output = filterComputeService.compute(config, input);
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
    public ValidationResultDTO validate(FilterNodeConfigDTO config) {
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public NodeMetaDTO metadata() {
        return nodeMetadataApplicationService.getNodeDefinition(supportType());
    }

    private ComputeAuditDTO buildAudit(FilterNodeConfigDTO config, DatasetDTO input, DatasetDTO output) {
        List<Map<String, Object>> conditions = new ArrayList<>();
        if (config.getConditions() != null) {
            for (FilterConditionDTO condition : config.getConditions()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("field", condition.getField());
                item.put("operator", condition.getOperator() == null ? null : condition.getOperator().name());
                item.put("value", condition.getValue());
                item.put("values", condition.getValues());
                conditions.add(item);
            }
        }
        return ComputeAuditDTO.builder()
                .capabilityType(supportType())
                .steps(List.of(ComputeStepDTO.builder()
                        .stepName("filter")
                        .description("按条件过滤数据")
                        .params(Map.of("conditions", conditions))
                        .build()))
                .inputRowCount(rowCount(input))
                .outputRowCount(rowCount(output))
                .build();
    }

    private Integer rowCount(DatasetDTO dataset) {
        if (dataset == null) {
            return 0;
        }
        if (dataset.getStat() != null && dataset.getStat().getRowCount() != null) {
            return dataset.getStat().getRowCount();
        }
        return dataset.getRows() == null ? 0 : dataset.getRows().size();
    }
}
