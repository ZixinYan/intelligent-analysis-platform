package com.kuaishou.intelligentanalysisplatform.application.compute;

import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.VariableRefDTO;
import org.springframework.stereotype.Component;

@Component
public class ComputeDatasetResolver {
    public DatasetDTO resolve(VariableRefDTO datasetRef, Map<String, StandardResultDTO> upstreamResults) {
        if (datasetRef != null) {
            StandardResultDTO standardResult = upstreamResults == null ? null : upstreamResults.get(datasetRef.getSourceNodeId());
            if (standardResult != null && standardResult.getDataset() != null) {
                return standardResult.getDataset();
            }
        }
        if (upstreamResults != null) {
            for (StandardResultDTO result : upstreamResults.values()) {
                if (result != null && result.getKind() == ResultKind.DATASET && result.getDataset() != null) {
                    return result.getDataset();
                }
            }
        }
        throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "dataset input is required");
    }
}
