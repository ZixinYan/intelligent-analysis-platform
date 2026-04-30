package com.kuaishou.intelligentanalysisplatform.domain.query;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.domain.query.model.GuardViolationCode;
import java.util.List;
import java.util.stream.Collectors;

public class SqlGuardRejectedException extends BaseBusinessException {

    public SqlGuardRejectedException(ErrorCode errorCode, String message, List<GuardViolationCode> violationCodes) {
        super(errorCode, message, violationCodes == null ? null : violationCodes.stream()
                .map(Enum::name)
                .collect(Collectors.joining(",")), null, false);
    }
}
