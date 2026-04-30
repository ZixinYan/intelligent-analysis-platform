package com.kuaishou.intelligentanalysisplatform.common.security;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;

public final class TenantSecurityGuard {
    private TenantSecurityGuard() {
    }

    public static void requireSameTenant(String resourceTenantId, RequestContextDTO context) {
        requireContext(context);
        if (resourceTenantId == null || !resourceTenantId.equals(context.getTenantId())) {
            throw new BaseBusinessException(ErrorCode.DATASOURCE_ACCESS_DENIED, "datasource access denied");
        }
    }

    public static void requireContext(RequestContextDTO context) {
        if (context == null || context.getTenantId() == null || context.getTenantId().isBlank()) {
            throw new BaseBusinessException(ErrorCode.UNAUTHORIZED, "unauthorized");
        }
    }
}
