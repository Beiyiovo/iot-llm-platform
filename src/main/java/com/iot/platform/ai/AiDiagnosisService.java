package com.iot.platform.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.platform.config.IotProperties;
import com.iot.platform.entity.Alarm;
import com.iot.platform.entity.Device;
import com.iot.platform.entity.Telemetry;
import com.iot.platform.mapper.AlarmMapper;
import com.iot.platform.service.AlarmService;
import com.iot.platform.service.DeviceService;
import com.iot.platform.service.TelemetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * AI 诊断的门面：组装上下文 → 选诊断器 → 控超时 → 兜底降级 → 落库。
 *
 * <p>三条设计约束，都是被现实逼出来的：
 * <ol>
 *     <li><b>模型不能拖垮主流程</b>：调用放在虚拟线程里跑，超时就取消并降级。</li>
 *     <li><b>没有 Key 也要能跑</b>：默认 provider=mock，别人 clone 下来不用申请账号。</li>
 *     <li><b>降级要留痕</b>：结果里带 fallback 标记和模型名，避免把规则输出当成模型输出。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDiagnosisService {

    private final MockDiagnoser mockDiagnoser;
    private final DashScopeDiagnoser dashScopeDiagnoser;
    private final AlarmService alarmService;
    private final DeviceService deviceService;
    private final TelemetryService telemetryService;
    private final AlarmMapper alarmMapper;
    private final IotProperties props;

    /** Java 21 虚拟线程：IO 密集型调用的理想载体，不用维护线程池大小 */
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * 对指定告警做一次诊断并写回数据库。
     */
    public DiagnosisResult diagnoseAndSave(Long alarmId) {
        Alarm alarm = alarmService.getById(alarmId);
        Device device = deviceService.getById(alarm.getDeviceId());
        DiagnosisContext ctx = buildContext(alarm, device);

        DiagnosisResult result = callWithFallback(ctx);
        alarmService.saveDiagnosis(alarmId, result.text(), result.model());
        return result;
    }

    /**
     * 只诊断不落库，用于"预览"或联调。
     */
    public DiagnosisResult diagnose(Alarm alarm, Device device) {
        return callWithFallback(buildContext(alarm, device));
    }

    private DiagnosisResult callWithFallback(DiagnosisContext ctx) {
        boolean useModel = props.getAi().isEnabled()
                && "dashscope".equalsIgnoreCase(props.getAi().getProvider())
                && dashScopeDiagnoser.available();

        if (!useModel) {
            return mockDiagnoser.diagnose(ctx);
        }

        try {
            long timeout = props.getAi().getTimeoutSeconds();
            DiagnosisResult r = CompletableFuture
                    .supplyAsync(() -> dashScopeDiagnoser.diagnose(ctx), virtualExecutor)
                    .orTimeout(timeout, TimeUnit.SECONDS)
                    .join();
            return r;
        } catch (Exception e) {
            log.warn("大模型诊断失败，降级为本地规则。原因: {}", e.getMessage());
            DiagnosisResult fallback = mockDiagnoser.diagnose(ctx);
            return new DiagnosisResult(
                    fallback.text() + "\n\n> 本次由本地规则生成（大模型调用失败：" + brief(e.getMessage()) + "）",
                    fallback.model(), true, fallback.costMs());
        }
    }

    private DiagnosisContext buildContext(Alarm alarm, Device device) {
        int sampleSize = props.getAi().getSampleSize();
        List<Telemetry> samples = telemetryService.recent(alarm.getDeviceId(), alarm.getMetric(), sampleSize);
        Map<String, Double> latest = telemetryService.latestValues(alarm.getDeviceId());

        List<String> others = new ArrayList<>();
        List<Alarm> recent = alarmMapper.selectList(new LambdaQueryWrapper<Alarm>()
                .eq(Alarm::getDeviceId, alarm.getDeviceId())
                .ne(Alarm::getId, alarm.getId())
                .ge(Alarm::getTriggeredAt, alarm.getTriggeredAt().minusHours(24))
                .orderByDesc(Alarm::getTriggeredAt)
                .last("LIMIT 5"));
        for (Alarm a : recent) {
            others.add(String.format("%s %s=%s（%s，%s）",
                    a.getTriggeredAt().format(TIME_FMT), a.getMetric(),
                    a.getMetricValue() == null ? "-" : String.format("%.2f", a.getMetricValue()),
                    a.getLevel(), a.getStatus()));
        }

        return new DiagnosisContext(
                alarm.getId(), device.getId(), device.getName(), device.getDeviceKey(),
                device.getLocation(), alarm.getMetric(),
                alarm.getMetricValue() == null ? 0 : alarm.getMetricValue(),
                alarm.getThreshold() == null ? 0 : alarm.getThreshold(),
                operatorOf(alarm, device), alarm.getLevel(), alarm.getTriggeredAt(),
                samples.stream().map(t -> new DiagnosisContext.Sample(t.getTs(), t.getValue())).toList(),
                latest, others);
    }

    /**
     * 告警表里没存 operator，从规则推不回来时按"高于阈值"处理。
     * 这里留个口子：真要做准，应该在建表时就把 operator 冗余进 alarm。
     */
    private String operatorOf(Alarm alarm, Device device) {
        if (alarm.getMetricValue() != null && alarm.getThreshold() != null) {
            return alarm.getMetricValue() >= alarm.getThreshold() ? "GT" : "LT";
        }
        return "GT";
    }

    private String brief(String msg) {
        if (msg == null) {
            return "unknown";
        }
        return msg.length() > 80 ? msg.substring(0, 80) + "…" : msg;
    }
}
