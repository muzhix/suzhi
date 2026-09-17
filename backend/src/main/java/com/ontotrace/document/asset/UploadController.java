package com.ontotrace.document.asset;

import com.ontotrace.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预签名上传接口。
 *
 * @author hanbd
 */
@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final UploadService uploadService;

    /**
     * 创建控制器。
     *
     * @param uploadService 上传服务
     */
    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    /**
     * 创建上传会话。
     *
     * @param user 当前用户
     * @param request 上传请求
     * @return 预签名信息
     */
    @PostMapping
    public UploadResponse create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CreateUploadRequest request) {
        UploadService.UploadCreated created = uploadService.create(
                user,
                request.documentId(),
                request.filename(),
                request.sizeBytes(),
                request.contentType(),
                request.checksumSha256());
        return new UploadResponse(
                created.uploadId().toString(), created.objectKey(), created.presignedPutUrl(), created.expiresAt());
    }

    /**
     * 完成上传并校验对象。
     *
     * @param user 当前用户
     * @param uploadId 上传标识
     * @return 资产标识
     */
    @PostMapping("/{uploadId}/complete")
    public CompleteResponse complete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID uploadId) {
        return new CompleteResponse(uploadService.complete(user, uploadId).toString());
    }

    /**
     * 创建上传请求。
     *
     * @param documentId 文档标识
     * @param filename 文件名
     * @param sizeBytes 大小
     * @param contentType 浏览器声明类型
     * @param checksumSha256 SHA-256
     */
    public record CreateUploadRequest(
            @NotNull UUID documentId,
            @NotBlank String filename,
            @Positive long sizeBytes,
            String contentType,
            @NotBlank String checksumSha256) {}

    /**
     * 上传响应。
     *
     * @param uploadId 上传标识
     * @param objectKey 对象键
     * @param presignedPutUrl 预签名 URL
     * @param expiresAt 到期时间
     */
    public record UploadResponse(String uploadId, String objectKey, String presignedPutUrl, Instant expiresAt) {}

    /**
     * 完成响应。
     *
     * @param assetId 资产标识
     */
    public record CompleteResponse(String assetId) {}
}
