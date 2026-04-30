package com.kuaishou.intelligentanalysisplatform.infra.query.cache;

import com.kuaishou.intelligentanalysisplatform.domain.query.connector.QueryResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryQueryCacheStoreTest {
    @Test
    void shouldExpireCacheEntry() throws Exception {
        InMemoryQueryCacheStore store = new InMemoryQueryCacheStore();
        store.put("k", QueryResult.builder().fields(List.of()).rows(List.of(Map.of())).rowCount(1).truncated(false).cached(false).build(), 1);
        assertTrue(store.get("k").isPresent());
        Thread.sleep(1100L);
        assertFalse(store.get("k").isPresent());
    }
}
