package com.iot.platform.controller;

import com.iot.platform.ai.AiDiagnosisService;
import com.iot.platform.ai.DiagnosisResult;
import com.iot.platform.common.PageResult;
import com.iot.platform.common.Result;
import com.iot.platform.entity.Alarm;
import com.iot.platform.security.LoginUser;
import com.iot.platform.service.AlarmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "04 · 告警工单")
@RestController
@RequestMapping("/api/alarms")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmService alarmService;
    private final AiDiagnosisService aiDiagnosisService;

    @Operation(summary = "告警分页列表")
    @GetMapping
    public Result<PageResult<Alarm>> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) Long deviceId,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        var p = alarmService.page(status, deviceId, page, size);
        return Result.ok(PageResult.of(p, a -> a));
    }

    @Operation(summary = "告警详情")
    @GetMapping("/{id}")
    public Result<Alarm> detail(@PathVariable Long id) {
        return Result.ok(alarmService.getById(id));
    }

    @Operation(summary = "认领告警（TRIGGERED → ACKED）")
    @PostMapping("/{id}/ack")
    public Result<Alarm> ack(@PathVariable Long id, @AuthenticationPrincipal LoginUser user) {
        return Result.ok(alarmService.ack(id, user.username()));
    }

    @Operation(summary = "关闭告警（ACKED → CLOSED）", description = "必须先认领，保证有人对处置负责")
    @PostMapping("/{id}/close")
    public Result<Alarm> close(@PathVariable Long id, @AuthenticationPrincipal LoginUser user) {
        return Result.ok(alarmService.close(id, user.username()));
    }

    @Operation(summary = "重新跑一次 AI 诊断",
            description = "告警产生时会自动诊断一次；这个接口用于人工重跑。force=true 可绕过 10 分钟结果缓存")
    @PostMapping("/{id}/diagnose")
    public Result<DiagnosisResult> diagnose(@PathVariable Long id,
                                            @RequestParam(defaultValue = "false") boolean force) {
        return Result.ok(aiDiagnosisService.diagnoseAndSave(id, force));
    }
}
