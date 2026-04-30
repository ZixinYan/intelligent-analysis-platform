package com.kuaishou.intelligentanalysisplatform.contract.spi;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValidationResultDTO {
    private boolean valid;
    private String errorMessage;
}
