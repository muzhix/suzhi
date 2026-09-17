package com.ontotrace.document.parser.structure;

import com.ontotrace.document.DocumentService;
import com.ontotrace.document.DocumentVersionRepository;
import com.ontotrace.document.asset.Asset;
import com.ontotrace.document.asset.AssetRepository;
import com.ontotrace.document.asset.S3AssetStore;
import com.ontotrace.document.asset.UploadService;
import com.ontotrace.document.asset.UploadSession;
import com.ontotrace.document.parser.DocumentParser;
import com.ontotrace.document.parser.TextDocumentParser;
import com.ontotrace.security.CurrentUser;
import com.ontotrace.web.ApiExceptionHandler.UnprocessableException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 结构方案列表与试解析。试解析只读已上传对象，不写 document_version。
 *
 * @author hanbd
 */
@Slf4j
@Service
public class DocumentStructureService {

    private final DocumentService documents;
    private final DocumentVersionRepository versions;
    private final UploadService uploads;
    private final AssetRepository assets;
    private final S3AssetStore store;
    private final TextDocumentParser textParser;
    private final TextStructureParser structureParser;
    private final StructureSchemeRegistry schemes;

    /**
     * 创建服务。
     *
     * @param documents 文档服务
     * @param versions 版本文仓
     * @param uploads 上传服务
     * @param assets 资产仓储
     * @param store 对象存储
     * @param textParser 文本导入器
     * @param structureParser 结构解析器
     * @param schemes 预置方案
     */
    public DocumentStructureService(
            DocumentService documents,
            DocumentVersionRepository versions,
            UploadService uploads,
            AssetRepository assets,
            S3AssetStore store,
            TextDocumentParser textParser,
            TextStructureParser structureParser,
            StructureSchemeRegistry schemes) {
        this.documents = documents;
        this.versions = versions;
        this.uploads = uploads;
        this.assets = assets;
        this.store = store;
        this.textParser = textParser;
        this.structureParser = structureParser;
        this.schemes = schemes;
    }

    /**
     * 预置结构方案。
     *
     * @return 方案列表
     */
    public java.util.List<StructureProfile> listSchemes() {
        return schemes.list();
    }

    /**
     * 试解析已上传文本。确认前不得调用版本写入。
     *
     * @param user 当前用户
     * @param documentId 文档标识
     * @param request 方案
     * @return 预览
     */
    public TextStructureParser.ParseResult preview(CurrentUser user, UUID documentId, StructureSchemeRequest request) {
        documents.requireEdit(user, documentId);
        StructureProfile profile = schemes.resolve(
                request == null ? null : request.schemeId(), request == null ? null : request.profile());
        if (profile == null) {
            throw new UnprocessableException("试解析需要结构方案");
        }
        int versionCount = versions.findByDocumentIdOrderByVersionNoDesc(documentId).size();
        TextStructureParser.ParseResult result = structureParser.parse(readUploadedText(documentId), profile);
        int after = versions.findByDocumentIdOrderByVersionNoDesc(documentId).size();
        if (after != versionCount) {
            throw new IllegalStateException("试解析不得写入文档版本");
        }
        log.info(
                "extraction preview documentId={} scheme={} units={} headings={} acceptable={}",
                documentId,
                profile.id(),
                result.units().size(),
                result.headingCount(),
                result.acceptable());
        return result;
    }

    /**
     * 读取文档最近一次完成上传的文本。
     *
     * @param documentId 文档标识
     * @return 标准化文本
     */
    public String readUploadedText(UUID documentId) {
        UploadSession session = uploads.requireCompleted(documentId);
        Asset asset = assets.findById(session.getAssetId()).orElseThrow();
        byte[] bytes = store.getObject(asset.getObjectKey());
        DocumentParser.ParseResult parsed = textParser.parse(new DocumentParser.ParseRequest(
                asset.getObjectKey(), asset.getOriginalFilename(), asset.getContentType(), bytes));
        if (!"succeeded".equals(parsed.status())) {
            throw new UnprocessableException(parsed.error());
        }
        return parsed.text();
    }

    /**
     * 解析请求中的方案；无方案时返回空。
     *
     * @param request 请求
     * @return 方案
     */
    public StructureProfile resolve(StructureSchemeRequest request) {
        if (request == null) {
            return null;
        }
        return schemes.resolve(request.schemeId(), request.profile());
    }
}
