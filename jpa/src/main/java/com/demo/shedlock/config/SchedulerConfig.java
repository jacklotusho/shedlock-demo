package com.demo.shedlock.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "4500ms") // safety net < fixedDelay (5000ms)
public class SchedulerConfig {

    @Value("${task.scheduler.thread-pool-size:4}")
    private int threadPoolSize;

    /**
     * ShedLock JDBC-template provider.
     *
     * Note: shedlock-provider-jpa was removed from ShedLock and does not exist
     * on Maven Central. JdbcTemplateLockProvider is the correct SQL provider —
     * it works alongside JPA/Hibernate with no conflict, using the same DataSource.
     *
     * usingDbTime() uses the DB server clock for lock timestamps, eliminating
     * clock-drift issues across pods (recommended for Kubernetes deployments).
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime() // uses DB clock — avoids pod clock drift
                .build()
        );
    }

    /**
     * Fixed thread pool for task execution.
     * Size controlled by task.scheduler.thread-pool-size in application.yml.
     * Named threads: task-worker-0, task-worker-1, ... for easy log tracing.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService taskExecutorService() {
        return Executors.newFixedThreadPool(threadPoolSize, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("task-worker-" + counter.getAndIncrement());
                t.setDaemon(true); // don't block JVM shutdown
                return t;
            }
        });
    }
}
