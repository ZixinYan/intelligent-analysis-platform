package com.kuaishou.intelligentanalysisplatform.application;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldCandidateSlotDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SchemaInferResultDTO;

public interface NodeMetadataApplicationService {
    List<NodeMetaDTO> listNodeDefinitions();

    NodeMetaDTO getNodeDefinition(String nodeType);

    SchemaInferResultDTO inferSchema(String nodeType);

    List<FieldCandidateSlotDTO> getMappingCandidates(String nodeType, String renderer);
}
