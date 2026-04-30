package com.kuaishou.intelligentanalysisplatform.infra.query.cache;

import java.util.Optional;

import com.kuaishou.intelligentanalysisplatform.domain.query.connector.QueryResult;

public interface QueryCacheStore {
    Optional<QueryResult> get(String key);

    void put(String key, QueryResult result, int ttlSeconds);
}
