package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PanelRuleDTO {
    private String event;
    private String whenExpr;
    private String action;
    private String targetField;
    private Object value;
    private Map<String, Object> extensions;
}
