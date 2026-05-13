package com.kuaishou.intelligentanalysisplatform.application.compute;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ComputeAuditDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetStatDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeRunMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import org.springframework.stereotype.Component;

@Component
public class ComputeResultFactory {
    public NodeResultDTO success(String nodeId,
                                 String nodeType,
                                 DatasetDTO dataset,
                                 boolean pushdownPlanned,
                                 String capabilityType,
                                 long elapsedMs) {
        return success(nodeId, nodeType, dataset, pushdownPlanned, capabilityType, elapsedMs, null);
    }

    public NodeResultDTO success(String nodeId,
                                 String nodeType,
                                 DatasetDTO dataset,
                                 boolean pushdownPlanned,
                                 String capabilityType,
                                 long elapsedMs,
                                 ComputeAuditDTO audit) {
        String executionBoundary = resolveBoundary(capabilityType, pushdownPlanned);
        ComputeAuditDTO normalizedAudit = normalizeAudit(audit, capabilityType, executionBoundary, dataset);
        DatasetDTO normalized = withExtensions(dataset, pushdownPlanned, capabilityType, executionBoundary, normalizedAudit);
        return NodeResultDTO.builder()
                .nodeId(nodeId)
                .nodeType(nodeType)
                .status(ExecutionStatus.SUCCEEDED)
                .result(StandardResultDTO.builder()
                        .kind(ResultKind.DATASET)
                        .dataset(normalized)
                        .build())
                .meta(NodeRunMetaDTO.builder()
                        .elapsedMs(elapsedMs)
                        .capabilityType(capabilityType)
                        .computeEngine(pushdownPlanned ? "DB" : "MEMORY")
                        .pushdownApplied(pushdownPlanned)
                        .executionBoundary(executionBoundary)
                        .audit(normalizedAudit)
                        .build())
                .build();
    }

    private DatasetDTO withExtensions(DatasetDTO dataset,
                                      boolean pushdownPlanned,
                                      String capabilityType,
                                      String executionBoundary,
                                      ComputeAuditDTO audit) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("computeEngine", pushdownPlanned ? "DB" : "MEMORY");
        extensions.put("pushdownPlanned", pushdownPlanned);
        extensions.put("pushdownApplied", pushdownPlanned);
        extensions.put("capabilityType", capabilityType);
        extensions.put("executionBoundary", executionBoundary);
        if (audit != null) {
            extensions.put("audit", audit);
        }
        DatasetStatDTO current = dataset == null ? null : dataset.getStat();
        DatasetStatDTO stat = DatasetStatDTO.builder()
                .rowCount(current == null ? null : current.getRowCount())
                .returnedRowCount(current == null ? null : current.getReturnedRowCount())
                .truncated(current == null ? null : current.getTruncated())
                .extensions(merge(current == null ? null : current.getExtensions(), extensions))
                .build();
        return DatasetDTO.builder()
                .schema(dataset == null ? null : dataset.getSchema())
                .rows(dataset == null ? null : dataset.getRows())
                .page(dataset == null ? null : dataset.getPage())
                .stat(stat)
                .sourceSql(dataset == null ? null : dataset.getSourceSql())
                .sourceDatasourceId(dataset == null ? null : dataset.getSourceDatasourceId())
                .build();
    }

    private ComputeAuditDTO normalizeAudit(ComputeAuditDTO audit,
                                           String capabilityType,
                                           String executionBoundary,
                                           DatasetDTO dataset) {
        if (audit == null) {
            return null;
        }
        return ComputeAuditDTO.builder()
                .capabilityType(audit.getCapabilityType() == null ? capabilityType : audit.getCapabilityType())
                .executionBoundary(audit.getExecutionBoundary() == null ? executionBoundary : audit.getExecutionBoundary())
                .steps(audit.getSteps() == null ? List.of() : audit.getSteps())
                .inputRowCount(audit.getInputRowCount())
                .outputRowCount(audit.getOutputRowCount() == null ? resolveRowCount(dataset) : audit.getOutputRowCount())
                .derivedMetricNote(audit.getDerivedMetricNote())
                .build();
    }

    private Integer resolveRowCount(DatasetDTO dataset) {
        if (dataset == null) {
            return 0;
        }
        if (dataset.getStat() != null && dataset.getStat().getRowCount() != null) {
            return dataset.getStat().getRowCount();
        }
        return dataset.getRows() == null ? 0 : dataset.getRows().size();
    }

    private String resolveBoundary(String capabilityType, boolean pushdownPlanned) {
        if ("aggregate".equals(capabilityType)) {
            return pushdownPlanned ? "GROUP_BY_AGG_WITH_FALLBACK" : "IN_MEMORY_GROUP_BY";
        }
        if ("time_series_compute".equals(capabilityType)) {
            return "IN_MEMORY_PERIOD_SHIFT";
        }
        if ("pivot".equals(capabilityType)) {
            return "IN_MEMORY_PIVOT";
        }
        if ("filter".equals(capabilityType)) {
            return pushdownPlanned ? "WHERE_PUSHDOWN_WITH_FALLBACK" : "IN_MEMORY_FILTER";
        }
        if ("sort".equals(capabilityType)) {
            return pushdownPlanned ? "ORDER_BY_LIMIT_WITH_FALLBACK" : "IN_MEMORY_SORT";
        }
        if ("formula".equals(capabilityType)) {
            return "IN_MEMORY_DERIVED_METRIC";
        }
        if ("data_join".equals(capabilityType)) {
            return pushdownPlanned ? "SQL_JOIN_PUSHDOWN" : "IN_MEMORY_HASH_JOIN";
        }
        return "IN_MEMORY_COMPUTE";
    }

    private Map<String, Object> merge(Map<String, Object> base, Map<String, Object> extra) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (base != null) {
            merged.putAll(base);
        }
        merged.putAll(extra);
        return merged;
    }
}
