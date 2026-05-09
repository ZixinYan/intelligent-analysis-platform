package com.kuaishou.intelligentanalysisplatform.infra.observability;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that populates MDC with contextual information for structured logging.
 * This ensures every request has a traceId, tenantId, userId, httpMethod, and requestUri
 * in the MDC context, which gets included in every log line via the Logstash encoder.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggingFilter implements Filter {

    private static final String HEADER_TRACE_ID = "X-Trace-Id";
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String HEADER_USER_ID = "X-User-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // Resolve or generate trace ID
        String traceId = httpRequest.getHeader(HEADER_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        // Populate MDC
        MDC.put("traceId", traceId);
        MDC.put("tenantId", httpRequest.getHeader(HEADER_TENANT_ID));
        MDC.put("userId", httpRequest.getHeader(HEADER_USER_ID));
        MDC.put("httpMethod", httpRequest.getMethod());
        MDC.put("requestUri", httpRequest.getRequestURI());

        // Echo trace ID in response header
        if (response instanceof HttpServletResponse httpResponse) {
            httpResponse.setHeader(HEADER_TRACE_ID, traceId);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
