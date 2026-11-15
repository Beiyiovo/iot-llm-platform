package com.iot.platform.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.iot.platform.dto.DeviceCreateRequest;
import com.iot.platform.dto.DeviceUpdateRequest;
import com.iot.platform.entity.Device;

public interface DeviceService {

    /** 创建设备，生成 deviceKey / secret（不传 secret 时） */
    Device create(DeviceCreateRequest req);

    Device getById(Long id);

    Device getByKey(String deviceKey);

    IPage<Device> page(String keyword, String status, int page, int size);

    void update(Long id, DeviceUpdateRequest req);

    void delete(Long id);

    /** 设备上报心跳：更新 last_seen_at / status，带节流 */
    void touch(Long deviceId, java.time.LocalDateTime seenAt);

    /** 把超过 timeoutSeconds 没上报的设备标成 OFFLINE，返回受影响条数 */
    int markOfflineStale(int timeoutSeconds);

    /** 设备凭据校验，供 MQTT 认证器调用（走缓存） */
    boolean verifyCredential(String deviceKey, byte[] secret);

    java.util.Map<String, Object> stats();
}
