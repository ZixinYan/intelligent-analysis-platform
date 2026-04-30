package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.enums.FieldComponentType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.FieldSemanticType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ValueType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PanelFieldDTO {
    private String field;
    private String label;
    private FieldComponentType componentType;
    private Boolean required;
    private Boolean visible;
    private Boolean editable;
    private Boolean disabled;
    private Boolean multiple;
    private Integer order;
    private ValueType valueType;
    private FieldSemanticType semanticType;
    private Object defaultValue;
    private String placeholder;
    private String description;
    private List<OptionDTO> options;
    private OptionsSourceDTO optionsSource;
    private List<ValidationRuleDTO> validations;
    private ValidationRuleDTO validation;
    private VariableBindingSupportDTO variableBinding;
    private List<FieldVisibilityRuleDTO> visibilityRules;
    private List<FieldEnableRuleDTO> enableRules;
    private Map<String, Object> props;
    private Map<String, Object> extensions;
}
