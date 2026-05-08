package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Configuration for the {@code python_script} node type.
 *
 * <p>User contract:</p>
 * <ul>
 *   <li>{@code rows} — {@code list[dict]} — injected automatically from the upstream dataset</li>
 *   <li>{@code output_rows} — the script must assign the result to this variable</li>
 * </ul>
 *
 * <p>Minimal example:</p>
 * <pre>{@code
 * output_rows = [row for row in rows if row.get('amount', 0) > 100]
 * }</pre>
 */
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PythonScriptNodeConfigDTO extends BaseNodeConfigDTO {
    /** Reference to the upstream node whose dataset is injected as {@code rows}. */
    private VariableRefDTO datasetRef;
    /** Python 3 script body. Must assign {@code output_rows = List[dict]}. */
    private String script;
    /** Execution timeout in seconds. Defaults to 30. */
    private Integer timeoutSeconds;
}
