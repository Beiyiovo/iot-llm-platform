package com.iot.platform.service;

import com.iot.platform.config.IotProperties;
import com.iot.platform.entity.Alarm;
import com.iot.platform.entity.AlarmRule;
import com.iot.platform.entity.Device;
import com.iot.platform.mapper.AlarmRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 告警防抖是这个系统里最容易写错的一段逻辑：
 * 写成"越线就报"会被毛刺刷屏，写成"越线即累计"会漏掉中断后重新计数的语义。
 * 这两个用例就是钉住这个行为的。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("告警引擎 · 连续 N 点防抖")
class AlarmEngineTest {

    @Mock
    private AlarmRuleMapper ruleMapper;

    @Mock
    private AlarmService alarmService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AlarmEngine engine;
    private Device device;

    @BeforeEach
    void setUp() {
        IotProperties props = new IotProperties();
        engine = new AlarmEngine(ruleMapper, alarmService, eventPublisher, props);

        device = new Device();
        device.setId(1L);
        device.setDeviceKey("dev_test");
        device.setName("测试设备");

        AlarmRule rule = new AlarmRule();
        rule.setId(10L);
        rule.setDeviceId(1L);
        rule.setMetric("temperature");
        rule.setOperator("GT");
        rule.setThreshold(60.0);
        rule.setDurationPoints(3);
        rule.setLevel("CRITICAL");
        rule.setEnabled(1);

        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));

        Alarm alarm = new Alarm();
        alarm.setId(100L);
        alarm.setDeviceId(1L);
        // 不是每个用例都会触发告警，这里用 lenient 避免严格桩检查报 UnnecessaryStubbing
        org.mockito.Mockito.lenient()
                .when(alarmService.trigger(any(), any(), anyDouble(), any())).thenReturn(alarm);
    }

    @Test
    @DisplayName("连续 3 个点越线才开单，前两个点不报")
    void shouldTriggerOnlyAfterThreeConsecutiveBreaches() {
        LocalDateTime now = LocalDateTime.now();

        engine.evaluate(device, "temperature", 65.0, now);
        engine.evaluate(device, "temperature", 66.0, now);
        verify(alarmService, never()).trigger(any(), any(), anyDouble(), any());

        engine.evaluate(device, "temperature", 67.0, now);
        verify(alarmService, times(1)).trigger(eq(device), any(), eq(67.0), any());
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("中途回落会清零计数，重新从 1 开始")
    void shouldResetStreakWhenValueFallsBack() {
        LocalDateTime now = LocalDateTime.now();

        engine.evaluate(device, "temperature", 65.0, now);
        engine.evaluate(device, "temperature", 66.0, now);
        // 落回阈值以下
        engine.evaluate(device, "temperature", 42.0, now);
        // 再连续两次越线，累计只有 2，不够 3
        engine.evaluate(device, "temperature", 65.0, now);
        engine.evaluate(device, "temperature", 66.0, now);

        verify(alarmService, never()).trigger(any(), any(), anyDouble(), any());
    }

    @Test
    @DisplayName("不在规则里的指标不参与判定")
    void shouldIgnoreMetricsWithoutRule() {
        engine.evaluate(device, "humidity", 99.0, LocalDateTime.now());
        engine.evaluate(device, "humidity", 99.0, LocalDateTime.now());
        engine.evaluate(device, "humidity", 99.0, LocalDateTime.now());

        verify(alarmService, never()).trigger(any(), any(), anyDouble(), any());
    }

    @Test
    @DisplayName("未越线时计数保持为 0")
    void streakShouldStayZeroWhenBelowThreshold() {
        engine.evaluate(device, "temperature", 30.0, LocalDateTime.now());
        org.junit.jupiter.api.Assertions.assertEquals(0, engine.currentStreak(1L, 10L));
    }
}
