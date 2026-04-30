package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ValueSourceType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ChartOutputDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeInputBindingDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.TableOutputDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.VariableRefDTO;
import org.springframework.stereotype.Component;

@Component
public class RuntimeBindingResolver {
    public Map<String, Object> resolve(List<NodeInputBindingDTO> bindings, Map<String, StandardResultDTO> upstreamResults) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        if (bindings == null || bindings.isEmpty()) {
            return resolved;
        }
        for (NodeInputBindingDTO binding : bindings) {
            if (binding == null || binding.getName() == null || binding.getName().isBlank()) {
                continue;
            }
            Object value = resolveBinding(binding, upstreamResults);
            if (value != null || Boolean.TRUE.equals(binding.getRequired())) {
                resolved.put(binding.getName(), value);
            }
        }
        return resolved;
    }

    public Object resolveVariable(VariableRefDTO variableRef, Map<String, StandardResultDTO> upstreamResults) {
        if (variableRef == null || variableRef.getSourceNodeId() == null || variableRef.getSourceNodeId().isBlank()) {
            return null;
        }
        StandardResultDTO result = upstreamResults == null ? null : upstreamResults.get(variableRef.getSourceNodeId());
        if (result == null) {
            return null;
        }
        Object current = result;
        List<String> path = variableRef.getPath();
        if (path == null || path.isEmpty()) {
            return current;
        }
        for (String segment : path) {
            current = descend(current, segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private Object resolveBinding(NodeInputBindingDTO binding, Map<String, StandardResultDTO> upstreamResults) {
        if (binding.getSourceType() == null || binding.getSourceType() == ValueSourceType.LITERAL) {
            return binding.getLiteralValue();
        }
        if (binding.getSourceType() == ValueSourceType.VARIABLE_REF) {
            Object value = resolveVariable(binding.getVariableRef(), upstreamResults);
            if (value == null && Boolean.TRUE.equals(binding.getRequired())) {
                throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "required variable binding missing");
            }
            return value;
        }
        throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "unsupported binding source type");
    }

    private Object descend(Object current, String segment) {
        if (current instanceof StandardResultDTO standardResult) {
            return fromStandardResult(standardResult, segment);
        }
        if (current instanceof DatasetDTO dataset) {
            return switch (segment) {
                case "schema" -> dataset.getSchema();
                case "rows" -> dataset.getRows();
                case "page" -> dataset.getPage();
                case "stat" -> dataset.getStat();
                default -> null;
            };
        }
        if (current instanceof TableOutputDTO table) {
            return switch (segment) {
                case "title" -> table.getTitle();
                case "columns" -> table.getColumns();
                case "rows" -> table.getRows();
                case "option" -> table.getOption();
                case "meta" -> table.getMeta();
                default -> null;
            };
        }
        if (current instanceof ChartOutputDTO chart) {
            return switch (segment) {
                case "title" -> chart.getTitle();
                case "chartType" -> chart.getChartType();
                case "data" -> chart.getData();
                case "option" -> chart.getOption();
                case "meta" -> chart.getMeta();
                default -> null;
            };
        }
        if (current instanceof Map<?, ?> map) {
            return map.get(segment);
        }
        if (current instanceof List<?> list) {
            Integer index = toIndex(segment);
            if (index == null || index < 0 || index >= list.size()) {
                return null;
            }
            return list.get(index);
        }
        return null;
    }

    private Object fromStandardResult(StandardResultDTO result, String segment) {
        return switch (segment) {
            case "kind" -> result.getKind();
            case "dataset" -> result.getDataset();
            case "table" -> result.getTable();
            case "chart" -> result.getChart();
            case "variables" -> result.getVariables();
            default -> null;
        };
    }

    private Integer toIndex(String segment) {
        try {
            return Integer.parseInt(segment);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
