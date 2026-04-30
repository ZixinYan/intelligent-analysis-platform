package com.kuaishou.intelligentanalysisplatform.application.security;

import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;

public interface PermissionChecker {
    void requireRead(RequestContextDTO context);

    void requireWrite(RequestContextDTO context);

    void requireDelete(RequestContextDTO context);

    void requireTestConnection(RequestContextDTO context);
}
