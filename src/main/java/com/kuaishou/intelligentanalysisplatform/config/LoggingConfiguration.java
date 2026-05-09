package com.kuaishou.intelligentanalysisplatform.config;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.kuaishou.intelligentanalysisplatform.infra.observability.ElasticsearchLogbackAppender;
import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.fieldnames.LogstashFieldNames;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class LoggingConfiguration {

    @Value("${spring.application.name:intelligent-analysis-platform}")
    private String applicationName;

    @Value("${elasticsearch.log.enabled:true}")
    private boolean esEnabled;

    @Value("${elasticsearch.log.urls:http://192.168.1.114:9200}")
    private String esUrls;

    @Value("${elasticsearch.log.index-prefix:intelligent-analysis-platform}")
    private String esIndexPrefix;

    @Value("${elasticsearch.log.batch-size:200}")
    private int esBatchSize;

    @Value("${elasticsearch.log.flush-interval-seconds:5}")
    private int esFlushIntervalSeconds;

    @Value("${elasticsearch.log.connect-timeout-seconds:5}")
    private int esConnectTimeoutSeconds;

    @Value("${elasticsearch.log.read-timeout-seconds:10}")
    private int esReadTimeoutSeconds;

    @Value("${elasticsearch.log.max-retries:3}")
    private int esMaxRetries;

    @Value("${elasticsearch.log.verbose:false}")
    private boolean esVerbose;

    @EventListener(ApplicationReadyEvent.class)
    public void configureElasticsearchAppender() {
        if (!esEnabled) {
            return;
        }

        Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        LoggerContext loggerContext = rootLogger.getLoggerContext();

        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setContext(loggerContext);
        LogstashFieldNames fieldNames = new LogstashFieldNames();
        fieldNames.setVersion(LogstashFieldNames.IGNORE_FIELD_INDICATOR);
        fieldNames.setLevelValue(LogstashFieldNames.IGNORE_FIELD_INDICATOR);
        encoder.setFieldNames(fieldNames);
        encoder.addIncludeMdcKeyName("traceId");
        encoder.addIncludeMdcKeyName("tenantId");
        encoder.addIncludeMdcKeyName("userId");
        encoder.addIncludeMdcKeyName("requestUri");
        encoder.addIncludeMdcKeyName("httpMethod");
        encoder.setCustomFields("{\"app\":\"" + applicationName + "\"}");
        encoder.start();

        ElasticsearchLogbackAppender esAppender = new ElasticsearchLogbackAppender();
        esAppender.setElasticsearchUrls(esUrls);
        esAppender.setIndexPrefix(esIndexPrefix);
        esAppender.setBatchSize(esBatchSize);
        esAppender.setFlushIntervalSeconds(esFlushIntervalSeconds);
        esAppender.setConnectTimeoutSeconds(esConnectTimeoutSeconds);
        esAppender.setReadTimeoutSeconds(esReadTimeoutSeconds);
        esAppender.setMaxRetries(esMaxRetries);
        esAppender.setVerbose(esVerbose);
        esAppender.setEncoder(encoder);
        esAppender.setContext(rootLogger.getLoggerContext());
        esAppender.start();

        AsyncAppender asyncAppender = new AsyncAppender();
        asyncAppender.setContext(rootLogger.getLoggerContext());
        asyncAppender.setName("ELASTICSEARCH_ASYNC");
        asyncAppender.addAppender(esAppender);
        asyncAppender.setQueueSize(1024);
        asyncAppender.setDiscardingThreshold(0);
        asyncAppender.setNeverBlock(true);
        asyncAppender.start();

        rootLogger.addAppender(asyncAppender);

        Logger apiAuditLogger = (Logger) LoggerFactory.getLogger("API_AUDIT");
        apiAuditLogger.setAdditive(false);
        apiAuditLogger.addAppender(asyncAppender);

        Logger taskExecLogger = (Logger) LoggerFactory.getLogger("TASK_EXECUTION");
        taskExecLogger.setAdditive(false);
        taskExecLogger.addAppender(asyncAppender);
    }
}
