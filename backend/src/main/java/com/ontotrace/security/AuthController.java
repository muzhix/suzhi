package com.ontotrace.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录、登出与当前用户。
 *
 * @author hanbd
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    /**
     * 创建控制器。
     *
     * @param authenticationManager 认证管理器
     * @param securityContextRepository Session 仓储
     */
    public AuthController(
            AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * 返回 CSRF 令牌，供 SPA 后续写请求使用。
     *
     * @param token CSRF 令牌
     * @return 令牌与头名称
     */
    @GetMapping("/auth/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    /**
     * 账号密码登录并写入 Session。
     *
     * @param request 登录请求
     * @param httpRequest HTTP 请求
     * @param httpResponse HTTP 响应
     * @return 当前用户
     */
    @PostMapping("/auth/login")
    public MeResponse login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        AppUserDetails details = (AppUserDetails) authentication.getPrincipal();
        CurrentUser currentUser = details.toCurrentUser();
        Authentication sessionAuth = UsernamePasswordAuthenticationToken.authenticated(
                currentUser, null, details.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(sessionAuth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        log.info("user logged in userId={}", currentUser.id());
        return MeResponse.from(currentUser);
    }

    /**
     * 登出并清理 Session。
     *
     * @param httpRequest HTTP 请求
     * @param httpResponse HTTP 响应
     * @param authentication 当前认证
     */
    @PostMapping("/auth/logout")
    public void logout(
            HttpServletRequest httpRequest, HttpServletResponse httpResponse, Authentication authentication) {
        new SecurityContextLogoutHandler().logout(httpRequest, httpResponse, authentication);
    }

    /**
     * 返回当前登录用户。
     *
     * @param user 当前用户
     * @return 用户信息
     */
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal CurrentUser user) {
        return MeResponse.from(user);
    }

    /**
     * 登录请求。
     *
     * @param username 用户名
     * @param password 密码
     */
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    /**
     * 当前用户响应。
     *
     * @param id 用户标识
     * @param username 用户名
     * @param displayName 显示名
     * @param platformRole 平台角色
     */
    public record MeResponse(String id, String username, String displayName, String platformRole) {
        static MeResponse from(CurrentUser user) {
            return new MeResponse(user.id().toString(), user.username(), user.displayName(), user.platformRole());
        }
    }
}
