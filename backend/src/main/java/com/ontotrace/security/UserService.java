package com.ontotrace.security;

import com.ontotrace.web.ApiExceptionHandler.ConflictException;
import com.ontotrace.web.ApiExceptionHandler.NotFoundException;
import com.ontotrace.web.LikeQuery;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员用户管理。
 *
 * @author hanbd
 */
@Slf4j
@Service
public class UserService {

    private final AppUserRepository appUserRepo;
    private final PasswordEncoder passwordEncoder;

    /**
     * 创建服务。
     *
     * @param appUserRepo 用户仓储
     * @param passwordEncoder 密码编码器
     */
    public UserService(AppUserRepository appUserRepo, PasswordEncoder passwordEncoder) {
        this.appUserRepo = appUserRepo;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 列出用户，可按用户名或显示名筛选。
     *
     * @param q 关键字
     * @return 用户列表
     */
    public List<AppUser> list(String q) {
        return appUserRepo.search(LikeQuery.contains(q));
    }

    /**
     * 创建用户。
     *
     * @param username 用户名
     * @param displayName 显示名
     * @param password 明文密码
     * @param platformRole 平台角色
     * @return 新用户
     */
    @Transactional
    public AppUser create(String username, String displayName, String password, String platformRole) {
        appUserRepo.findByUsername(username).ifPresent(existing -> {
            throw new ConflictException("用户名已存在");
        });
        Instant now = Instant.now();
        AppUser user = AppUser.builder()
                .id(UUID.randomUUID())
                .username(username)
                .displayName(displayName)
                .passwordHash(passwordEncoder.encode(password))
                .status("active")
                .platformRole(platformRole == null ? "user" : platformRole)
                .createdAt(now)
                .updatedAt(now)
                .build();
        appUserRepo.save(user);
        log.info("created user userId={} role={}", user.getId(), user.getPlatformRole());
        return user;
    }

    /**
     * 更新用户名、状态或重置密码。
     *
     * @param userId 用户标识
     * @param username 新用户名，可空
     * @param status 新状态，可空
     * @param password 新密码，可空
     * @return 更新后的用户
     */
    @Transactional
    public AppUser update(UUID userId, String username, String status, String password) {
        AppUser user = appUserRepo.findById(userId).orElseThrow(() -> new NotFoundException("用户不存在"));
        user.setNew(false);
        if (username != null && !username.isBlank()) {
            String next = username.trim();
            appUserRepo.findByUsername(next).ifPresent(existing -> {
                if (!existing.getId().equals(userId)) {
                    throw new ConflictException("用户名已存在");
                }
            });
            user.setUsername(next);
        }
        if (status != null) {
            if (!"active".equals(status) && !"disabled".equals(status)) {
                throw new IllegalArgumentException("非法状态");
            }
            user.setStatus(status);
        }
        if (password != null && !password.isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }
        user.setUpdatedAt(Instant.now());
        appUserRepo.save(user);
        log.info("updated user userId={} username={} status={}", userId, user.getUsername(), user.getStatus());
        return user;
    }
}
