package com.ontotrace.runcontrol;

import com.ontotrace.config.OntoTraceProperties;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定期领取任务，交给虚拟线程并发执行。
 *
 * @author hanbd
 */
@Slf4j
@Component
public class JobWorker {

    private final JobService jobService;
    private final OntoTraceProperties properties;
    private final String workerId = UUID.randomUUID().toString();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore permits;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * 创建工作进程。
     *
     * @param jobService 任务服务
     * @param properties 运行参数
     */
    public JobWorker(JobService jobService, OntoTraceProperties properties) {
        this.jobService = jobService;
        this.properties = properties;
        this.permits = new Semaphore(Math.max(1, properties.getWorker().getMaxConcurrency()));
        log.info(
                "job worker started workerId={} maxConcurrency={}",
                workerId,
                properties.getWorker().getMaxConcurrency());
    }

    /**
     * 续租在途任务，并在空闲许可上领取新任务。
     */
    @Scheduled(fixedDelayString = "${ontotrace.worker.poll-interval-ms:2000}")
    public void poll() {
        if (!properties.getWorker().isEnabled()) {
            return;
        }
        try {
            renewInFlightLeases();
            dispatchAvailable();
        } catch (Exception ex) {
            log.warn("worker poll failed workerId={}", workerId, ex);
        }
    }

    /**
     * 在许可范围内领取并投递任务。调度线程和任务结束回调都会调用。
     */
    void dispatchAvailable() {
        if (!properties.getWorker().isEnabled()) {
            return;
        }
        while (permits.tryAcquire()) {
            Job claimed;
            try {
                claimed = jobService.claim(workerId);
            } catch (RuntimeException ex) {
                permits.release();
                throw ex;
            }
            if (claimed == null) {
                permits.release();
                return;
            }
            if (!inFlight.add(claimed.getId())) {
                permits.release();
                log.warn("skip already in-flight jobId={} workerId={}", claimed.getId(), workerId);
                continue;
            }
            try {
                executor.submit(() -> runClaimed(claimed));
            } catch (RejectedExecutionException ex) {
                inFlight.remove(claimed.getId());
                permits.release();
                log.warn("executor rejected jobId={} workerId={}", claimed.getId(), workerId, ex);
            }
        }
    }

    /**
     * 停止接收新任务。未完成的任务靠租约被其它进程收回。
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        log.info("job worker stopping workerId={}", workerId);
    }

    private void runClaimed(Job claimed) {
        try {
            jobService.execute(claimed);
        } finally {
            inFlight.remove(claimed.getId());
            permits.release();
            try {
                dispatchAvailable();
            } catch (Exception ex) {
                log.warn("worker refill failed workerId={}", workerId, ex);
            }
        }
    }

    private void renewInFlightLeases() {
        if (inFlight.isEmpty()) {
            return;
        }
        jobService.renewLeases(workerId, List.copyOf(inFlight));
    }
}
