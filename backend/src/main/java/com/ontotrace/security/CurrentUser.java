package com.ontotrace.security;

import java.util.UUID;

/**
 * 当前登录用户。
 *
 * @param id 用户标识
 * @param username 用户名
 * @param displayName 显示名
 * @param platformRole 平台角色 user 或 admin
 * @author hanbd
 */
public record CurrentUser(UUID id, String username, String displayName, String platformRole) {

    /**
     * 是否为平台管理员。
     *
     * @return 管理员返回 true
     */
    public boolean admin() {
        return "admin".equals(platformRole);
    }
}
