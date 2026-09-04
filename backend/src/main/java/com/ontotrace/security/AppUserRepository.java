package com.ontotrace.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

/**
 * 用户仓储。
 *
 * @author hanbd
 */
public interface AppUserRepository extends ListCrudRepository<AppUser, UUID> {

    /**
     * 按用户名查找。
     *
     * @param username 用户名
     * @return 用户
     */
    Optional<AppUser> findByUsername(String username);

    /**
     * 按用户名或显示名模糊查找。空关键字返回全部。
     *
     * @param q 已转义的关键字，空串表示不筛选
     * @return 用户列表
     */
    @Query(
            """
            SELECT id, username, display_name, password_hash, status, platform_role, created_at, updated_at
            FROM app_user
            WHERE :q = ''
               OR username ILIKE ('%' || :q || '%') ESCAPE '\\'
               OR display_name ILIKE ('%' || :q || '%') ESCAPE '\\'
            ORDER BY created_at DESC
            """)
    List<AppUser> search(String q);

    /**
     * 是否已有管理员。
     *
     * @param platformRole 平台角色
     * @return 存在返回 true
     */
    boolean existsByPlatformRole(String platformRole);
}
