package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.application.DatasetApplicationService;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.NodeType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetReadNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.springframework.stereotype.Component;

@Component
public class DatasetReadNodeExecutor implements NodeExecutor<DatasetReadNodeConfigDTO> {

    private final DatasetApplicationService datasetApplicationService;

    public DatasetReadNodeExecutor(DatasetApplicationService datasetApplicationService) {
        this.datasetApplicationService = datasetApplicationService;
    }

    @Override
    public String supportType() {
        return NodeType.DATASET_READ.getCode();
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO context, DatasetReadNodeConfigDTO config) {
        String tenantId = context.getRequestContext() != null
                ? context.getRequestContext().getTenantId() : null;

        DatasetDTO dataset = datasetApplicationService.getFullDataset(config.getDatasetId(), tenantId);

        if (config.getRowLimit() != null && dataset.getRows() != null
                && dataset.getRows().size() > config.getRowLimit()) {
            List<java.util.Map<String, Object>> truncated = dataset.getRows().subList(0, config.getRowLimit());
            dataset = DatasetDTO.builder()
                    .schema(dataset.getSchema())
                    .stat(dataset.getStat())
                    .rows(truncated)
                    .build();
        }

        return NodeResultDTO.builder()
                .nodeId(context.getNodeId())
                .nodeType(supportType())
                .status(ExecutionStatus.SUCCEEDED)
                .result(StandardResultDTO.builder()
                        .kind(ResultKind.DATASET)
                        .dataset(dataset)
                        .build())
                .build();
    }

    @Override
    public ValidationResultDTO validate(DatasetReadNodeConfigDTO config) {
        if (config == null || config.getDatasetId() == null || config.getDatasetId().isBlank()) {
            return ValidationResultDTO.builder().valid(false).errorMessage("datasetId is required").build();
        }
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public NodeMetaDTO metadata() {
        return null;
    }
}
