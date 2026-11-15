package com.iot.platform.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.iot.platform.entity.Alarm;
import com.iot.platform.entity.AlarmRule;
import com.iot.platform.entity.Device;

import java.time.LocalDateTime;
import java.util.Map;

public interface AlarmService {

    /** 规则命中后落地工单（内部会做去重，避免同一规则刷屏） */
    Alarm trigger(Device device, AlarmRule rule, double value, LocalDateTime ts);

    IPage<Alarm> page(String status, Long deviceId, int page, int size);

    Alarm getById(Long id);

    /** TRIGGERED → ACKED */
    Alarm ack(Long id, String operator);

    /** ACKED → CLOSED */
    Alarm close(Long id, String operator);

    void saveDiagnosis(Long id, String text, String model);

    Map<String, Object> stats();
}
