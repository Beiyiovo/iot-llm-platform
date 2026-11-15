package com.iot.platform.dto;

import com.iot.platform.entity.Device;

import java.time.LocalDateTime;

/**
 * 设备对外视图。secret 只在"刚创建"和"详情"里出现，列表页一律不带。
 */
public record DeviceVO(
        Long id,
        String productKey,
        String deviceKey,
        String name,
        String location,
        String status,
        LocalDateTime lastSeenAt,
        LocalDateTime createdAt,
        String secret
) {
    public static DeviceVO of(Device d) {
        return of(d, false);
    }

    public static DeviceVO of(Device d, boolean withSecret) {
        return new DeviceVO(d.getId(), d.getProductKey(), d.getDeviceKey(), d.getName(),
                d.getLocation(), d.getStatus(), d.getLastSeenAt(), d.getCreatedAt(),
                withSecret ? d.getSecret() : null);
    }
}
