package com.kuaishou.intelligentanalysisplatform.infra.security;

import com.kuaishou.intelligentanalysisplatform.application.security.PermissionChecker;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import org.springframework.stereotype.Service;

@Service
public class DefaultPermissionChecker implements PermissionChecker {
    @Override
    public void requireRead(RequestContextDTO context) {
        requireAuthenticated(context);
    }

    @Override
    public void requireWrite(RequestContextDTO context) {
        requireAuthenticated(context);
    }

    @Override
    public void requireDelete(RequestContextDTO context) {
        requireAuthenticated(context);
    }

    @Override
    public void requireTestConnection(RequestContextDTO context) {
        requireAuthenticated(context);
    }

    private void requireAuthenticated(RequestContextDTO context) {
        if (context == null || context.getTenantId() == null || context.getTenantId().isBlank()
                || context.getUserId() == null || context.getUserId().isBlank()) {
            throw new BaseBusinessException(ErrorCode.UNAUTHORIZED, "unauthorized");
        }
    }
}
