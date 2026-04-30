package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.enums.FieldCapabilityTag;
import com.kuaishou.intelligentanalysisplatform.contract.enums.FieldSemanticType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ValueType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
public class FieldSchemaDTO {
    private String fieldId;
    private String name;
    private List<String> path;
    private ValueType valueType;
    private Boolean nullable;
    private String displayName;
    private FieldSemanticType semanticType;
    private List<FieldCapabilityTag> capabilities;
    private List<Object> sampleValues;
    private Map<String, Object> stats;
    private Map<String, Object> extensions;
}
