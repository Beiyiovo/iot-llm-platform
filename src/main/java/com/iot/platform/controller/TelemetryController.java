package com.iot.platform.controller;

import com.iot.platform.common.Result;
import com.iot.platform.dto.TelemetryVO;
import com.iot.platform.service.TelemetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "03 · 遥测数据")
@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryService telemetryService;

    @Operation(summary = "按时间范围查遥测", description = "from/to 格式 2026-11-15T13:00:00，不传则不限")
    @GetMapping
    public Result<List<TelemetryVO>> query(
            @RequestParam Long deviceId,
            @RequestParam(required = false) String metric,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "500") int limit) {
        return Result.ok(telemetryService.query(deviceId, metric, from, to, limit)
                .stream().map(TelemetryVO::of).toList());
    }

    @Operation(summary = "各指标最新值")
    @GetMapping("/latest")
    public Result<Map<String, Double>> latest(@RequestParam Long deviceId) {
        return Result.ok(telemetryService.latestValues(deviceId));
    }

    @Operation(summary = "指标聚合（均值/极值/条数）", description = "给看板顶部的概览卡片用")
    @GetMapping("/summary")
    public Result<List<Map<String, Object>>> summary(@RequestParam Long deviceId,
                                                     @RequestParam(defaultValue = "24") int hours) {
        return Result.ok(telemetryService.summary(deviceId, hours));
    }
}
