package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalGateNodeConfigDTO extends BaseNodeConfigDTO {
    /** 审批原因描述（可含变量引用） */
    private String reasonTemplate;
    /** 审批人列表（用户ID或角色标识） */
    private List<String> approvers;
    /** 超时时间（秒），超时自动拒绝，0 表示永不超时 */
    private Integer timeoutSeconds;
    /** 超时后行为：REJECT（默认）/ AUTO_APPROVE */
    private String timeoutAction;
}
