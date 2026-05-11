package com.kuaishou.intelligentanalysisplatform.contract.enums;

public enum MaskingStrategy {
    HASH,           // SHA-256 哈希
    PARTIAL,        // 部分遮蔽（保留首尾）
    REGEX_REPLACE,  // 正则替换
    NULL_OUT        // 置空
}
