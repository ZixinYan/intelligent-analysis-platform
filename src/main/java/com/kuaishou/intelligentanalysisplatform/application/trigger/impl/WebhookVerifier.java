package com.kuaishou.intelligentanalysisplatform.application.trigger.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class WebhookVerifier {

    public void verify(String secret, String body, String signature) {
        if (signature == null || signature.isBlank()) {
            throw new BaseBusinessException(
                    ErrorCode.WEBHOOK_SIGNATURE_INVALID, "Missing X-Webhook-Signature header");
        }
        String expected = "sha256=" + hmacSha256(secret, body == null ? "" : body);
        // 使用 MessageDigest.isEqual 做常量时间比较，防 timing attack
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw new BaseBusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID, "Invalid webhook signature");
        }
    }

    private String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR, "HMAC computation failed: " + e.getMessage());
        }
    }
}
