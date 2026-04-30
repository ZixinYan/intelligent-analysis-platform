package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import com.kuaishou.intelligentanalysisplatform.application.AnalysisService;
import com.kuaishou.intelligentanalysisplatform.common.response.ApiResponse;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncSubmitResponseDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SchemaInferResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ValidateResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/query")
@RequiredArgsConstructor
public class QueryController {
    private final AnalysisService analysisService;

    @PostMapping("/validate")
    public ApiResponse<ValidateResultDTO> validate(@RequestBody QueryRequestDTO request) {
        return ApiResponse.success(analysisService.validate(request));
    }

    @PostMapping("/preview")
    public ApiResponse<QueryResultDTO> preview(@RequestBody QueryRequestDTO request) {
        return ApiResponse.success(analysisService.preview(request));
    }

    @PostMapping("/run-async")
    public ApiResponse<AsyncSubmitResponseDTO> runAsync(@RequestBody QueryRequestDTO request) {
        return ApiResponse.success(analysisService.run(request));
    }

    @GetMapping("/{queryId}/status")
    public ApiResponse<QueryResultDTO> getStatus(@PathVariable String queryId) {
        return ApiResponse.success(analysisService.getStatus(queryId));
    }

    @DeleteMapping("/{queryId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable String queryId) {
        analysisService.cancel(queryId);
        return ApiResponse.success(null);
    }

    @PostMapping("/schema/infer")
    public ApiResponse<SchemaInferResultDTO> inferSchema(@RequestBody QueryRequestDTO request) {
        return ApiResponse.success(analysisService.inferSchema(request));
    }
}
