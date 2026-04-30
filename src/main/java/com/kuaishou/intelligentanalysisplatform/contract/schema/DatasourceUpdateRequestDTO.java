package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasourceUpdateRequestDTO {
    private String name;
    private DatasourceType type;
    private String host;
    @Min(1)
    @Max(65535)
    private Integer port;
    private String database;
    private String username;
    private String password;
    @Builder.Default
    private Map<String, String> jdbcOptions = Map.of();
    private Boolean readonly;
    private RequestContextDTO context;
}
