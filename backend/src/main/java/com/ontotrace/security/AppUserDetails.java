package com.ontotrace.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security 用户详情。
 *
 * @author hanbd
 */
public class AppUserDetails implements UserDetails {

    private final AppUser user;

    /**
     * 包装用户记录。
     *
     * @param user 用户
     */
    public AppUserDetails(AppUser user) {
        this.user = user;
    }

    /**
     * 转为会话中的当前用户。
     *
     * @return 当前用户
     */
    public CurrentUser toCurrentUser() {
        return new CurrentUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getPlatformRole());
    }

    /**
     * 返回授权。
     *
     * @return 角色列表
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getPlatformRole().toUpperCase()));
    }

    /**
     * 返回密码哈希。
     *
     * @return 密码哈希
     */
    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    /**
     * 返回用户名。
     *
     * @return 用户名
     */
    @Override
    public String getUsername() {
        return user.getUsername();
    }

    /**
     * 账号是否可用。
     *
     * @return 启用返回 true
     */
    @Override
    public boolean isEnabled() {
        return "active".equals(user.getStatus());
    }
}
