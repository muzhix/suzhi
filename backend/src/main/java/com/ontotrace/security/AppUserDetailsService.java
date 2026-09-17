package com.ontotrace.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 按用户名加载登录用户。
 *
 * @author hanbd
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepo;

    /**
     * 创建服务。
     *
     * @param appUserRepo 用户仓储
     */
    public AppUserDetailsService(AppUserRepository appUserRepo) {
        this.appUserRepo = appUserRepo;
    }

    /**
     * 加载用户。
     *
     * @param username 用户名
     * @return 用户详情
     */
    @Override
    public UserDetails loadUserByUsername(String username) {
        AppUser user = appUserRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
        return new AppUserDetails(user);
    }
}
