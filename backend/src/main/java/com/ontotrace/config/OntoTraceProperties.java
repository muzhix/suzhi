package com.ontotrace.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 溯知运行参数。
 *
 * @author hanbd
 */
@Data
@ConfigurationProperties(prefix = "ontotrace")
public class OntoTraceProperties {

    private final Bootstrap bootstrap = new Bootstrap();
    private final Worker worker = new Worker();
    private final S3 s3 = new S3();
    private final Ai ai = new Ai();

    /**
     * 引导管理员。
     */
    @Data
    public static class Bootstrap {
        private String adminUsername = "admin";
        private String adminPassword = "change-me";
    }

    /**
     * 任务工作进程。maxConcurrency 限制本进程同时执行的任务数，maxAttempts 限制单任务领取次数。
     */
    @Data
    public static class Worker {
        private boolean enabled = true;
        private long pollIntervalMs = 2000;
        private long leaseSeconds = 600;
        private int maxConcurrency = 4;
        private int maxAttempts = 3;
    }

    /**
     * S3 兼容存储。
     */
    @Data
    public static class S3 {
        private String endpoint = "http://localhost:9000";
        private String region = "us-east-1";
        private String bucket = "ontotrace";
        private String accessKey = "minio";
        private String secretKey = "minio12345";
        private boolean pathStyleAccess = true;
    }

    /**
     * 模型接入。mode 为 disabled、stub 或 live。reviewEnabled 单独控制抽取后的模型复核步骤，默认关闭。
     */
    @Data
    public static class Ai {
        private String mode = "disabled";
        private String provider = "openai";
        private String chatModel = "gpt-4o-mini";
        private String reviewModel = "gpt-4o-mini";
        private boolean reviewEnabled = false;
        private String generatePromptVersion = "edu-generate-v1";
        private String reviewPromptVersion = "edu-review-v1";
        private String outputSchemaVersion = "edu-output-v1";
    }
}
