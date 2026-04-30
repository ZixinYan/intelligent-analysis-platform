package com.kuaishou.intelligentanalysisplatform.infra.observability;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class QueryGovernanceLimiter {
    private final Map<String, TenantWindow> tenantWindows = new ConcurrentHashMap<>();
    private final Map<String, Semaphore> datasourceSemaphores = new ConcurrentHashMap<>();
    private final AtomicInteger currentDatasourceConcurrency = new AtomicInteger();

    public Lease acquire(String tenantId, int tenantLimit, String datasourceId, int datasourceLimit) {
        checkTenantRateLimit(tenantId, tenantLimit);
        Semaphore semaphore = datasourceSemaphores.computeIfAbsent(normalize(datasourceId), key -> new Semaphore(Math.max(datasourceLimit, 1)));
        if (!semaphore.tryAcquire()) {
            throw new BaseBusinessException(ErrorCode.REQUEST_CONFLICT, "datasource concurrency limit exceeded");
        }
        currentDatasourceConcurrency.incrementAndGet();
        return new Lease(semaphore, currentDatasourceConcurrency);
    }

    public int currentDatasourceConcurrency() {
        return Math.max(currentDatasourceConcurrency.get(), 0);
    }

    private void checkTenantRateLimit(String tenantId, int tenantLimit) {
        long second = System.currentTimeMillis() / 1000;
        TenantWindow window = tenantWindows.computeIfAbsent(normalize(tenantId), key -> new TenantWindow(second));
        synchronized (window) {
            if (window.second.get() != second) {
                window.second.set(second);
                window.counter.set(0);
            }
            if (window.counter.incrementAndGet() > Math.max(tenantLimit, 1)) {
                throw new BaseBusinessException(ErrorCode.RATE_LIMITED, "tenant rate limit exceeded");
            }
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "default" : value;
    }

    private static final class TenantWindow {
        private final AtomicLong second;
        private final AtomicInteger counter = new AtomicInteger();

        private TenantWindow(long second) {
            this.second = new AtomicLong(second);
        }
    }

    public static final class Lease implements AutoCloseable {
        private final Semaphore semaphore;
        private final AtomicInteger currentDatasourceConcurrency;
        private boolean released;

        private Lease(Semaphore semaphore, AtomicInteger currentDatasourceConcurrency) {
            this.semaphore = semaphore;
            this.currentDatasourceConcurrency = currentDatasourceConcurrency;
        }

        @Override
        public void close() {
            if (released) {
                return;
            }
            released = true;
            semaphore.release();
            currentDatasourceConcurrency.updateAndGet(value -> Math.max(value - 1, 0));
        }
    }
}
