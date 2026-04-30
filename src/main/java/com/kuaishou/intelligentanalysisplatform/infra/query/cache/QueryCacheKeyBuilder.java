package com.kuaishou.intelligentanalysisplatform.infra.query.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class QueryCacheKeyBuilder {
    private QueryCacheKeyBuilder() {
    }

    public static String build(String tenantId, String datasourceId, String sql) {
        return sha256((tenantId == null ? "" : tenantId) + "|" + (datasourceId == null ? "" : datasourceId) + "|" + (sql == null ? "" : sql));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
