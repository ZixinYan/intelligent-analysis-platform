package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasourceQueryAccessDTO {
    private String id;
    private String tenantId;
    private DatasourceType type;
    private String host;
    private Integer port;
    private String database;
    private String username;
    private String encryptedPassword;
    private Map<String, String> jdbcOptions;
    private Boolean readonly;
}
