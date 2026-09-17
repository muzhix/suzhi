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

    private final DocumentService documentService;
    private final DocumentVersionRepository documentVersionRepo;
    private final EduRepository eduRepo;
    private final EduSourceRefRepository eduSourceRefRepo;
    private final EduArgumentRepository eduArgumentRepo;

    /**
     * 创建服务。
     *
     * @param documentService 文档服务
     * @param documentVersionRepo 版本文仓
     * @param eduRepo EDU 仓储
     * @param eduSourceRefRepo 来源仓储
     * @param eduArgumentRepo 参数仓储
     */
    public EduService(
            DocumentService documentService,
            DocumentVersionRepository documentVersionRepo,
            EduRepository eduRepo,
            EduSourceRefRepository eduSourceRefRepo,
            EduArgumentRepository eduArgumentRepo) {
        this.documentService = documentService;
        this.documentVersionRepo = documentVersionRepo;
        this.eduRepo = eduRepo;
        this.eduSourceRefRepo = eduSourceRefRepo;
        this.eduArgumentRepo = eduArgumentRepo;
    }

    /**
     * 读取固定版本可见 EDU。
     *
     * @param user 当前用户
     * @param versionId 版本标识
     * @return EDU 详情
     */
    public List<EduDetail> list(CurrentUser user, UUID versionId) {
        DocumentVersion version = documentVersionRepo.findById(versionId).orElseThrow(() -> new NotFoundException("文档版本不存在"));
        documentService.requireView(user, version.getDocumentId());
        List<Edu> rows = eduRepo.findVisible(versionId);
        List<UUID> ids = rows.stream().map(Edu::getId).toList();
        Map<UUID, List<EduSourceRef>> sourceMap = ids.isEmpty()
                ? Map.of()
                : eduSourceRefRepo.findByEduIdIn(ids).stream().collect(Collectors.groupingBy(EduSourceRef::getEduId));
        Map<UUID, List<EduArgument>> argMap = ids.isEmpty()
                ? Map.of()
                : eduArgumentRepo.findByEduIdIn(ids).stream().collect(Collectors.groupingBy(EduArgument::getEduId));
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
