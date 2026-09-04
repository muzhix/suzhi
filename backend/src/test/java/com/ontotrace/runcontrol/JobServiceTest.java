package com.ontotrace.runcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InterruptedIOException;
import org.junit.jupiter.api.Test;

/**
 * 任务失败摘要。
 *
 * @author hanbd
 */
class JobServiceTest {

    /**
     * OkHttp 超时时，摘要指向超时配置，而不是外层的 Request failed。
     */
    @Test
    void timeoutSummary() {
        RuntimeException ex = new RuntimeException("Request failed", new InterruptedIOException("timeout"));
        String summary = JobService.errorSummary(ex);
        assertTrue(summary.contains("超时"));
        assertTrue(summary.contains("AI_CHAT_TIMEOUT"));
    }

    /**
     * 其它异常沿用原消息。
     */
    @Test
    void otherSummary() {
        assertEquals("文档版本不存在", JobService.errorSummary(new IllegalStateException("文档版本不存在")));
    }
}
