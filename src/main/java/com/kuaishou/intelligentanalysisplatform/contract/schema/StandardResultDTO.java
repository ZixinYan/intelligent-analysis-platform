package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StandardResultDTO {
    private ResultKind kind;
    private DatasetDTO dataset;
    private TableOutputDTO table;
    private ChartOutputDTO chart;
    private Map<String, Object> variables;
}
