package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeDatasetResolver;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeResultFactory;
import com.kuaishou.intelligentanalysisplatform.application.compute.InMemorySortComputeService;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ComputeAuditDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ComputeStepDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SortFieldDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SortNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.springframework.stereotype.Component;

@Component
public class SortNodeExecutor implements NodeExecutor<SortNodeConfigDTO> {
    private final NodeMetadataApplicationService nodeMetadataApplicationService;
    private final ComputeDatasetResolver computeDatasetResolver;
    private final InMemorySortComputeService sortComputeService;
    private final ComputeResultFactory computeResultFactory;

    public SortNodeExecutor(NodeMetadataApplicationService nodeMetadataApplicationService,
                            ComputeDatasetResolver computeDatasetResolver,
                            InMemorySortComputeService sortComputeService,
                            ComputeResultFactory computeResultFactory) {
        this.nodeMetadataApplicationService = nodeMetadataApplicationService;
        this.computeDatasetResolver = computeDatasetResolver;
        this.sortComputeService = sortComputeService;
        this.computeResultFactory = computeResultFactory;
    }

    @Override
    public String supportType() {
        return "sort";
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO context, SortNodeConfigDTO config) {
        long start = System.currentTimeMillis();
        DatasetDTO input = computeDatasetResolver.resolve(config.getDatasetRef(), context.getUpstreamResults());
        DatasetDTO output = sortComputeService.compute(config, input);
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
    public ValidationResultDTO validate(SortNodeConfigDTO config) {
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public NodeMetaDTO metadata() {
        return nodeMetadataApplicationService.getNodeDefinition(supportType());
    }

    private ComputeAuditDTO buildAudit(SortNodeConfigDTO config, DatasetDTO input, DatasetDTO output) {
        List<Map<String, Object>> sortFields = new ArrayList<>();
        if (config.getSortFields() != null) {
            for (SortFieldDTO sortField : config.getSortFields()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("field", sortField.getField());
                item.put("order", sortField.getOrder());
                sortFields.add(item);
            }
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("sortFields", sortFields);
        params.put("limit", config.getLimit());
        return ComputeAuditDTO.builder()
                .capabilityType(supportType())
                .steps(List.of(ComputeStepDTO.builder()
                        .stepName("sort")
                        .description("按字段排序并应用限制")
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
