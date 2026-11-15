package com.iot.platform.ai.nl;

import com.iot.platform.entity.Device;
import com.iot.platform.mapper.AlarmMapper;
import com.iot.platform.mapper.DeviceMapper;
import com.iot.platform.service.DeviceShadowService;
import com.iot.platform.service.TelemetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * 自然语言解析的边界。这部分是纯逻辑，最值得测——
 * 解析错了，后面查得再对也是白查。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("自然语言查询 · 意图解析")
class NlQueryServiceTest {

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private AlarmMapper alarmMapper;

    @Mock
    private TelemetryService telemetryService;

    @Mock
    private DeviceShadowService shadowService;

    private NlQueryService service;

    private Device device(long id, String name, String key) {
        Device d = new Device();
        d.setId(id);
        d.setName(name);
        d.setDeviceKey(key);
        d.setStatus("ONLINE");
        return d;
    }

    @BeforeEach
    void setUp() {
        service = new NlQueryService(deviceMapper, alarmMapper, telemetryService, shadowService);
        lenient().when(deviceMapper.selectList(any())).thenReturn(List.of(
                device(1L, "3号楼网关", "dev_demo_gw01"),
                device(2L, "冷库温控", "dev_demo_cold01"),
                device(3L, "车间配电柜", "dev_demo_power01")));
    }

    @Test
    @DisplayName("「3号楼网关最近1小时温度最高多少」→ 遥测统计 + 正确设备与指标")
    void shouldParseTelemetryStats() {
        QueryIntent i = service.parse("3号楼网关最近1小时温度最高多少");
        assertEquals(QueryIntent.Type.TELEMETRY_STATS, i.type());
        assertEquals(1L, i.deviceId());
        assertEquals("temperature", i.metric());
        assertNotNull(i.from());
    }

    @Test
    @DisplayName("「冷库温控昨天掉线几次」→ 告警查询 + 昨天的时间窗")
    void shouldParseAlarmQueryWithYesterday() {
        QueryIntent i = service.parse("冷库温控昨天掉线几次");
        assertEquals(QueryIntent.Type.ALARM_LIST, i.type());
        assertEquals(2L, i.deviceId());
        assertEquals(LocalDate.now().minusDays(1), i.from().toLocalDate());
        assertEquals(23, i.to().getHour());
    }

    @Test
    @DisplayName("「现在有几台设备在线」→ 设备状态，且不绑定具体设备")
    void shouldParseStatusQuery() {
        QueryIntent i = service.parse("现在有几台设备在线");
        assertEquals(QueryIntent.Type.DEVICE_STATUS, i.type());
        assertNull(i.deviceId());
    }

    @Test
    @DisplayName("问句里只有泛称「网关」也能命中设备")
    void shouldMatchByGenericKeyword() {
        QueryIntent i = service.parse("网关现在温度多少");
        assertEquals(1L, i.deviceId());
    }

    @Test
    @DisplayName("直接写 deviceKey 也能命中")
    void shouldMatchByDeviceKey() {
        QueryIntent i = service.parse("dev_demo_power01 最近2小时电流");
        assertEquals(3L, i.deviceId());
        assertEquals("current", i.metric());
    }

    @Test
    @DisplayName("「最近3天」能解析成 72 小时窗口")
    void shouldParseRecentDays() {
        QueryIntent i = service.parse("3号楼网关最近3天湿度平均多少");
        assertEquals("humidity", i.metric());
        long hours = java.time.Duration.between(i.from(), i.to()).toHours();
        assertEquals(72, hours);
    }

    @Test
    @DisplayName("问句里没有设备时，deviceId 为空而不是乱猜")
    void shouldNotGuessDevice() {
        QueryIntent i = service.parse("温度最高多少");
        assertNull(i.deviceId());
        assertEquals("temperature", i.metric());
    }

    @Test
    @DisplayName("完全无关的问句不抛异常，走兜底意图")
    void shouldNotThrowOnUnknownQuestion() {
        QueryIntent i = service.parse("你好");
        assertNotNull(i.type());
        assertNotNull(i.from());
    }

    @Test
    @DisplayName("时间描述里不出现纳秒")
    void timeDescShouldNotContainNanos() {
        QueryIntent i = service.parse("3号楼网关最近1小时温度");
        assertEquals(false, i.timeDesc().contains("."));
    }
}
