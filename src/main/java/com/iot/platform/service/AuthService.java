package com.iot.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.platform.common.BizException;
import com.iot.platform.dto.LoginRequest;
import com.iot.platform.dto.LoginResponse;
import com.iot.platform.entity.SysUser;
import com.iot.platform.mapper.SysUserMapper;
import com.iot.platform.security.JwtService;
import com.iot.platform.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest req) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.username()));
        // 用户名不存在和密码错误返回同一句话，避免被用来枚举账号
        if (user == null || !passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (user.getEnabled() != null && user.getEnabled() == 0) {
            throw new BizException(403, "账号已被停用");
        }
        LoginUser principal = new LoginUser(user.getId(), user.getUsername(), user.getNickname(), user.getRole());
        String token = jwtService.generate(principal);
        return LoginResponse.of(token, jwtService.getExpireMinutes(),
                user.getId(), user.getUsername(), user.getNickname(), user.getRole());
    }
}
