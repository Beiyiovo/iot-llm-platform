package com.iot.platform.dto;

/**
 * 登录返回。前端把 token 放 localStorage，后续请求带 Authorization: Bearer xxx。
 */
public record LoginResponse(
        String token,
        String tokenType,
        long expiresInMinutes,
        Long userId,
        String username,
        String nickname,
        String role
) {
    public static LoginResponse of(String token, long expireMinutes, Long id, String username, String nickname, String role) {
        return new LoginResponse(token, "Bearer", expireMinutes, id, username, nickname, role);
    }
}
