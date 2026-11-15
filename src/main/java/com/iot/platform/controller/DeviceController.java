package com.iot.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.iot.platform.common.PageResult;
import com.iot.platform.common.Result;
import com.iot.platform.dto.DeviceCreateRequest;
import com.iot.platform.dto.DeviceUpdateRequest;
import com.iot.platform.dto.DeviceVO;
import com.iot.platform.entity.Device;
import com.iot.platform.service.DeviceService;
import com.iot.platform.service.DeviceShadowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "02 · 设备管理")
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceShadowService shadowService;

    @Operation(summary = "设备分页列表")
    @GetMapping
    public Result<PageResult<DeviceVO>> list(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        IPage<Device> p = deviceService.page(keyword, status, page, size);
        return Result.ok(PageResult.of(p, DeviceVO::of));
    }

    @Operation(summary = "设备详情（含 secret，用于配置真机）")
    @GetMapping("/{id}")
    public Result<DeviceVO> detail(@PathVariable Long id) {
        return Result.ok(DeviceVO.of(deviceService.getById(id), true));
    }

    @Operation(summary = "注册设备", description = "需要 ADMIN。不传 secret 则服务端生成")
    @PostMapping
    public Result<DeviceVO> create(@Valid @RequestBody DeviceCreateRequest req) {
        return Result.ok(DeviceVO.of(deviceService.create(req), true));
    }

    @Operation(summary = "修改设备", description = "需要 ADMIN")
    @PutMapping("/{id}")
    public Result<DeviceVO> update(@PathVariable Long id, @Valid @RequestBody DeviceUpdateRequest req) {
        deviceService.update(id, req);
        return Result.ok(DeviceVO.of(deviceService.getById(id), true));
    }

    @Operation(summary = "删除设备（逻辑删除）", description = "需要 ADMIN")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "设备影子", description = "内存里的最新状态快照，不查时序表")
    @GetMapping("/{id}/shadow")
    public Result<Object> shadow(@PathVariable Long id) {
        Device d = deviceService.getById(id);
        var shadow = shadowService.get(d.getDeviceKey());
        return Result.ok(shadow == null ? Map.of("message", "该设备暂无上报") : shadow);
    }
}
