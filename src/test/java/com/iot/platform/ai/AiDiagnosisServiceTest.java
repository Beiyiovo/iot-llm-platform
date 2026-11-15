package com.iot.platform.ai;

import com.iot.platform.config.IotProperties;
import com.iot.platform.entity.Alarm;
import com.iot.platform.entity.Device;
import com.iot.platform.mapper.AlarmMapper;
import com.iot.platform.service.AlarmService;
import com.iot.platform.service.DeviceService;
import com.iot.platform.service.TelemetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 诊断降级的三条路径。
 *
 * <p>这三条跑不通，简历上那句"无 API Key 环境也能完整运行"就是空话——
 * 所以这里不是走过场，是把"模型挂了会发生什么"一个个钉死。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AI 诊断 · 降级路径")
class AiDiagnosisServiceTest {

    @Mock
    private MockDiagnoser mockDiagnoser;

    @Mock
    private DashScopeDiagnoser dashScopeDiagnoser;

    @Mock
    private AlarmService alarmService;

    @Mock
    private DeviceService deviceService;

    @Mock
    private TelemetryService telemetryService;

    @Mock
    private AlarmMapper alarmMapper;

    private IotProperties props;
    private AiDiagnosisService service;

    private final DiagnosisResult mockResult =
            new DiagnosisResult("【根因判断】\n本地规则输出", "rule-based-mock", false, 3);

    @BeforeEach
    void setUp() {
        props = new IotProperties();
        props.getAi().setEnabled(true);
        props.getAi().setProvider("mock");
        props.getAi().setTimeoutSeconds(2);

        service = new AiDiagnosisService(mockDiagnoser, dashScopeDiagnoser,
                alarmService, deviceService, telemetryService, alarmMapper, props);

        Alarm alarm = new Alarm();
        alarm.setId(1L);
        alarm.setDeviceId(1L);
        alarm.setMetric("temperature");
        alarm.setMetricValue(65.0);
        alarm.setThreshold(60.0);
        alarm.setLevel("CRITICAL");
        alarm.setTriggeredAt(LocalDateTime.now());

        Device device = new Device();
        device.setId(1L);
        device.setName("3号楼网关");
        device.setDeviceKey("dev_demo_gw01");

        lenient().when(alarmService.getById(1L)).thenReturn(alarm);
        lenient().when(deviceService.getById(1L)).thenReturn(device);
        lenient().when(telemetryService.recent(anyLong(), any(), anyInt())).thenReturn(List.of());
        lenient().when(telemetryService.latestValues(anyLong())).thenReturn(Map.of());
        lenient().when(alarmMapper.selectList(any())).thenReturn(List.of());
        lenient().when(mockDiagnoser.diagnose(any())).thenReturn(mockResult);
    }

    @Test
    @DisplayName("默认配置（provider=mock）不调模型，直接走本地规则")
    void shouldUseMockByDefault() {
        DiagnosisResult r = service.diagnoseAndSave(1L);

        assertEquals("rule-based-mock", r.model());
        assertFalse(r.fallback());
        verify(dashScopeDiagnoser, never()).diagnose(any());
    }

    @Test
    @DisplayName("provider 设成 dashscope 但没配 Key，仍然走本地规则")
    void shouldFallbackWhenNoApiKey() {
        props.getAi().setProvider("dashscope");
        when(dashScopeDiagnoser.available()).thenReturn(false);

        DiagnosisResult r = service.diagnoseAndSave(1L);

        assertEquals("rule-based-mock", r.model());
        verify(dashScopeDiagnoser, never()).diagnose(any());
    }

    @Test
    @DisplayName("模型调用抛异常时降级，并在结果里标 fallback")
    void shouldFallbackWhenModelThrows() {
        props.getAi().setProvider("dashscope");
        when(dashScopeDiagnoser.available()).thenReturn(true);
        when(dashScopeDiagnoser.diagnose(any()))
                .thenThrow(new IllegalStateException("DashScope 调用失败: connect timeout"));

        DiagnosisResult r = service.diagnoseAndSave(1L);

        assertTrue(r.fallback(), "模型失败时必须标记 fallback，否则会把规则输出当成模型输出");
        assertTrue(r.text().contains("本地规则"), "降级说明要写进正文，人工能看出来");
    }

    @Test
    @DisplayName("诊断结果会落库")
    void shouldPersistDiagnosis() {
        service.diagnoseAndSave(1L);
        verify(alarmService).saveDiagnosis(anyLong(), any(), any());
    }

    @Test
    @DisplayName("同一工单第二次调用命中缓存，不重复调模型")
    void shouldHitCacheOnSecondCall() {
        props.getAi().setProvider("dashscope");
        lenient().when(dashScopeDiagnoser.available()).thenReturn(true);
        lenient().when(dashScopeDiagnoser.diagnose(any())).thenReturn(
                new DiagnosisResult("模型输出", "dashscope:qwen-plus", false, 500));

        DiagnosisResult first = service.diagnoseAndSave(1L);
        DiagnosisResult second = service.diagnoseAndSave(1L);

        assertEquals(first.text(), second.text());
        // force=true 才应该绕开缓存
        service.diagnoseAndSave(1L, true);
        verify(alarmService, org.mockito.Mockito.times(2))
                .saveDiagnosis(anyLong(), any(), any());
    }
}
