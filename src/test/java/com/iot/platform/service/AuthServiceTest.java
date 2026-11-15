package com.iot.platform.service;

import com.iot.platform.common.BizException;
import com.iot.platform.config.IotProperties;
import com.iot.platform.dto.LoginRequest;
import com.iot.platform.dto.LoginResponse;
import com.iot.platform.entity.SysUser;
import com.iot.platform.mapper.SysUserMapper;
import com.iot.platform.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("登录与 JWT 签发")
class AuthServiceTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        IotProperties props = new IotProperties();
        props.getJwt().setSecret("unit-test-secret-key-must-be-at-least-32-bytes-long");
        props.getJwt().setExpireMinutes(60);
        JwtService jwtService = new JwtService(props);
        authService = new AuthService(userMapper, passwordEncoder, jwtService);
    }

    private SysUser user(int enabled) {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setUsername("admin");
        u.setPassword("$2a$10$hash");
        u.setNickname("管理员");
        u.setRole("ADMIN");
        u.setEnabled(enabled);
        return u;
    }

    @Test
    @DisplayName("密码正确时签发 token，token 里带角色")
    void shouldIssueToken() {
        when(userMapper.selectOne(any())).thenReturn(user(1));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        LoginResponse r = authService.login(new LoginRequest("admin", "123456"));

        assertNotNull(r.token());
        assertEquals("ADMIN", r.role());
        assertTrue(r.expiresInMinutes() > 0);
    }

    @Test
    @DisplayName("密码错误返回 401，不区分用户是否存在")
    void shouldRejectWrongPassword() {
        when(userMapper.selectOne(any())).thenReturn(user(1));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        BizException e = assertThrows(BizException.class,
                () -> authService.login(new LoginRequest("admin", "bad")));
        assertEquals(401, e.getCode());
    }

    @Test
    @DisplayName("用户不存在也返回同一句错误，避免账号枚举")
    void shouldNotRevealUserExistence() {
        when(userMapper.selectOne(any())).thenReturn(null);

        BizException e = assertThrows(BizException.class,
                () -> authService.login(new LoginRequest("ghost", "123456")));
        assertEquals(401, e.getCode());
        assertEquals("用户名或密码错误", e.getMessage());
    }

    @Test
    @DisplayName("停用账号不允许登录")
    void shouldRejectDisabledUser() {
        when(userMapper.selectOne(any())).thenReturn(user(0));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        BizException e = assertThrows(BizException.class,
                () -> authService.login(new LoginRequest("admin", "123456")));
        assertEquals(403, e.getCode());
    }
}
