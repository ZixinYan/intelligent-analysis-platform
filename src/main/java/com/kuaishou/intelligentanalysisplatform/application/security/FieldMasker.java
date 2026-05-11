package com.kuaishou.intelligentanalysisplatform.application.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.kuaishou.intelligentanalysisplatform.contract.schema.MaskingRuleDTO;
import org.springframework.stereotype.Component;

@Component
public class FieldMasker {

    public Object mask(Object value, MaskingRuleDTO rule) {
        if (value == null) return null;
        String str = value.toString();
        return switch (rule.getStrategy()) {
            case HASH -> hashSha256(str);
            case PARTIAL -> maskPartial(str, rule);
            case REGEX_REPLACE -> maskRegex(str, rule);
            case NULL_OUT -> null;
        };
    }

    private String hashSha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String maskPartial(String value, MaskingRuleDTO rule) {
        int prefix = rule.getKeepPrefix() != null ? rule.getKeepPrefix() : 3;
        int suffix = rule.getKeepSuffix() != null ? rule.getKeepSuffix() : 4;
        if (value.length() <= prefix + suffix) {
            return "*".repeat(value.length());
        }
        int maskLen = value.length() - prefix - suffix;
        return value.substring(0, prefix) + "*".repeat(maskLen)
                + value.substring(value.length() - suffix);
    }

    private String maskRegex(String value, MaskingRuleDTO rule) {
        if (rule.getRegexPattern() == null) return value;
        String replacement = rule.getReplacement() != null ? rule.getReplacement() : "***";
        return value.replaceAll(rule.getRegexPattern(), replacement);
    }
}
