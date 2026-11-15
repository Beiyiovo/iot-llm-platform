package com.iot.platform.service.impl;

import com.iot.platform.common.BizException;
import com.iot.platform.entity.Alarm;
import com.iot.platform.entity.AlarmRule;
import com.iot.platform.entity.Device;
import com.iot.platform.mapper.AlarmMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 工单状态机。这里最容易出错的地方不是"能不能改状态"，
 * 而是"能不能跳过中间态"——所以每个越级操作都要有用例钉住。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("告警工单 · 状态机")
class AlarmServiceImplTest {

    @Mock
    private AlarmMapper alarmMapper;

    @InjectMocks
    private AlarmServiceImpl service;

    private Alarm alarm(long id, String status) {
        Alarm a = new Alarm();
        a.setId(id);
        a.setStatus(status);
        a.setDeviceId(1L);
        a.setMetric("temperature");
        a.setMetricValue(65.0);
        a.setThreshold(60.0);
        a.setLevel("CRITICAL");
        a.setTriggeredAt(LocalDateTime.now());
        return a;
    }

    @Test
    @DisplayName("TRIGGERED → ACKED，记下认领人和时间")
    void shouldAckTriggeredAlarm() {
        when(alarmMapper.selectById(1L)).thenReturn(alarm(1L, "TRIGGERED"));
        Alarm r = service.ack(1L, "admin");
        assertEquals("ACKED", r.getStatus());
        assertEquals("admin", r.getAckedBy());
        assertNotNull(r.getAckedAt());
    }

    @Test
    @DisplayName("重复认领返回 409，不能覆盖掉第一个认领人")
    void shouldRejectDoubleAck() {
        when(alarmMapper.selectById(1L)).thenReturn(alarm(1L, "ACKED"));
        BizException e = assertThrows(BizException.class, () -> service.ack(1L, "admin"));
        assertEquals(409, e.getCode());
    }

    @Test
    @DisplayName("跳过认领直接关闭要被拦住")
    void shouldRejectCloseWithoutAck() {
        when(alarmMapper.selectById(1L)).thenReturn(alarm(1L, "TRIGGERED"));
        BizException e = assertThrows(BizException.class, () -> service.close(1L, "admin"));
        assertEquals(409, e.getCode());
        assertEquals(true, e.getMessage().contains("认领"));
    }

    @Test
    @DisplayName("ACKED → CLOSED，记下关闭人")
    void shouldCloseAckedAlarm() {
        when(alarmMapper.selectById(1L)).thenReturn(alarm(1L, "ACKED"));
        Alarm r = service.close(1L, "user1");
        assertEquals("CLOSED", r.getStatus());
        assertEquals("user1", r.getClosedBy());
        assertNotNull(r.getClosedAt());
    }

    @Test
    @DisplayName("已关闭的工单不能再关")
    void shouldRejectDoubleClose() {
        when(alarmMapper.selectById(1L)).thenReturn(alarm(1L, "CLOSED"));
        BizException e = assertThrows(BizException.class, () -> service.close(1L, "admin"));
        assertEquals(409, e.getCode());
    }

    @Test
    @DisplayName("查不存在的工单返回 404")
    void shouldThrowWhenAlarmMissing() {
        when(alarmMapper.selectById(99L)).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.getById(99L));
        assertEquals(404, e.getCode());
    }

    @Test
    @DisplayName("同一规则已有未关闭工单时不再重复开单")
    void shouldNotDuplicateOpenAlarm() {
        Device device = new Device();
        device.setId(1L);
        device.setName("3号楼网关");
        device.setDeviceKey("dev_demo_gw01");

        AlarmRule rule = new AlarmRule();
        rule.setId(10L);
        rule.setMetric("temperature");
        rule.setOperator("GT");
        rule.setThreshold(60.0);
        rule.setDurationPoints(3);
        rule.setLevel("CRITICAL");

        when(alarmMapper.selectCount(any())).thenReturn(1L);

        Alarm r = service.trigger(device, rule, 65.0, LocalDateTime.now());
        assertEquals(null, r, "已有未关闭工单时应当返回 null，不重复开单");
    }

    @Test
    @DisplayName("统计里三个状态的计数分开算")
    void statsShouldSplitByStatus() {
        when(alarmMapper.selectCount(any())).thenReturn(2L, 1L, 5L, 8L);
        Map<String, Object> s = service.stats();
        assertEquals(2L, s.get("triggered"));
        assertEquals(1L, s.get("acked"));
        assertEquals(5L, s.get("closed"));
    }
}
