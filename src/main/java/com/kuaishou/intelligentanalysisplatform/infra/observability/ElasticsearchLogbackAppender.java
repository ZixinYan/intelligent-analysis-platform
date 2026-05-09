package com.kuaishou.intelligentanalysisplatform.infra.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ElasticsearchLogbackAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static final DateTimeFormatter INDEX_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd")
            .withZone(ZoneOffset.UTC);

    private String elasticsearchUrls = "http://192.168.1.114:9200";
    private String indexPrefix = "intelligent-analysis-platform";
    private int batchSize = 200;
    private int flushIntervalSeconds = 5;
    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 10;
    private int maxRetries = 3;
    private boolean verbose = false;
    private Encoder<ILoggingEvent> encoder;
    private final BlockingQueue<ILoggingEvent> eventQueue = new LinkedBlockingQueue<>(10000);
    private HttpClient httpClient;
    private ScheduledExecutorService scheduler;
    private List<String> esUrlsList = new ArrayList<>();

    public void setElasticsearchUrls(String elasticsearchUrls) { this.elasticsearchUrls = elasticsearchUrls; }
    public void setIndexPrefix(String indexPrefix) { this.indexPrefix = indexPrefix; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public void setFlushIntervalSeconds(int flushIntervalSeconds) { this.flushIntervalSeconds = flushIntervalSeconds; }
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }
    public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public void setVerbose(boolean verbose) { this.verbose = verbose; }
    public void setEncoder(Encoder<ILoggingEvent> encoder) { this.encoder = encoder; }

    @Override
    public void start() {
        if (elasticsearchUrls == null || elasticsearchUrls.isBlank()) {
            addWarn("Elasticsearch URLs not configured, appender will not start");
            return;
        }
        if (encoder == null) {
            addError("Encoder is required but not set");
            return;
        }
        for (String url : elasticsearchUrls.split(",")) {
            String trimmed = url.trim();
            if (!trimmed.isBlank()) {
                if (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
                esUrlsList.add(trimmed);
            }
        }
        if (esUrlsList.isEmpty()) {
            addWarn("No valid ES URLs parsed, appender will not start");
            return;
        }
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectTimeoutSeconds)).build();
        encoder.start();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "es-log-flusher");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::flushBatch, flushIntervalSeconds, flushIntervalSeconds, TimeUnit.SECONDS);
        if (verbose) addInfo("ElasticsearchLogbackAppender started, targets: " + esUrlsList);
        super.start();
    }

    @Override
    public void stop() {
        if (!isStarted()) return;
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(flushIntervalSeconds + 2, TimeUnit.SECONDS))
                    scheduler.shutdownNow();
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        flushBatch();
        if (encoder != null) encoder.stop();
        super.stop();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!isStarted()) return;
        eventQueue.offer(event);
    }

    private void flushBatch() {
        if (eventQueue.isEmpty()) return;
        List<ILoggingEvent> batch = new ArrayList<>(batchSize);
        eventQueue.drainTo(batch, batchSize);
        if (batch.isEmpty()) return;

        String nowDate = INDEX_DATE_FORMAT.format(Instant.now());
        String indexName = indexPrefix + "-" + nowDate;
        StringBuilder bulkBody = new StringBuilder(batch.size() * 2048);

        for (ILoggingEvent event : batch) {
            byte[] encoded = encoder.encode(event);
            if (encoded == null || encoded.length == 0) continue;
            String json = new String(encoded, StandardCharsets.UTF_8).trim();
            if (json.isEmpty()) continue;
            bulkBody.append("{\"index\":{\"_index\":\"").append(indexName).append("\"}}\n");
            bulkBody.append(json).append("\n");
        }
        if (bulkBody.isEmpty()) return;
        bulkBody.append("\n");
        sendToElasticsearch(bulkBody.toString());
    }

    private void sendToElasticsearch(String bulkBody) {
        for (String esUrl : esUrlsList) {
            if (trySend(esUrl + "/_bulk", bulkBody)) return;
        }
        if (verbose) addWarn("Failed to send log batch to all configured Elasticsearch URLs");
    }

    private boolean trySend(String bulkUrl, String bulkBody) {
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(bulkUrl))
                        .header("Content-Type", "application/x-ndjson")
                        .POST(HttpRequest.BodyPublishers.ofString(bulkBody, StandardCharsets.UTF_8))
                        .timeout(Duration.ofSeconds(readTimeoutSeconds))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 || response.statusCode() == 201) {
                    if (verbose && response.body() != null && response.body().contains("\"errors\":true")) {
                        addWarn("ES bulk response contains errors");
                    }
                    return true;
                }
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    addError("ES authentication failed, stopping appender");
                    this.started = false;
                    return false;
                }
                if (verbose) addWarn("ES bulk request failed attempt " + (attempt + 1) + " HTTP " + response.statusCode());
            } catch (IOException e) {
                if (verbose) addWarn("ES connection error attempt " + (attempt + 1) + ": " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                if (verbose) addWarn("ES unexpected error: " + e.getMessage());
            }
            if (attempt < maxRetries) {
                try { Thread.sleep((long) Math.pow(2, attempt) * 1000L); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
            }
        }
        return false;
    }
}
