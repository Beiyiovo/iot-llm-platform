package com.iot.platform.controller;

import com.iot.platform.common.Result;
import com.iot.platform.service.AlarmService;
import com.iot.platform.service.DeviceService;
import com.iot.platform.service.DeviceShadowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "06 · 看板")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DeviceService deviceService;
    private final AlarmService alarmService;
    private final DeviceShadowService shadowService;

    @Operation(summary = "总览：设备数 / 告警数 / 24h 数据点 / 影子更新次数")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("devices", deviceService.stats());
        m.put("alarms", alarmService.stats());
        m.put("shadowUpdates", shadowService.totalUpdates());
        m.put("serverTime", LocalDateTime.now());
        return Result.ok(m);
    }
}
