package com.kuaishou.intelligentanalysisplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch 知识库向量检索配置。
 */
@Component
@ConfigurationProperties(prefix = "knowledge.es")
public class KnowledgeEsProperties {

    private String url = "http://localhost:9200";
    private String indexName = "knowledge_chunk";
    private int vectorDimension = 1536;
    private int numCandidates = 100;
    private String username = "";
    private String password = "";
    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 30;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }

    public int getVectorDimension() { return vectorDimension; }
    public void setVectorDimension(int vectorDimension) { this.vectorDimension = vectorDimension; }

    public int getNumCandidates() { return numCandidates; }
    public void setNumCandidates(int numCandidates) { this.numCandidates = numCandidates; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }

    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }
}
