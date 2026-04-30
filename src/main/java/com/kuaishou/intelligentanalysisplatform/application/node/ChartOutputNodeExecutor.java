package com.kuaishou.intelligentanalysisplatform.application.node;

import com.kuaishou.intelligentanalysisplatform.application.node.output.DatasetTruncator;
import com.kuaishou.intelligentanalysisplatform.application.node.output.OutputProtocolAssembler;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.NodeType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ChartOutputDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ChartOutputNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.OutputMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.springframework.stereotype.Component;

@Component
public class ChartOutputNodeExecutor implements NodeExecutor<ChartOutputNodeConfigDTO> {
    private final RuntimeBindingResolver runtimeBindingResolver;
    private final OutputProtocolAssembler outputProtocolAssembler;
    private final DatasetTruncator datasetTruncator;

    public ChartOutputNodeExecutor(RuntimeBindingResolver runtimeBindingResolver,
                                   OutputProtocolAssembler outputProtocolAssembler,
                                   DatasetTruncator datasetTruncator) {
        this.runtimeBindingResolver = runtimeBindingResolver;
        this.outputProtocolAssembler = outputProtocolAssembler;
        this.datasetTruncator = datasetTruncator;
    }

    @Override
    public String supportType() {
        return NodeType.CHART_OUTPUT.getCode();
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO context, ChartOutputNodeConfigDTO config) {
        DatasetDTO dataset = requireDataset(config, context);
        DatasetTruncator.TruncateResult truncateResult = datasetTruncator.truncateForChart(dataset);
        ChartOutputDTO chartOutput = outputProtocolAssembler.assembleChart(truncateResult.dataset(), config.getMapping(),
                config.getChartType(), config.getOption(), context.getNodeId());
        OutputMetaDTO meta = outputProtocolAssembler.buildMeta(context.getNodeId(), truncateResult.partial(), false,
                truncateResult.totalRows(), truncateResult.returnedRows(), truncateResult.truncationStrategy());
        chartOutput.setMeta(meta);
        return NodeResultDTO.builder()
                .nodeId(context.getNodeId())
                .nodeType(supportType())
                .status(ExecutionStatus.SUCCEEDED)
                .result(StandardResultDTO.builder()
                        .kind(ResultKind.CHART)
                        .chart(chartOutput)
                        .build())
                .build();
    }

    @Override
    public ValidationResultDTO validate(ChartOutputNodeConfigDTO config) {
        if (config == null || config.getDatasetRef() == null) {
            return ValidationResultDTO.builder().valid(false).errorMessage("datasetRef is required").build();
        }
        if (config.getChartType() == null) {
            return ValidationResultDTO.builder().valid(false).errorMessage("chartType is required").build();
        }
        if (config.getMapping() == null || config.getMapping().getCategoryField() == null
                || config.getMapping().getCategoryField().isBlank()) {
            return ValidationResultDTO.builder().valid(false).errorMessage("mapping.categoryField is required").build();
        }
        if (config.getMapping().getSeriesFields() == null || config.getMapping().getSeriesFields().isEmpty()) {
            return ValidationResultDTO.builder().valid(false).errorMessage("mapping.seriesFields is required").build();
        }
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public NodeMetaDTO metadata() {
        return null;
    }

    private DatasetDTO requireDataset(ChartOutputNodeConfigDTO config, NodeExecuteContextDTO context) {
        Object value = runtimeBindingResolver.resolveVariable(config.getDatasetRef(), context.getUpstreamResults());
        if (!(value instanceof DatasetDTO dataset)) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "chart output requires dataset input");
        }
        return dataset;
    }
}
