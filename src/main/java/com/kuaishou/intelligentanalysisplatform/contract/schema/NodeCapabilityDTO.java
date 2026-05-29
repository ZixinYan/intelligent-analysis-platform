package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeCapabilityDTO {
    private String code;
    private String name;
    private CapabilityConfigDTO capabilityConfig;
    private Map<String, Object> params;
}
