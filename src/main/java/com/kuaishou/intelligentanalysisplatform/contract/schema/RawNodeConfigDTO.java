package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RawNodeConfigDTO extends BaseNodeConfigDTO {
    private final Map<String, Object> extraProperties = new LinkedHashMap<>();

    @JsonAnySetter
    public void putExtraProperty(String key, Object value) {
        extraProperties.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getExtraProperties() {
        return extraProperties;
    }
}
