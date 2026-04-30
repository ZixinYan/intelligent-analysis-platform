package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeDatasetResolver;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeResultFactory;
import com.kuaishou.intelligentanalysisplatform.application.compute.InMemoryPivotComputeService;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ComputeAuditDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ComputeStepDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.PivotNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.springframework.stereotype.Component;

@Component
public class PivotNodeExecutor implements NodeExecutor<PivotNodeConfigDTO> {
    private final NodeMetadataApplicationService nodeMetadataApplicationService;
    private final ComputeDatasetResolver computeDatasetResolver;
    private final InMemoryPivotComputeService pivotComputeService;
    private final ComputeResultFactory computeResultFactory;

    public PivotNodeExecutor(NodeMetadataApplicationService nodeMetadataApplicationService,
                             ComputeDatasetResolver computeDatasetResolver,
                             InMemoryPivotComputeService pivotComputeService,
                             ComputeResultFactory computeResultFactory) {
        this.nodeMetadataApplicationService = nodeMetadataApplicationService;
        this.computeDatasetResolver = computeDatasetResolver;
        this.pivotComputeService = pivotComputeService;
        this.computeResultFactory = computeResultFactory;
    }

    @Override
    public String supportType() {
        return "pivot";
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO context, PivotNodeConfigDTO config) {
        long start = System.currentTimeMillis();
        DatasetDTO input = computeDatasetResolver.resolve(config.getDatasetRef(), context.getUpstreamResults());
        DatasetDTO output = pivotComputeService.compute(config, input);
        return computeResultFactory.success(
                context.getNodeId(),
                supportType(),
                output,
                Boolean.TRUE.equals(config.getPushdownEnabled()),
                supportType(),
                System.currentTimeMillis() - start,
                buildAudit(config, input, output)
        );
    }

    @Override
    public ValidationResultDTO validate(PivotNodeConfigDTO config) {
        if (config == null || config.getColumnField() == null || config.getValueField() == null) {
            return ValidationResultDTO.builder().valid(false).errorMessage("columnField and valueField are required").build();
        }
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public NodeMetaDTO metadata() {
        return nodeMetadataApplicationService.getNodeDefinition(supportType());
    }

    private ComputeAuditDTO buildAudit(PivotNodeConfigDTO config, DatasetDTO input, DatasetDTO output) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("rowFields", config.getRowFields() == null ? List.of() : config.getRowFields());
        params.put("columnField", config.getColumnField());
        params.put("valueField", config.getValueField());
        params.put("aggregateField", config.getAggregateField());
        return ComputeAuditDTO.builder()
                .capabilityType(supportType())
                .steps(List.of(ComputeStepDTO.builder()
                        .stepName("pivot")
                        .description("按行列字段透视数据")
                        .params(params)
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
