package com.kuaishou.intelligentanalysisplatform.application;

import com.kuaishou.intelligentanalysisplatform.common.response.PageResult;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowVersionDiffDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowVersionDTO;

public interface WorkflowVersionApplicationService {

    /** 手动创建版本快照（不影响当前草稿） */
    WorkflowVersionDTO snapshot(String workflowId, String changeSummary, RequestContextDTO context);

    /** 列出指定工作流的全部版本（分页） */
    PageResult<WorkflowVersionDTO> listVersions(String workflowId, int page, int pageSize, RequestContextDTO context);

    /** 查看指定版本的完整定义 */
    WorkflowDefinitionDTO getVersion(String workflowId, int versionNumber, RequestContextDTO context);

    /** 发布指定版本（设置为 publishedVersionId） */
    void publish(String workflowId, int versionNumber, RequestContextDTO context);

    /** 回滚：将指定版本内容复制为新版本并设为当前草稿 */
    WorkflowVersionDTO rollback(String workflowId, int versionNumber, RequestContextDTO context);

    /** 版本差异对比（返回节点/边的增删改摘要） */
    WorkflowVersionDiffDTO diff(String workflowId, int fromVersion, int toVersion, RequestContextDTO context);
}
