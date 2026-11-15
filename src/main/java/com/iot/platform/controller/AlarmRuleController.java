package com.iot.platform.controller;

import com.iot.platform.common.Result;
import com.iot.platform.dto.AlarmRuleRequest;
import com.iot.platform.entity.AlarmRule;
import com.iot.platform.service.AlarmRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "05 · 告警规则")
@RestController
@RequestMapping("/api/alarm-rules")
@RequiredArgsConstructor
public class AlarmRuleController {

    private final AlarmRuleService ruleService;

    @Operation(summary = "某设备的规则列表")
    @GetMapping
    public Result<List<AlarmRule>> list(@RequestParam Long deviceId) {
        return Result.ok(ruleService.listByDevice(deviceId));
    }

    @Operation(summary = "新建规则", description = "需要 ADMIN。durationPoints 是防抖点位数")
    @PostMapping
    public Result<AlarmRule> create(@Valid @RequestBody AlarmRuleRequest req) {
        return Result.ok(ruleService.create(req));
    }

    @Operation(summary = "启用/停用规则", description = "需要 ADMIN")
    @PostMapping("/{id}/toggle")
    public Result<AlarmRule> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        return Result.ok(ruleService.toggle(id, enabled));
    }

    @Operation(summary = "删除规则", description = "需要 ADMIN，有未关闭工单时拒绝")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return Result.ok();
    }
}
