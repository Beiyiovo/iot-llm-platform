package com.iot.platform.mqtt;

import com.iot.platform.service.DeviceService;
import io.moquette.broker.security.IAuthenticator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 设备接入认证：一机一密。
 *
 * <p>设备连 MQTT 时，clientId 随便填，username 必须填 deviceKey，password 填 secret。
 * 这里查缓存比对，命中才放行；对不上直接拒绝 CONNECT。
 *
 * <p>注意这个方法跑在 Netty 的 IO 线程上，任何慢操作都会拖垮整个 Broker。
 * 所以 DeviceService 内部对凭据做了 Caffeine 缓存，且这里把所有异常都吞掉返回 false——
 * 认证器抛异常会让连接被异常关闭，日志里看不出原因。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceAuthenticator implements IAuthenticator {

    private final DeviceService deviceService;

    @Override
    public boolean checkValid(String clientId, String username, byte[] password) {
        if (username == null || username.isBlank() || password == null || password.length == 0) {
            log.debug("拒绝匿名连接 clientId={}", clientId);
            return false;
        }
        try {
            boolean ok = deviceService.verifyCredential(username, password);
            if (!ok) {
                log.warn("设备认证失败 clientId={} username={}", clientId, username);
            }
            return ok;
        } catch (Exception e) {
            log.error("设备认证异常 clientId={} username={}", clientId, username, e);
            return false;
        }
    }
}
