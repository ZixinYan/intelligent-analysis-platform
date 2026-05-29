package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 迭代节点配置。
 *
 * <p>迭代节点从上游节点的变量字段中取出一个 List，对列表中的每个元素执行内部子图，
 * 并将所有迭代结果合并（FLATTEN：合并为单个 Dataset；COLLECT：收集为变量数组）后输出。
 *
 * <p>内部子图中可通过 sourceNodeId="$item" 引用当前迭代元素，其结构为：
 * <pre>
 *   variables.value = 当前迭代元素本身
 * </pre>
 * 同时，外层所有上游结果在子图内仍然可用。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class IterationNodeConfigDTO extends BaseNodeConfigDTO {

    /**
     * 指向上游节点某个 List 变量的引用。
     * 例如：sourceNodeId="node1", path=["variables","items"]
     * 将从 node1 的 variables.items 字段取出数组作为迭代输入。
     */
    private VariableRefDTO inputArrayRef;

    /** 迭代节点内部子图的节点定义 */
    private List<WorkflowNodeDTO> innerNodes;

    /** 迭代节点内部子图的边定义 */
    private List<WorkflowEdgeDTO> innerEdges;

    /**
     * 输出模式：
     * <ul>
     *   <li>FLATTEN（默认）：将每轮迭代产生的 Dataset 行合并为一个大 Dataset</li>
     *   <li>COLLECT：将每轮迭代的最终结果收集为 variables._results 列表</li>
     * </ul>
     */
    private String outputMode;

    /**
     * 最大迭代次数，防止超大数组导致执行失控，默认 100。
     * 超过此值时截断输入数组，不报错。
     */
    private Integer maxIterations;
}
