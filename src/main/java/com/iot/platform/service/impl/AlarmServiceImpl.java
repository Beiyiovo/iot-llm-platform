package com.iot.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.platform.common.BizException;
import com.iot.platform.entity.Alarm;
import com.iot.platform.entity.AlarmRule;
import com.iot.platform.entity.Device;
import com.iot.platform.mapper.AlarmMapper;
import com.iot.platform.service.AlarmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmServiceImpl implements AlarmService {

    private final AlarmMapper alarmMapper;

    @Override
    @Transactional
    public Alarm trigger(Device device, AlarmRule rule, double value, LocalDateTime ts) {
        // 同一规则如果还有未关闭的工单，就不再重复开单——这是"告警聚合"的最简形式
        Long openCount = alarmMapper.selectCount(new LambdaQueryWrapper<Alarm>()
                .eq(Alarm::getRuleId, rule.getId())
                .ne(Alarm::getStatus, "CLOSED"));
        if (openCount != null && openCount > 0) {
            log.debug("规则 {} 已有未关闭工单，跳过", rule.getId());
            return null;
        }

        Alarm alarm = new Alarm();
        alarm.setDeviceId(device.getId());
        alarm.setRuleId(rule.getId());
        alarm.setMetric(rule.getMetric());
        alarm.setMetricValue(value);
        alarm.setThreshold(rule.getThreshold());
        alarm.setLevel(rule.getLevel());
        alarm.setStatus("TRIGGERED");
        alarm.setMessage(String.format("设备[%s] 的 %s 连续 %d 个采样点%s %.2f（当前 %.2f）",
                device.getName(), rule.getMetric(), rule.getDurationPoints(),
                operatorText(rule.getOperator()), rule.getThreshold(), value));
        alarm.setTriggeredAt(ts);
        alarmMapper.insert(alarm);
        log.warn("告警触发 device={} metric={} value={} rule={}",
                device.getDeviceKey(), rule.getMetric(), value, rule.getId());
        return alarm;
    }

    @Override
    public IPage<Alarm> page(String status, Long deviceId, int page, int size) {
        return alarmMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<Alarm>()
                .eq(status != null && !status.isBlank(), Alarm::getStatus, status)
                .eq(deviceId != null, Alarm::getDeviceId, deviceId)
                .orderByDesc(Alarm::getTriggeredAt));
    }

    @Override
    public Alarm getById(Long id) {
        Alarm a = alarmMapper.selectById(id);
        if (a == null) {
            throw BizException.notFound("告警");
        }
        return a;
    }

    @Override
    @Transactional
    public Alarm ack(Long id, String operator) {
        Alarm a = getById(id);
        if (!"TRIGGERED".equals(a.getStatus())) {
            throw BizException.conflict("只有 TRIGGERED 状态的告警可以认领，当前是 " + a.getStatus());
        }
        a.setStatus("ACKED");
        a.setAckedAt(LocalDateTime.now());
        a.setAckedBy(operator);
        alarmMapper.updateById(a);
        return a;
    }

    @Override
    @Transactional
    public Alarm close(Long id, String operator) {
        Alarm a = getById(id);
        if ("CLOSED".equals(a.getStatus())) {
            throw BizException.conflict("该告警已经关闭");
        }
        if (!"ACKED".equals(a.getStatus())) {
            throw BizException.conflict("请先认领（ACK）再关闭，保证有人对这次处置负责");
        }
        a.setStatus("CLOSED");
        a.setClosedAt(LocalDateTime.now());
        a.setClosedBy(operator);
        alarmMapper.updateById(a);
        return a;
    }

    @Override
    public void saveDiagnosis(Long id, String text, String model) {
        Alarm a = getById(id);
        a.setAiDiagnosis(text);
        a.setAiModel(model);
        alarmMapper.updateById(a);
    }

    @Override
    public Map<String, Object> stats() {
        Map<String, Object> map = new HashMap<>();
        map.put("triggered", alarmMapper.selectCount(new LambdaQueryWrapper<Alarm>().eq(Alarm::getStatus, "TRIGGERED")));
        map.put("acked", alarmMapper.selectCount(new LambdaQueryWrapper<Alarm>().eq(Alarm::getStatus, "ACKED")));
        map.put("closed", alarmMapper.selectCount(new LambdaQueryWrapper<Alarm>().eq(Alarm::getStatus, "CLOSED")));
        map.put("total", alarmMapper.selectCount(null));
        return map;
    }

    private String operatorText(String op) {
        return switch (op) {
            case "GT" -> "高于";
            case "LT" -> "低于";
            case "GE" -> "不低于";
            case "LE" -> "不高于";
            default -> "越过";
        };
    }
}
