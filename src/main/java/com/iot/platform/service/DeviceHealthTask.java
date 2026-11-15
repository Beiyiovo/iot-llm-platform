package com.iot.platform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 周期性任务。目前只有一个：把长时间没上报的设备标成离线。
 *
 * <p>为什么不靠 MQTT 的 onDisconnect：移动网络下设备经常"假在线"——TCP 连接没断，
 * 但数据早就不发了。所以判定在线的依据必须是"最近有没有数据"，不是"连接还在不在"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceHealthTask {

    private final DeviceService deviceService;

    /** 3 分钟没有上报就算离线：模拟器 15 秒一次，留了 12 个周期的余量 */
    private static final int OFFLINE_TIMEOUT_SECONDS = 180;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void markOffline() {
        int n = deviceService.markOfflineStale(OFFLINE_TIMEOUT_SECONDS);
        if (n > 0) {
            log.info("{} 台设备超过 {} 秒无上报，已标记离线", n, OFFLINE_TIMEOUT_SECONDS);
        }
    }
}
