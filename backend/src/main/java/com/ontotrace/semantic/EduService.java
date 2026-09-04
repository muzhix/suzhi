package com.ontotrace.semantic;

import com.ontotrace.document.DocumentService;
import com.ontotrace.document.DocumentVersion;
import com.ontotrace.document.DocumentVersionRepository;
import com.ontotrace.security.CurrentUser;
import com.ontotrace.semantic.persistence.EduArgumentRepository;
import com.ontotrace.semantic.persistence.EduRepository;
import com.ontotrace.semantic.persistence.EduSourceRefRepository;
import com.ontotrace.web.ApiExceptionHandler.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * EDU 读取。
 *
 * @author hanbd
 */
@Service
public class EduService {

    private final DocumentService documents;
    private final DocumentVersionRepository versions;
    private final EduRepository edus;
    private final EduSourceRefRepository sources;
    private final EduArgumentRepository arguments;

    /**
     * 创建服务。
     *
     * @param documents 文档服务
     * @param versions 版本文仓
     * @param edus EDU 仓储
     * @param sources 来源仓储
     * @param arguments 参数仓储
     */
    public EduService(
            DocumentService documents,
            DocumentVersionRepository versions,
            EduRepository edus,
            EduSourceRefRepository sources,
            EduArgumentRepository arguments) {
        this.documents = documents;
        this.versions = versions;
        this.edus = edus;
        this.sources = sources;
        this.arguments = arguments;
    }

    /**
     * 读取固定版本可见 EDU。
     *
     * @param user 当前用户
     * @param versionId 版本标识
     * @return EDU 详情
     */
    public List<EduDetail> list(CurrentUser user, UUID versionId) {
        DocumentVersion version = versions.findById(versionId).orElseThrow(() -> new NotFoundException("文档版本不存在"));
        documents.requireView(user, version.getDocumentId());
        List<Edu> rows = edus.findVisible(versionId);
        List<UUID> ids = rows.stream().map(Edu::getId).toList();
        Map<UUID, List<EduSourceRef>> sourceMap = ids.isEmpty()
                ? Map.of()
                : sources.findByEduIdIn(ids).stream().collect(Collectors.groupingBy(EduSourceRef::getEduId));
        Map<UUID, List<EduArgument>> argMap = ids.isEmpty()
                ? Map.of()
                : arguments.findByEduIdIn(ids).stream().collect(Collectors.groupingBy(EduArgument::getEduId));
        return rows.stream()
                .map(edu -> new EduDetail(
                        edu, sourceMap.getOrDefault(edu.getId(), List.of()), argMap.getOrDefault(edu.getId(), List.of())))
                .toList();
    }

    /**
     * EDU 详情。
     *
     * @param edu EDU
     * @param sources 来源
     * @param arguments 参数
     */
    public record EduDetail(Edu edu, List<EduSourceRef> sources, List<EduArgument> arguments) {}
}
