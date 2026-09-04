package com.ontotrace.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员用户接口。
 *
 * @author hanbd
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService users;

    /**
     * 创建控制器。
     *
     * @param users 用户服务
     */
    public UserController(UserService users) {
        this.users = users;
    }

    /**
     * 列出用户。
     *
     * @param q 用户名或显示名关键字
     * @return 用户列表
     */
    @GetMapping
    public List<UserResponse> list(@RequestParam(defaultValue = "") String q) {
        return users.list(q).stream().map(UserResponse::from).toList();
    }

    /**
     * 创建用户。
     *
     * @param request 创建请求
     * @return 新用户
     */
    @PostMapping
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return UserResponse.from(
                users.create(request.username(), request.displayName(), request.password(), request.platformRole()));
    }

    /**
     * 更新用户。
     *
     * @param userId 用户标识
     * @param request 更新请求
     * @return 更新后的用户
     */
    @PatchMapping("/{userId}")
    public UserResponse update(@PathVariable UUID userId, @RequestBody UpdateUserRequest request) {
        return UserResponse.from(users.update(userId, request.username(), request.status(), request.password()));
    }

    /**
     * 创建用户请求。
     *
     * @param username 用户名
     * @param displayName 显示名
     * @param password 密码
     * @param platformRole 平台角色
     */
    public record CreateUserRequest(
            @NotBlank String username, @NotBlank String displayName, @NotBlank String password, String platformRole) {}

    /**
     * 更新用户请求。
     *
     * @param username 新用户名
     * @param status 状态
     * @param password 新密码
     */
    public record UpdateUserRequest(String username, String status, String password) {}

    /**
     * 用户响应，不含密码。
     *
     * @param id 用户标识
     * @param username 用户名
     * @param displayName 显示名
     * @param status 状态
     * @param platformRole 平台角色
     */
    public record UserResponse(String id, String username, String displayName, String status, String platformRole) {
        static UserResponse from(AppUser user) {
            return new UserResponse(
                    user.getId().toString(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getStatus(),
                    user.getPlatformRole());
        }
    }
}
