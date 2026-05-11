package com.kuaishou.intelligentanalysisplatform.application.security;

import com.kuaishou.intelligentanalysisplatform.contract.enums.MaskingStrategy;
import com.kuaishou.intelligentanalysisplatform.contract.schema.MaskingRuleDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FieldMaskerTest {

    private final FieldMasker masker = new FieldMasker();

    @Test
    void hash_sameInputProducesSameHash() {
        MaskingRuleDTO rule = MaskingRuleDTO.builder().fieldName("id").strategy(MaskingStrategy.HASH).build();
        Object h1 = masker.mask("abc123", rule);
        Object h2 = masker.mask("abc123", rule);
        assertEquals(h1, h2);
        assertNotEquals("abc123", h1);
        assertEquals(64, h1.toString().length()); // SHA-256 hex is 64 chars
    }

    @Test
    void partial_phoneNumber() {
        MaskingRuleDTO rule = MaskingRuleDTO.builder().fieldName("phone").strategy(MaskingStrategy.PARTIAL).build();
        // default keepPrefix=3, keepSuffix=4, "13812345678" length=11, mask=4
        assertEquals("138****5678", masker.mask("13812345678", rule));
    }

    @Test
    void partial_customPrefixSuffix() {
        MaskingRuleDTO rule = MaskingRuleDTO.builder().fieldName("phone").strategy(MaskingStrategy.PARTIAL)
                .keepPrefix(2).keepSuffix(2).build();
        assertEquals("13******78", masker.mask("1312345678", rule));
    }

    @Test
    void partial_shortString_allStars() {
        MaskingRuleDTO rule = MaskingRuleDTO.builder().fieldName("v").strategy(MaskingStrategy.PARTIAL)
                .keepPrefix(3).keepSuffix(4).build();
        // length 5 <= 3+4=7, all stars
        assertEquals("*****", masker.mask("hello", rule));
    }

    @Test
    void regexReplace_basic() {
        MaskingRuleDTO rule = MaskingRuleDTO.builder().fieldName("phone").strategy(MaskingStrategy.REGEX_REPLACE)
                .regexPattern("\\d{4}-(\\d{3})-(\\d{4})").replacement("***-***-****").build();
        assertEquals("***-***-****", masker.mask("4001-123-5678", rule));
    }

    @Test
    void regexReplace_defaultReplacement() {
        MaskingRuleDTO rule = MaskingRuleDTO.builder().fieldName("v").strategy(MaskingStrategy.REGEX_REPLACE)
                .regexPattern("\\d+").build();
        assertEquals("ID:***", masker.mask("ID:12345", rule));
    }

    @Test
    void nullOut_returnsNull() {
        MaskingRuleDTO rule = MaskingRuleDTO.builder().fieldName("secret").strategy(MaskingStrategy.NULL_OUT).build();
        assertNull(masker.mask("sensitive", rule));
    }

    @Test
    void nullValue_returnsNull() {
        MaskingRuleDTO rule = MaskingRuleDTO.builder().fieldName("v").strategy(MaskingStrategy.HASH).build();
        assertNull(masker.mask(null, rule));
    }
}
