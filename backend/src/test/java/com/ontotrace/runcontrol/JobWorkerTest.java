package com.ontotrace.runcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ontotrace.config.OntoTraceProperties;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 虚拟线程 worker 的并发上限与完成后立即补槽。
 *
 * @author hanbd
 */
class JobWorkerTest {

    /**
     * 同时进入 execute 的任务数不超过 maxConcurrency。
     */
    @Test
    void capsConcurrentExecute() throws Exception {
        JobService jobService = mock(JobService.class);
        Queue<Job> pending = jobs(4);
        when(jobService.claim(anyString())).thenAnswer(invocation -> pending.poll());

        AtomicInteger current = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        CountDownLatch finished = new CountDownLatch(4);
        doAnswer(invocation -> {
                    int n = current.incrementAndGet();
                    peak.accumulateAndGet(n, Math::max);
                    try {
                        Thread.sleep(150);
                    } finally {
                        current.decrementAndGet();
                        finished.countDown();
                    }
                    return null;
                })
                .when(jobService)
                .execute(any());

        JobWorker worker = new JobWorker(jobService, workerProperties(2));
        try {
            worker.dispatchAvailable();
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            assertEquals(2, peak.get());
            verify(jobService, times(4)).execute(any());
        } finally {
            worker.shutdown();
        }
    }

    /**
     * 任务结束后立刻领取下一条，不必等下一次 poll。
     */
    @Test
    void refillsSlotWhenJobFinishes() throws Exception {
        JobService jobService = mock(JobService.class);
        Queue<Job> pending = jobs(2);
        when(jobService.claim(anyString())).thenAnswer(invocation -> pending.poll());

        CountDownLatch finished = new CountDownLatch(2);
        doAnswer(invocation -> {
                    Thread.sleep(50);
                    finished.countDown();
                    return null;
                })
                .when(jobService)
                .execute(any());

        JobWorker worker = new JobWorker(jobService, workerProperties(1));
        try {
            worker.dispatchAvailable();
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            verify(jobService, times(2)).execute(any());
        } finally {
            worker.shutdown();
        }
    }

    private static OntoTraceProperties workerProperties(int maxConcurrency) {
        OntoTraceProperties properties = new OntoTraceProperties();
        properties.getWorker().setEnabled(true);
        properties.getWorker().setMaxConcurrency(maxConcurrency);
        return properties;
    }

    private static Queue<Job> jobs(int count) {
        ConcurrentLinkedQueue<Job> pending = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < count; i++) {
            pending.add(Job.builder().id(UUID.randomUUID()).type("extract_content").status("pending").build());
        }
        return pending;
    }
}
