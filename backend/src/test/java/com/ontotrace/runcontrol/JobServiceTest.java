package com.ontotrace.runcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ontotrace.web.ApiExceptionHandler.UnprocessableException;
import java.io.InterruptedIOException;
import org.junit.jupiter.api.Test;

/**
 * 任务失败摘要与重试分类。
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

    /**
     * 网络、超时与模型输出解析失败可重试；输入与状态类错误重试无意义。
     */
    @Test
    void retryClassification() {
        assertTrue(JobService.isRetryable(new RuntimeException(new InterruptedIOException("timeout"))));
        assertTrue(JobService.isRetryable(new IllegalArgumentException("无法解析 EDU JSON")));
        assertFalse(JobService.isRetryable(new UnprocessableException("请先完成 TXT/Markdown 上传")));
        assertFalse(JobService.isRetryable(new IllegalStateException("任务指定的文本单元不在该版本中")));
    }
}
