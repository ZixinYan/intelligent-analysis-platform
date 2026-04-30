package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeCapabilityRegistry;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.common.response.ApiResponse;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldCandidateSlotDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeCapabilityDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SchemaInferResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/node-definitions")
@RequiredArgsConstructor
public class NodeDefinitionController {
    private final NodeMetadataApplicationService nodeMetadataApplicationService;
    private final ComputeCapabilityRegistry computeCapabilityRegistry;

    @GetMapping
    public ApiResponse<List<NodeMetaDTO>> list() {
        return ApiResponse.success(nodeMetadataApplicationService.listNodeDefinitions());
    }

    @GetMapping("/{nodeType}")
    public ApiResponse<NodeMetaDTO> get(@PathVariable String nodeType) {
        return ApiResponse.success(nodeMetadataApplicationService.getNodeDefinition(nodeType));
    }

    @GetMapping("/{nodeType}/schema-infer")
    public ApiResponse<SchemaInferResultDTO> inferSchema(@PathVariable String nodeType) {
        return ApiResponse.success(nodeMetadataApplicationService.inferSchema(nodeType));
    }

    @GetMapping("/{nodeType}/mapping-candidates")
    public ApiResponse<List<FieldCandidateSlotDTO>> getMappingCandidates(
            @PathVariable String nodeType,
            @RequestParam String renderer) {
        return ApiResponse.success(nodeMetadataApplicationService.getMappingCandidates(nodeType, renderer));
    }

    @GetMapping("/compute-capabilities")
    public ApiResponse<List<NodeCapabilityDTO>> listComputeCapabilities() {
        return ApiResponse.success(computeCapabilityRegistry.listAll());
    }

    @GetMapping("/compute-capabilities/{code}")
    public ApiResponse<NodeCapabilityDTO> getComputeCapability(@PathVariable String code) {
        NodeCapabilityDTO capability = computeCapabilityRegistry.getByCode(code);
        if (capability == null) {
            throw new BaseBusinessException(ErrorCode.NODE_NOT_FOUND, "compute capability not found");
        }
        return ApiResponse.success(capability);
    }
}
