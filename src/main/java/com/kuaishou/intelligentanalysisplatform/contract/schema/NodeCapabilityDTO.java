package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NodeCapabilityDTO {
    private String code;
    private String name;
    private CapabilityConfigDTO capabilityConfig;
    private Map<String, Object> params;
}
