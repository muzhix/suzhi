package com.ontotrace.document;

import com.ontotrace.security.CurrentUser;
import com.ontotrace.web.ApiExceptionHandler.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目用例。项目成员不能提升文档权限，查询只返回同时有文档 ACL 的文档。
 *
 * @author hanbd
 */
@Slf4j
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final DocumentRepository documents;

    /**
     * 创建服务。
     *
     * @param projects 项目仓储
     * @param documents 文档仓储
     */
    public ProjectService(ProjectRepository projects, DocumentRepository documents) {
        this.projects = projects;
        this.documents = documents;
    }

    /**
     * 创建项目，创建者成为所有者。
     *
     * @param user 当前用户
     * @param name 名称
     * @param description 描述
     * @return 项目
     */
    @Transactional
    public Project create(CurrentUser user, String name, String description) {
        Instant now = Instant.now();
        Project project = Project.builder()
                .id(UUID.randomUUID())
                .name(name)
                .description(description)
                .createdBy(user.id())
                .createdAt(now)
                .updatedAt(now)
                .build();
        projects.save(project);
        projects.insertMember(project.getId(), user.id(), "owner");
        log.info("created project projectId={} userId={}", project.getId(), user.id());
        return project;
    }

    /**
     * 读取项目。
     *
     * @param user 当前用户
     * @param projectId 项目标识
     * @return 项目与可见文档
     */
    public ProjectDetail get(CurrentUser user, UUID projectId) {
        Project project = projects.findAccessible(projectId, user.id())
                .orElseThrow(() -> new NotFoundException("项目不存在或无权访问"));
        return new ProjectDetail(project, projects.findVisibleDocumentIds(projectId, user.id()));
    }

    /**
     * 把文档加入项目。调用方必须同时是项目成员且拥有文档权限。
     *
     * @param user 当前用户
     * @param projectId 项目标识
     * @param documentId 文档标识
     * @param documentVersionId 固定版本，可空
     */
    @Transactional
    public void addDocument(CurrentUser user, UUID projectId, UUID documentId, UUID documentVersionId) {
        if (!projects.existsMember(projectId, user.id())) {
            throw new AccessDeniedException("不是项目成员");
        }
        if (!documents.canView(documentId, user.id())) {
            throw new AccessDeniedException("没有该文档权限，不能加入项目");
        }
        projects.upsertDocument(projectId, documentId, documentVersionId);
    }

    /**
     * 项目详情。
     *
     * @param project 项目
     * @param visibleDocumentIds 当前用户同时有权看到的文档
     */
    public record ProjectDetail(Project project, List<UUID> visibleDocumentIds) {}
}
