package com.iot.platform.security;

/**
 * 登录态里携带的最小用户信息，放进 SecurityContext 的 principal。
 */
public record LoginUser(Long id, String username, String nickname, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
