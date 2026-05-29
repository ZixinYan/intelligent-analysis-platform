package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionsSourceDTO {
    private String type;
    private String source;
    private String uri;
    private String method;
    private String valueField;
    private String labelField;
    private List<String> acceptedTypes;
    private List<String> acceptedCapabilities;
    private List<OptionDTO> options;
    private Map<String, Object> filters;
    private Map<String, Object> extensions;
}
