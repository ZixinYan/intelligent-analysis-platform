package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExportFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ExportOutputNodeConfigDTO extends BaseNodeConfigDTO {
    /** 上游 Dataset 引用 */
    private VariableRefDTO datasetRef;
    /** 导出格式：CSV / EXCEL / PARQUET（首期实现 CSV + EXCEL） */
    private ExportFormat format;
    /** 自定义文件名（不含扩展名），null 则自动生成 */
    private String fileName;
    /** 需要导出的列（null = 全部列，有序） */
    private List<String> columns;
    /** CSV 特有：分隔符（默认逗号） */
    private String csvDelimiter;
    /** 是否包含 header 行（默认 true） */
    private Boolean includeHeader;
}
