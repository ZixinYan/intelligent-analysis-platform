package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeDatasetResolver;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeResultFactory;
import com.kuaishou.intelligentanalysisplatform.application.compute.InMemoryFormulaComputeService;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ComputeAuditDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ComputeStepDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FormulaFieldDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FormulaNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.springframework.stereotype.Component;

@Component
public class FormulaNodeExecutor implements NodeExecutor<FormulaNodeConfigDTO> {
    private final NodeMetadataApplicationService nodeMetadataApplicationService;
    private final ComputeDatasetResolver computeDatasetResolver;
    private final InMemoryFormulaComputeService formulaComputeService;
    private final ComputeResultFactory computeResultFactory;

    public FormulaNodeExecutor(NodeMetadataApplicationService nodeMetadataApplicationService,
                               ComputeDatasetResolver computeDatasetResolver,
                               InMemoryFormulaComputeService formulaComputeService,
                               ComputeResultFactory computeResultFactory) {
        this.nodeMetadataApplicationService = nodeMetadataApplicationService;
        this.computeDatasetResolver = computeDatasetResolver;
        this.formulaComputeService = formulaComputeService;
        this.computeResultFactory = computeResultFactory;
    }

    @Override
    public String supportType() {
        return "formula";
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO context, FormulaNodeConfigDTO config) {
        long start = System.currentTimeMillis();
        DatasetDTO input = computeDatasetResolver.resolve(config.getDatasetRef(), context.getUpstreamResults());
        DatasetDTO output = formulaComputeService.compute(config, input);
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
    public ValidationResultDTO validate(FormulaNodeConfigDTO config) {
        return ValidationResultDTO.builder().valid(config != null && config.getFormulas() != null && !config.getFormulas().isEmpty()).errorMessage("formulas are required").build();
    }

    @Override
    public NodeMetaDTO metadata() {
        return nodeMetadataApplicationService.getNodeDefinition(supportType());
    }

    private ComputeAuditDTO buildAudit(FormulaNodeConfigDTO config, DatasetDTO input, DatasetDTO output) {
        List<ComputeStepDTO> steps = new ArrayList<>();
        if (config.getFormulas() != null) {
            for (FormulaFieldDTO formula : config.getFormulas()) {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("alias", formula.getAlias());
                params.put("expression", formula.getExpression());
                steps.add(ComputeStepDTO.builder()
                        .stepName("formula_eval")
                        .description("执行派生指标公式")
                        .params(params)
                        .build());
            }
        }
        return ComputeAuditDTO.builder()
                .capabilityType(supportType())
                .steps(steps)
                .inputRowCount(rowCount(input))
                .outputRowCount(rowCount(output))
                .derivedMetricNote("DERIVED_METRIC: 基于已有字段通过表达式派生新指标")
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
