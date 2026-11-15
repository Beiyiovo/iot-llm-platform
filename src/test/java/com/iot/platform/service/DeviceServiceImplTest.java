package com.iot.platform.service;

import com.iot.platform.entity.Device;
import com.iot.platform.mapper.DeviceMapper;
import com.iot.platform.mapper.TelemetryMapper;
import com.iot.platform.service.impl.DeviceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 一机一密是设备接入的安全底线，这里把四种边界都钉住：
 * 正确、错误、设备不存在、设备被停用。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("设备凭据校验")
class DeviceServiceImplTest {

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private TelemetryMapper telemetryMapper;

    private DeviceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeviceServiceImpl(deviceMapper, telemetryMapper);
    }

    private Device device(String secret, String status) {
        Device d = new Device();
        d.setId(1L);
        d.setDeviceKey("dev_test");
        d.setSecret(secret);
        d.setStatus(status);
        return d;
    }

    private byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("密钥正确则放行")
    void shouldAcceptCorrectSecret() {
        when(deviceMapper.selectOne(any())).thenReturn(device("s3cret", "ONLINE"));
        assertTrue(service.verifyCredential("dev_test", bytes("s3cret")));
    }

    @Test
    @DisplayName("密钥错误则拒绝")
    void shouldRejectWrongSecret() {
        when(deviceMapper.selectOne(any())).thenReturn(device("s3cret", "ONLINE"));
        assertFalse(service.verifyCredential("dev_test", bytes("wrong")));
    }

    @Test
    @DisplayName("设备不存在则拒绝")
    void shouldRejectUnknownDevice() {
        when(deviceMapper.selectOne(any())).thenReturn(null);
        assertFalse(service.verifyCredential("dev_ghost", bytes("s3cret")));
    }

    @Test
    @DisplayName("设备被停用则拒绝")
    void shouldRejectDisabledDevice() {
        when(deviceMapper.selectOne(any())).thenReturn(device("s3cret", "DISABLED"));
        assertFalse(service.verifyCredential("dev_test", bytes("s3cret")));
    }

    @Test
    @DisplayName("空密码直接拒绝，不查库")
    void shouldRejectEmptySecret() {
        assertFalse(service.verifyCredential("dev_test", new byte[0]));
        assertFalse(service.verifyCredential(null, bytes("x")));
    }
}
