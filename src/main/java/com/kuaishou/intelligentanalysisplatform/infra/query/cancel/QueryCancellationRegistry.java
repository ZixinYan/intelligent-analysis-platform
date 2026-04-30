package com.kuaishou.intelligentanalysisplatform.infra.query.cancel;

import java.lang.ref.WeakReference;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class QueryCancellationRegistry {
    private final ConcurrentHashMap<String, WeakReference<Statement>> statements = new ConcurrentHashMap<>();

    public void register(String queryId, Statement statement) {
        if (queryId == null || queryId.isBlank() || statement == null) {
            return;
        }
        statements.put(queryId, new WeakReference<>(statement));
    }

    public void deregister(String queryId) {
        if (queryId == null || queryId.isBlank()) {
            return;
        }
        statements.remove(queryId);
    }

    public boolean cancel(String queryId) {
        WeakReference<Statement> reference = statements.get(queryId);
        Statement statement = reference == null ? null : reference.get();
        if (statement == null) {
            return false;
        }
        try {
            statement.cancel();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
