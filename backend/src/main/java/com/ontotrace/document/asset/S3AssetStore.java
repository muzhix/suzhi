package com.ontotrace.document.asset;

import com.ontotrace.config.OntoTraceProperties;
import java.net.URI;
import java.time.Duration;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3 兼容对象存储。开发与生产共用 AWS SDK，仅 endpoint 和凭据不同。
 *
 * @author hanbd
 */
@Slf4j
@Component
public class S3AssetStore {

    private final OntoTraceProperties.S3 properties;
    private final S3Client client;
    private final S3Presigner presigner;

    /**
     * 创建存储。
     *
     * @param properties 运行参数
     * @param client S3 客户端
     * @param presigner 预签名器
     */
    public S3AssetStore(OntoTraceProperties properties, S3Client client, S3Presigner presigner) {
        this.properties = properties.getS3();
        this.client = client;
        this.presigner = presigner;
        ensureBucket();
    }

    /**
     * 生成预签名 PUT。
     *
     * @param objectKey 对象键
     * @param contentType 内容类型
     * @return 预签名 URL
     */
    public String presignPut(String objectKey, String contentType) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .build();
        return presigner
                .presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(15))
                        .putObjectRequest(put)
                        .build())
                .url()
                .toString();
    }

    /**
     * 读取对象字节，完成上传时重新校验。
     *
     * @param objectKey 对象键
     * @return 对象内容
     */
    public byte[] getObject(String objectKey) {
        return client.getObjectAsBytes(GetObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(objectKey)
                        .build())
                .asByteArray();
    }

    /**
     * 直接写入对象，供测试或同步导入使用。
     *
     * @param objectKey 对象键
     * @param contentType 内容类型
     * @param bytes 内容
     */
    public void putObject(String objectKey, String contentType, byte[] bytes) {
        client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(objectKey)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(bytes));
    }

    /**
     * 计算 SHA-256 十六进制。
     *
     * @param bytes 内容
     * @return 校验和
     */
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算校验和", ex);
        }
    }

    private void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(properties.getBucket()).build());
        } catch (NoSuchBucketException ex) {
            client.createBucket(CreateBucketRequest.builder().bucket(properties.getBucket()).build());
            log.info("created s3 bucket bucket={}", properties.getBucket());
        } catch (RuntimeException ex) {
            log.warn("s3 bucket check skipped: {}", ex.getMessage());
        }
    }
}
