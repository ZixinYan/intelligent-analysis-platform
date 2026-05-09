package com.kuaishou.intelligentanalysisplatform.domain.query.connector;

public record HealthCheckResult(boolean success, long latencyMs, String serverVersion, String message) {
}
