package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import com.kuaishou.intelligentanalysisplatform.application.TaskApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.response.ApiResponse;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncTaskStatusDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {
    private final TaskApplicationService taskApplicationService;

    @GetMapping("/{taskId}")
    public ApiResponse<AsyncTaskStatusDTO> get(@PathVariable String taskId) {
        return ApiResponse.success(taskApplicationService.getTask(taskId));
    }

    @PostMapping("/{taskId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable String taskId) {
        taskApplicationService.cancelTask(taskId);
        return ApiResponse.success();
    }
}
