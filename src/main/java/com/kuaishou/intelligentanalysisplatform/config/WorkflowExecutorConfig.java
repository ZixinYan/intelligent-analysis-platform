package com.kuaishou.intelligentanalysisplatform.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class WorkflowExecutorConfig {

    @Bean("workflowIoExecutor")
    public Executor workflowIoExecutor(
            @Value("${analysis.execution.workflow.core-pool-size:16}") int coreSize,
            @Value("${analysis.execution.workflow.max-pool-size:64}") int maxSize,
            @Value("${analysis.execution.workflow.queue-capacity:200}") int queueCapacity,
            @Value("${analysis.execution.workflow.thread-name-prefix:workflow-io-}") String prefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
