package com.kuaishou.intelligentanalysisplatform.infra.query.cache;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.kuaishou.intelligentanalysisplatform.domain.query.connector.QueryResult;
import org.springframework.stereotype.Component;

@Component
public class InMemoryQueryCacheStore implements QueryCacheStore {
    private final ConcurrentHashMap<String, CacheEntry> store = new ConcurrentHashMap<>();

    @Override
    public Optional<QueryResult> get(String key) {
        CacheEntry entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expireAtMs < System.currentTimeMillis()) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.result);
    }

    @Override
    public void put(String key, QueryResult result, int ttlSeconds) {
        store.put(key, new CacheEntry(result, System.currentTimeMillis() + ttlSeconds * 1000L));
    }

    private record CacheEntry(QueryResult result, long expireAtMs) {
    }
}
