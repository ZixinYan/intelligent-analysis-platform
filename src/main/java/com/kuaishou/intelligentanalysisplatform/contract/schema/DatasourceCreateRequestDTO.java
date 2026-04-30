package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasourceCreateRequestDTO {
    @NotBlank
    private String name;
    @NotNull
    private DatasourceType type;
    @NotBlank
    private String host;
    @NotNull
    @Min(1)
    @Max(65535)
    private Integer port;
    @NotBlank
    private String database;
    @NotBlank
    private String username;
    @NotBlank
    private String password;
    @Builder.Default
    private Map<String, String> jdbcOptions = Map.of();
    @NotNull
    private Boolean readonly;
    private RequestContextDTO context;
}
