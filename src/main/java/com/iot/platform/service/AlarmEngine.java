package com.iot.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.iot.platform.config.IotProperties;
import com.iot.platform.entity.Alarm;
import com.iot.platform.entity.AlarmRule;
import com.iot.platform.entity.Device;
import com.iot.platform.event.AlarmCreatedEvent;
import com.iot.platform.mapper.AlarmRuleMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 告警规则引擎。
 *
 * <p>核心是"防抖"：单点越线不告警，必须连续 N 个采样点都越线才开单。
 * 现实里传感器毛刺很多，没有这一层的告警系统基本没法用——运维第一天就会把所有告警静音。
 *
 * <p>计数器放在内存里（Caffeine），因为它是"瞬时状态"，重启丢掉可以接受：
 * 重启后重新数 N 个点即可，不会漏报，只是晚几个采样周期。
 */
@Service
public class AlarmEngine {

    /** 指标越限的连续计数，key = "deviceId:ruleId" */
    private final Cache<String, AtomicInteger> streak;

    /** 规则本身按设备缓存 30 秒，避免每个数据点都查一次库 */
    private final Cache<Long, List<AlarmRule>> ruleCache;

    private final AlarmRuleMapper ruleMapper;
    private final AlarmService alarmService;
    private final ApplicationEventPublisher eventPublisher;

    public AlarmEngine(AlarmRuleMapper ruleMapper,
                       AlarmService alarmService,
                       ApplicationEventPublisher eventPublisher,
                       IotProperties props) {
        this.ruleMapper = ruleMapper;
        this.alarmService = alarmService;
        this.eventPublisher = eventPublisher;
        this.streak = Caffeine.newBuilder()
                .maximumSize(props.getAlarm().getCounterCacheSize())
                .expireAfterAccess(Duration.ofMinutes(30))
                .build();
        this.ruleCache = Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 收到一个遥测点就过一遍规则。
     */
    public void evaluate(Device device, String metric, double value, LocalDateTime ts) {
        for (AlarmRule rule : rulesOf(device.getId())) {
            if (!rule.getMetric().equals(metric)) {
                continue;
            }
            boolean breached = compare(value, rule.getOperator(), rule.getThreshold());
            String key = device.getId() + ":" + rule.getId();

            if (!breached) {
                streak.invalidate(key);
                continue;
            }

            AtomicInteger counter = streak.get(key, k -> new AtomicInteger());
            int n = counter.incrementAndGet();
            int need = rule.getDurationPoints() == null ? 3 : rule.getDurationPoints();
            if (n >= need) {
                // 触发后清零：若工单已开，AlarmService 内部会去重；若已关闭又持续越限，则重新开单
                counter.set(0);
                Alarm alarm = alarmService.trigger(device, rule, value, ts);
                if (alarm != null) {
                    // 事件交给异步监听器跑 AI 诊断，不阻塞这条上报链路
                    eventPublisher.publishEvent(new AlarmCreatedEvent(alarm, device));
                }
            }
        }
    }

    public List<AlarmRule> rulesOf(Long deviceId) {
        return ruleCache.get(deviceId, id -> ruleMapper.selectList(
                new LambdaQueryWrapper<AlarmRule>()
                        .eq(AlarmRule::getDeviceId, id)
                        .eq(AlarmRule::getEnabled, 1)));
    }

    public void evict(Long deviceId) {
        ruleCache.invalidate(deviceId);
    }

    /** 当前连续越限次数，便于调试与演示 */
    public int currentStreak(Long deviceId, Long ruleId) {
        AtomicInteger c = streak.getIfPresent(deviceId + ":" + ruleId);
        return c == null ? 0 : c.get();
    }

    private boolean compare(double value, String op, double threshold) {
        return switch (op) {
            case "GT" -> value > threshold;
            case "LT" -> value < threshold;
            case "GE" -> value >= threshold;
            case "LE" -> value <= threshold;
            default -> false;
        };
    }
}
