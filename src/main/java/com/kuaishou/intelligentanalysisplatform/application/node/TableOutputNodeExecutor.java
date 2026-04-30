package com.kuaishou.intelligentanalysisplatform.application.node;

import com.kuaishou.intelligentanalysisplatform.application.node.output.DatasetTruncator;
import com.kuaishou.intelligentanalysisplatform.application.node.output.OutputProtocolAssembler;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.NodeType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.OutputMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.TableOutputDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.TableOutputNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.springframework.stereotype.Component;

@Component
public class TableOutputNodeExecutor implements NodeExecutor<TableOutputNodeConfigDTO> {
    private final RuntimeBindingResolver runtimeBindingResolver;
    private final OutputProtocolAssembler outputProtocolAssembler;
    private final DatasetTruncator datasetTruncator;

    public TableOutputNodeExecutor(RuntimeBindingResolver runtimeBindingResolver,
                                   OutputProtocolAssembler outputProtocolAssembler,
                                   DatasetTruncator datasetTruncator) {
        this.runtimeBindingResolver = runtimeBindingResolver;
        this.outputProtocolAssembler = outputProtocolAssembler;
        this.datasetTruncator = datasetTruncator;
    }

    @Override
    public String supportType() {
        return NodeType.TABLE_OUTPUT.getCode();
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO context, TableOutputNodeConfigDTO config) {
        DatasetDTO dataset = requireDataset(config, context);
        DatasetTruncator.TruncateResult truncateResult = datasetTruncator.truncateForTable(dataset);
        TableOutputDTO tableOutput = outputProtocolAssembler.assembleTable(truncateResult.dataset(), config.getColumns(),
                config.getOption(), context.getNodeId());
        Boolean downloadable = config.getOption() == null ? null : config.getOption().getDownloadable();
        OutputMetaDTO meta = outputProtocolAssembler.buildMeta(context.getNodeId(), truncateResult.partial(), downloadable,
                truncateResult.totalRows(), truncateResult.returnedRows(), truncateResult.truncationStrategy());
        tableOutput.setMeta(meta);
        return NodeResultDTO.builder()
                .nodeId(context.getNodeId())
                .nodeType(supportType())
                .status(ExecutionStatus.SUCCEEDED)
                .result(StandardResultDTO.builder()
                        .kind(ResultKind.TABLE)
                        .table(tableOutput)
                        .build())
                .build();
    }

    @Override
    public ValidationResultDTO validate(TableOutputNodeConfigDTO config) {
        if (config == null || config.getDatasetRef() == null) {
            return ValidationResultDTO.builder().valid(false).errorMessage("datasetRef is required").build();
        }
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public NodeMetaDTO metadata() {
        return null;
    }

    private DatasetDTO requireDataset(TableOutputNodeConfigDTO config, NodeExecuteContextDTO context) {
        Object value = runtimeBindingResolver.resolveVariable(config.getDatasetRef(), context.getUpstreamResults());
        if (!(value instanceof DatasetDTO dataset)) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "table output requires dataset input");
        }
        return dataset;
    }
}
