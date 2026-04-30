package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SqlQueryNodeConfigDTO extends BaseNodeConfigDTO {
    private String datasourceId;
    private String sqlTemplate;
    private List<QueryParameterDTO> parameters;
    private QueryOptionDTO queryOption;
}
