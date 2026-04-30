package com.kuaishou.intelligentanalysisplatform.infra.query.cancel;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryCancellationRegistryTest {
    @Test
    void shouldCancelRegisteredStatement() {
        QueryCancellationRegistry registry = new QueryCancellationRegistry();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Statement statement = (Statement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{Statement.class},
                (proxy, method, args) -> {
                    if ("cancel".equals(method.getName())) {
                        cancelled.set(true);
                        return null;
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                });
        registry.register("q1", statement);
        assertTrue(registry.cancel("q1"));
        assertTrue(cancelled.get());
        registry.deregister("q1");
        assertFalse(registry.cancel("q1"));
    }
}
