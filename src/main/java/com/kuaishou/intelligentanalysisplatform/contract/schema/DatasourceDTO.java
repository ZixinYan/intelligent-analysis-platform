package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasourceDTO {
    private String id;
    private String tenantId;
    private String name;
    private DatasourceType type;
    private String host;
    private Integer port;
    private String database;
    private String username;
    private Map<String, String> jdbcOptions;
    private DatasourceStatus status;
    private Boolean readonly;
    private Long createdAt;
    private Long updatedAt;
    private String createdBy;
}
