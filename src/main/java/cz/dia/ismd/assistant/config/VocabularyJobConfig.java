package cz.dia.ismd.assistant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class VocabularyJobConfig {
    @Bean
    ThreadPoolTaskExecutor vocabularyJobExecutor(
            @Value("${app.vocabulary-jobs.workers:2}") int workers,
            @Value("${app.vocabulary-jobs.queue-capacity:20}") int queueCapacity) {
        if (workers < 1 || queueCapacity < 0) {
            throw new IllegalArgumentException("Vocabulary workers must be positive and queue capacity non-negative");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("vocabulary-job-");
        return executor;
    }
}
