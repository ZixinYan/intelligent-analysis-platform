package com.kuaishou.intelligentanalysisplatform.contract.schema.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartRecommendationDTO {
    /** 推荐图表类型：LINE | BAR | PIE | SCATTER | AREA | MIXED */
    private String chartType;
    /** 置信度 0.0–1.0 */
    private float confidence;
    /** 推荐理由（可直接展示给用户） */
    private String reason;
    /** 字段映射建议，如 {"x": "date_field", "y": "metric_field"} */
    private Map<String, String> fieldMapping;
}
