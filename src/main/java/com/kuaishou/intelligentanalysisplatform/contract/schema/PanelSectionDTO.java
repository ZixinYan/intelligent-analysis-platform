package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PanelSectionDTO {
    private String key;
    private String title;
    private Integer order;
    private List<PanelFieldDTO> fields;
}
