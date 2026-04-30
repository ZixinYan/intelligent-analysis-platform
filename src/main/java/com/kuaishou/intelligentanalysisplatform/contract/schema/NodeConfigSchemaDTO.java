package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NodeConfigSchemaDTO {
    private String schemaType;
    private String schemaVersion;
    private String panelId;
    private Map<String, Object> layout;
    private List<PanelSectionDTO> sections;
    private List<PanelRuleDTO> rules;
    private Map<String, Object> extensions;
}
