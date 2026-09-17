package com.ontotrace.security;

import com.ontotrace.config.OntoTraceProperties;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 库中没有管理员时创建引导账号。
 *
 * @author hanbd
 */
@Slf4j
@Component
public class AdminBootstrap implements ApplicationRunner {

    private final AppUserRepository appUserRepo;
    private final PasswordEncoder passwordEncoder;
    private final OntoTraceProperties properties;

    /**
     * 创建引导器。
     *
     * @param appUserRepo 用户仓储
     * @param passwordEncoder 密码编码器
     * @param properties 运行参数
     */
    public AdminBootstrap(
            AppUserRepository appUserRepo, PasswordEncoder passwordEncoder, OntoTraceProperties properties) {
        this.appUserRepo = appUserRepo;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    /**
     * 必要时创建管理员。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        if (appUserRepo.existsByPlatformRole("admin")) {
            return;
        }
        Instant now = Instant.now();
        AppUser admin = AppUser.builder()
                .id(UUID.randomUUID())
                .username(properties.getBootstrap().getAdminUsername())
                .displayName("管理员")
                .passwordHash(passwordEncoder.encode(properties.getBootstrap().getAdminPassword()))
                .status("active")
                .platformRole("admin")
                .createdAt(now)
                .updatedAt(now)
                .build();
        appUserRepo.save(admin);
        log.info("bootstrapped admin userId={}", admin.getId());
    }
}
