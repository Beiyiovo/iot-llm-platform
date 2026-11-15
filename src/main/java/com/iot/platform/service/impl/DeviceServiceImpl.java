package com.iot.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.iot.platform.common.BizException;
import com.iot.platform.dto.DeviceCreateRequest;
import com.iot.platform.dto.DeviceUpdateRequest;
import com.iot.platform.entity.Device;
import com.iot.platform.entity.Telemetry;
import com.iot.platform.mapper.DeviceMapper;
import com.iot.platform.mapper.TelemetryMapper;
import com.iot.platform.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    private final DeviceMapper deviceMapper;
    private final TelemetryMapper telemetryMapper;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** MQTT 认证在 Netty IO 线程上执行，绝不能每次都查库，这里缓存凭据 */
    private final Cache<String, Device> credentialCache = Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    /** 心跳写库节流：同一设备 30 秒内只落一次库 */
    private final Map<Long, Long> lastTouchAt = new ConcurrentHashMap<>();
    private static final long TOUCH_INTERVAL_MS = 30_000;

    @Override
    @Transactional
    public Device create(DeviceCreateRequest req) {
        String deviceKey = "dev_" + randomHex(6);
        while (deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceKey, deviceKey)) > 0) {
            deviceKey = "dev_" + randomHex(6);
        }
        Device d = new Device();
        d.setProductKey(req.productKey());
        d.setDeviceKey(deviceKey);
        d.setSecret(req.secret() != null && !req.secret().isBlank() ? req.secret() : randomHex(16));
        d.setName(req.name());
        d.setLocation(req.location());
        d.setStatus("OFFLINE");
        d.setDeleted(0);
        deviceMapper.insert(d);
        log.info("设备已创建 deviceKey={} name={}", deviceKey, req.name());
        return d;
    }

    @Override
    public Device getById(Long id) {
        Device d = deviceMapper.selectById(id);
        if (d == null) {
            throw BizException.notFound("设备");
        }
        return d;
    }

    @Override
    public Device getByKey(String deviceKey) {
        Device cached = credentialCache.getIfPresent(deviceKey);
        if (cached != null) {
            return cached;
        }
        Device d = deviceMapper.selectOne(new LambdaQueryWrapper<Device>().eq(Device::getDeviceKey, deviceKey));
        if (d != null) {
            credentialCache.put(deviceKey, d);
        }
        return d;
    }

    @Override
    public IPage<Device> page(String keyword, String status, int page, int size) {
        LambdaQueryWrapper<Device> qw = new LambdaQueryWrapper<Device>()
                .eq(status != null && !status.isBlank(), Device::getStatus, status)
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(Device::getName, keyword).or().like(Device::getDeviceKey, keyword))
                .orderByDesc(Device::getCreatedAt);
        return deviceMapper.selectPage(new Page<>(page, size), qw);
    }

    @Override
    @Transactional
    public void update(Long id, DeviceUpdateRequest req) {
        Device d = getById(id);
        if (req.name() != null) {
            d.setName(req.name());
        }
        if (req.location() != null) {
            d.setLocation(req.location());
        }
        if (req.status() != null) {
            d.setStatus(req.status());
        }
        deviceMapper.updateById(d);
        credentialCache.invalidate(d.getDeviceKey());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Device d = getById(id);
        deviceMapper.deleteById(id);
        credentialCache.invalidate(d.getDeviceKey());
    }

    @Override
    public void touch(Long deviceId, LocalDateTime seenAt) {
        long now = System.currentTimeMillis();
        Long last = lastTouchAt.get(deviceId);
        if (last != null && now - last < TOUCH_INTERVAL_MS) {
            return;
        }
        lastTouchAt.put(deviceId, now);
        deviceMapper.update(null, new LambdaUpdateWrapper<Device>()
                .eq(Device::getId, deviceId)
                .set(Device::getLastSeenAt, seenAt)
                .set(Device::getStatus, "ONLINE"));
    }

    @Override
    public int markOfflineStale(int timeoutSeconds) {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(timeoutSeconds);
        return deviceMapper.update(null, new LambdaUpdateWrapper<Device>()
                .eq(Device::getStatus, "ONLINE")
                .lt(Device::getLastSeenAt, threshold)
                .set(Device::getStatus, "OFFLINE"));
    }

    @Override
    public boolean verifyCredential(String deviceKey, byte[] secret) {
        if (deviceKey == null || secret == null || secret.length == 0) {
            return false;
        }
        Device d = getByKey(deviceKey);
        if (d == null || "DISABLED".equals(d.getStatus())) {
            return false;
        }
        // 定长比较，避免时序侧信道
        return MessageDigest.isEqual(d.getSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8), secret);
    }

    @Override
    public Map<String, Object> stats() {
        LocalDateTime from = LocalDateTime.now().minusHours(24);
        Map<String, Object> map = new HashMap<>();
        map.put("total", deviceMapper.selectCount(null));
        map.put("online", deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getStatus, "ONLINE")));
        map.put("offline", deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getStatus, "OFFLINE")));
        map.put("telemetryPoints24h", telemetryMapper.selectCount(
                new LambdaQueryWrapper<Telemetry>().ge(Telemetry::getTs, from)));
        return map;
    }

    private String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }
}
