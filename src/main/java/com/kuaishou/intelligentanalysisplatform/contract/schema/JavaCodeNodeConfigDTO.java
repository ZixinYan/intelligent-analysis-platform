package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Configuration for the {@code java_code} node type.
 *
 * <p>The user supplies the <strong>body</strong> of a {@code process} method with signature:</p>
 * <pre>{@code
 * List<Map<String, Object>> process(List<Map<String, Object>> rows)
 * }</pre>
 *
 * <p>Common imports are pre-injected ({@code java.util.*}, {@code java.util.stream.*},
 * {@code java.math.*}). The code must end with a {@code return} statement.</p>
 *
 * <h3>Minimal example</h3>
 * <pre>{@code
 * return rows.stream()
 *     .filter(r -> ((Number) r.getOrDefault("amount", 0)).doubleValue() > 100)
 *     .collect(java.util.stream.Collectors.toList());
 * }</pre>
 */
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class JavaCodeNodeConfigDTO extends BaseNodeConfigDTO {
    /** Reference to the upstream node whose dataset is passed as {@code rows}. */
    private VariableRefDTO datasetRef;
    /** Java code that forms the body of the {@code process(rows)} method. */
    private String code;
    /** Execution timeout in seconds. Defaults to 30. */
    private Integer timeoutSeconds;
}
