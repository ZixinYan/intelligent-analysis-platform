package com.kuaishou.intelligentanalysisplatform.contract.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ChartType {
    LINE,
    BAR,
    PIE,
    SCATTER,
    AREA,
    MIXED;

    @JsonCreator
    public static ChartType fromValue(String value) {
        if (value == null) {
            return null;
        }
        // Strip leading non-letter characters (e.g. backslash from LLM LaTeX-style output like "\line")
        String normalized = value.replaceAll("^[^a-zA-Z]+", "").toUpperCase();
        for (ChartType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ChartType: " + value + ", accepted values: " + java.util.Arrays.toString(values()));
    }
}
