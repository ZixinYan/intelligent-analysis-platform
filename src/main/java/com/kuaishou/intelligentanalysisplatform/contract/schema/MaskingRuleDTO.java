package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.kuaishou.intelligentanalysisplatform.contract.enums.MaskingStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaskingRuleDTO {
    /** 目标字段名 */
    private String fieldName;
    /** 脱敏策略 */
    private MaskingStrategy strategy;
    /** PARTIAL 策略：保留前 N 位（默认3） */
    private Integer keepPrefix;
    /** PARTIAL 策略：保留后 N 位（默认4） */
    private Integer keepSuffix;
    /** REGEX_REPLACE 策略：正则表达式 */
    private String regexPattern;
    /** REGEX_REPLACE 策略：替换字符串（默认"***"） */
    private String replacement;
}
