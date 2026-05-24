package com.demo.shedlock.config;

import io.r2dbc.spi.ConnectionFactory;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.r2dbc.R2dbcLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "4500ms")
public class SchedulerConfig {

    @Value("${task.scheduler.thread-pool-size:4}")
    private int threadPoolSize;

    /**
     * ShedLock R2DBC provider.
     *
     * Uses the reactive ConnectionFactory — no JDBC DataSource needed.
     * The shedlock table is created by schema.sql via the JDBC datasource
     * before R2DBC connections open.
     *
     * Note: @Scheduled is blocking. ShedLock bridges R2DBC (reactive lock
     * acquire) to the blocking scheduler via its internal adapter —
     * no extra wiring needed on our side.
     */
    @Bean
    public LockProvider lockProvider(ConnectionFactory connectionFactory) {
        return new R2dbcLockProvider(connectionFactory);
    }

    /**
     * Dedicated thread pool for blocking provision workers.
     * Named task-worker-N for easy log tracing.
     * Daemon threads — won't block JVM shutdown.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService taskExecutorService() {
        return Executors.newFixedThreadPool(threadPoolSize, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "task-worker-" + counter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });
    }
}
