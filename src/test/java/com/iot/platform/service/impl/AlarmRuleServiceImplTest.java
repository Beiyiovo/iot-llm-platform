package com.iot.platform.service.impl;

import com.iot.platform.common.BizException;
import com.iot.platform.dto.AlarmRuleRequest;
import com.iot.platform.entity.AlarmRule;
import com.iot.platform.mapper.AlarmMapper;
import com.iot.platform.mapper.AlarmRuleMapper;
import com.iot.platform.service.AlarmEngine;
import com.iot.platform.service.DeviceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("告警规则 · 增删改")
class AlarmRuleServiceImplTest {

    @Mock
    private AlarmRuleMapper ruleMapper;

    @Mock
    private AlarmMapper alarmMapper;

    @Mock
    private DeviceService deviceService;

    @Mock
    private AlarmEngine alarmEngine;

    @InjectMocks
    private AlarmRuleServiceImpl service;

    private AlarmRule rule(long id, int enabled) {
        AlarmRule r = new AlarmRule();
        r.setId(id);
        r.setDeviceId(1L);
        r.setMetric("temperature");
        r.setOperator("GT");
        r.setThreshold(60.0);
        r.setDurationPoints(3);
        r.setLevel("WARN");
        r.setEnabled(enabled);
        return r;
    }

    @Test
    @DisplayName("新建规则时设备不存在要报 404，不能建出孤儿规则")
    void shouldRejectWhenDeviceMissing() {
        when(deviceService.getById(9L)).thenThrow(BizException.notFound("设备"));
        AlarmRuleRequest req = new AlarmRuleRequest(9L, "temperature", "GT", 60.0, 3, "WARN");

        BizException e = assertThrows(BizException.class, () -> service.create(req));
        assertEquals(404, e.getCode());
        verify(ruleMapper, never()).insert(any(AlarmRule.class));
    }

    @Test
    @DisplayName("新建规则后要让引擎的规则缓存失效，否则 30 秒内不生效")
    void shouldEvictCacheAfterCreate() {
        AlarmRuleRequest req = new AlarmRuleRequest(1L, "temperature", "GT", 60.0, 3, "WARN");

        service.create(req);

        verify(ruleMapper).insert(any(AlarmRule.class));
        verify(alarmEngine).evict(1L);
    }

    @Test
    @DisplayName("durationPoints 不传时默认 3 个点")
    void shouldDefaultDurationPoints() {
        AlarmRuleRequest req = new AlarmRuleRequest(1L, "temperature", "GT", 60.0, null, null);
        assertEquals(3, req.pointsOrDefault());
        assertEquals("WARN", req.levelOrDefault());
    }

    @Test
    @DisplayName("停用规则要把 enabled 改成 0")
    void shouldToggleOff() {
        when(ruleMapper.selectById(10L)).thenReturn(rule(10L, 1));

        AlarmRule r = service.toggle(10L, false);

        assertEquals(0, r.getEnabled());
        verify(alarmEngine).evict(1L);
    }

    @Test
    @DisplayName("规则还有未关闭工单时不允许删除")
    void shouldRejectDeleteWithOpenAlarm() {
        when(ruleMapper.selectById(10L)).thenReturn(rule(10L, 1));
        when(alarmMapper.selectCount(any())).thenReturn(2L);

        BizException e = assertThrows(BizException.class, () -> service.delete(10L));
        assertEquals(409, e.getCode());
        verify(ruleMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    @DisplayName("没有未关闭工单时可以删除，并清缓存")
    void shouldDeleteWhenNoOpenAlarm() {
        when(ruleMapper.selectById(10L)).thenReturn(rule(10L, 1));
        when(alarmMapper.selectCount(any())).thenReturn(0L);

        service.delete(10L);

        verify(ruleMapper).deleteById(10L);
        verify(alarmEngine).evict(1L);
    }
}
