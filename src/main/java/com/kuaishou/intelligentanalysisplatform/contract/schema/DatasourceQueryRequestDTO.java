package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasourceQueryRequestDTO {
    private String type;
    private String keyword;
    @Builder.Default
    private Integer page = 1;
    @Builder.Default
    private Integer pageSize = 20;
    private RequestContextDTO context;

    public DatasourceType resolveType() {
        if (type == null || type.isBlank()) {
            return null;
        }
        return DatasourceType.valueOf(type.trim().toUpperCase());
    }
}
