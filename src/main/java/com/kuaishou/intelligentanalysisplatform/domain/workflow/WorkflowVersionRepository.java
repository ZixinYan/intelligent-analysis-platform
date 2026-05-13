package com.kuaishou.intelligentanalysisplatform.domain.workflow;

import java.util.List;
import java.util.Optional;

public interface WorkflowVersionRepository {
    void save(WorkflowVersion version);

    Optional<WorkflowVersion> findByWorkflowIdAndVersionNumber(String workflowId, int versionNumber);

    Optional<WorkflowVersion> findLatestByWorkflowId(String workflowId);

    List<WorkflowVersion> findByWorkflowId(String workflowId, int offset, int limit);

    long countByWorkflowId(String workflowId);

    int getMaxVersionNumber(String workflowId);

    void clearPublishedByWorkflowId(String workflowId);

    void setPublished(String workflowId, int versionNumber, boolean published);
}
