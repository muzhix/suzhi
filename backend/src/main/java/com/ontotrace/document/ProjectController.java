package com.ontotrace.document;

import com.ontotrace.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目接口。
 *
 * @author hanbd
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projects;

    /**
     * 创建控制器。
     *
     * @param projects 项目服务
     */
    public ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    /**
     * 创建项目。
     *
     * @param user 当前用户
     * @param request 创建请求
     * @return 项目
     */
    @PostMapping
    public ProjectResponse create(
            @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CreateProjectRequest request) {
        return ProjectResponse.from(projects.create(user, request.name(), request.description()), List.of());
    }

    /**
     * 读取项目。
     *
     * @param user 当前用户
     * @param projectId 项目标识
     * @return 项目
     */
    @GetMapping("/{projectId}")
    public ProjectResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId) {
        ProjectService.ProjectDetail detail = projects.get(user, projectId);
        return ProjectResponse.from(detail.project(), detail.visibleDocumentIds());
    }

    /**
     * 加入文档。
     *
     * @param user 当前用户
     * @param projectId 项目标识
     * @param request 绑定请求
     */
    @PostMapping("/{projectId}/documents")
    public void addDocument(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable UUID projectId,
            @Valid @RequestBody AddProjectDocumentRequest request) {
        projects.addDocument(user, projectId, request.documentId(), request.documentVersionId());
    }

    /**
     * 创建项目请求。
     *
     * @param name 名称
     * @param description 描述
     */
    public record CreateProjectRequest(@NotBlank String name, String description) {}

    /**
     * 绑定文档请求。
     *
     * @param documentId 文档标识
     * @param documentVersionId 版本标识
     */
    public record AddProjectDocumentRequest(@NotNull UUID documentId, UUID documentVersionId) {}

    /**
     * 项目响应。
     *
     * @param id 项目标识
     * @param name 名称
     * @param description 描述
     * @param documentIds 可见文档
     * @param createdAt 创建时间
     */
    public record ProjectResponse(String id, String name, String description, List<String> documentIds, Instant createdAt) {
        static ProjectResponse from(Project project, List<UUID> documentIds) {
            return new ProjectResponse(
                    project.getId().toString(),
                    project.getName(),
                    project.getDescription(),
                    documentIds.stream().map(UUID::toString).toList(),
                    project.getCreatedAt());
        }
    }
}
