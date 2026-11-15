package com.iot.platform.ai.nl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.platform.entity.Alarm;
import com.iot.platform.entity.Device;
import com.iot.platform.entity.Telemetry;
import com.iot.platform.mapper.AlarmMapper;
import com.iot.platform.mapper.DeviceMapper;
import com.iot.platform.service.DeviceShadowService;
import com.iot.platform.service.TelemetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自然语言查设备。
 *
 * <p>解析层用规则（不依赖模型）：设备名、指标别名、时间词、意图词四类特征就能覆盖
 * "3号楼网关昨天掉线几次"这种运维日常问法。好处是离线可用、结果可预期、出错能定位。
 *
 * <p>执行层只做三件事：查聚合、查告警、查影子。范围是封闭的，
 * 就算以后换成模型解析意图，能执行的动作也不会超出这三个。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NlQueryService {

    private final DeviceMapper deviceMapper;
    private final AlarmMapper alarmMapper;
    private final TelemetryService telemetryService;
    private final DeviceShadowService shadowService;

    /** 中文说法 → 数据库里的指标名 */
    private static final Map<String, String> METRIC_ALIAS = new LinkedHashMap<>();

    static {
        METRIC_ALIAS.put("温度", "temperature");
        METRIC_ALIAS.put("气温", "temperature");
        METRIC_ALIAS.put("湿度", "humidity");
        METRIC_ALIAS.put("co2", "co2");
        METRIC_ALIAS.put("二氧化碳", "co2");
        METRIC_ALIAS.put("电流", "current");
        METRIC_ALIAS.put("电压", "voltage");
        METRIC_ALIAS.put("振动", "vibration");
        METRIC_ALIAS.put("压力", "pressure");
        METRIC_ALIAS.put("temperature", "temperature");
        METRIC_ALIAS.put("humidity", "humidity");
        METRIC_ALIAS.put("current", "current");
        METRIC_ALIAS.put("voltage", "voltage");
    }

    private static final Pattern RECENT = Pattern.compile("最近\\s*(\\d+)\\s*(小时|分钟|天|日)");
    private static final Pattern DATE = Pattern.compile("(\\d{1,2})月(\\d{1,2})[日号]");

    public record Answer(String question, String answer, QueryIntent intent, Map<String, Object> data) {
    }

    public Answer ask(String question) {
        QueryIntent intent = parse(question);
        Map<String, Object> data = new LinkedHashMap<>();
        String text = switch (intent.type()) {
            case ALARM_LIST -> answerAlarms(intent, data);
            case TELEMETRY_STATS -> answerTelemetry(intent, data);
            case DEVICE_STATUS -> answerStatus(intent, data);
        };
        return new Answer(question, text, intent, data);
    }

    // ---------------- 解析 ----------------

    public QueryIntent parse(String question) {
        String q = question.trim();
        String lower = q.toLowerCase();

        Device device = matchDevice(q);
        String metric = matchMetric(lower);
        LocalDateTime[] range = matchRange(q);

        QueryIntent.Type type;
        if (containsAny(q, "告警", "报警", "掉线", "几次", "多少条", "工单", "故障")) {
            type = QueryIntent.Type.ALARM_LIST;
        } else if (containsAny(q, "现在", "当前", "状态", "在线", "最新", "怎么样")) {
            type = QueryIntent.Type.DEVICE_STATUS;
        } else if (containsAny(q, "平均", "最大", "最小", "最高", "最低", "统计", "趋势")) {
            type = QueryIntent.Type.TELEMETRY_STATS;
        } else {
            // 兜底：给了指标就查统计，否则查状态
            type = metric != null ? QueryIntent.Type.TELEMETRY_STATS : QueryIntent.Type.DEVICE_STATUS;
        }

        return new QueryIntent(type,
                device == null ? null : device.getId(),
                device == null ? null : device.getName(),
                metric, range[0], range[1], q);
    }

    private Device matchDevice(String q) {
        List<Device> devices = deviceMapper.selectList(null);
        // 先匹配名字，再匹配 deviceKey，取最长匹配，避免"网关"这种泛词误命中
        Device best = null;
        int bestLen = 0;
        for (Device d : devices) {
            if (d.getName() != null && q.contains(d.getName()) && d.getName().length() > bestLen) {
                best = d;
                bestLen = d.getName().length();
            }
        }
        if (best != null) {
            return best;
        }
        for (Device d : devices) {
            if (d.getDeviceKey() != null && q.contains(d.getDeviceKey())) {
                return d;
            }
        }
        // 模糊词：楼/冷库/配电/车间
        for (Device d : devices) {
            String name = d.getName() == null ? "" : d.getName();
            for (String kw : List.of("网关", "冷库", "配电", "车间", "机柜")) {
                if (name.contains(kw) && q.contains(kw)) {
                    return d;
                }
            }
        }
        return null;
    }

    private String matchMetric(String lower) {
        for (Map.Entry<String, String> e : METRIC_ALIAS.entrySet()) {
            if (lower.contains(e.getKey().toLowerCase())) {
                return e.getValue();
            }
        }
        return null;
    }

    private LocalDateTime[] matchRange(String q) {
        LocalDateTime now = LocalDateTime.now();
        Matcher m = RECENT.matcher(q);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            LocalDateTime from = switch (m.group(2)) {
                case "分钟" -> now.minusMinutes(n);
                case "天", "日" -> now.minusDays(n);
                default -> now.minusHours(n);
            };
            return new LocalDateTime[]{from, now};
        }
        if (q.contains("昨天")) {
            LocalDate d = LocalDate.now().minusDays(1);
            return new LocalDateTime[]{d.atStartOfDay(), d.atTime(23, 59, 59)};
        }
        if (q.contains("今天")) {
            return new LocalDateTime[]{LocalDate.now().atStartOfDay(), now};
        }
        if (q.contains("本周") || q.contains("这周")) {
            return new LocalDateTime[]{LocalDate.now().minusDays(6).atStartOfDay(), now};
        }
        Matcher dm = DATE.matcher(q);
        if (dm.find()) {
            LocalDate d = LocalDate.of(LocalDate.now().getYear(),
                    Integer.parseInt(dm.group(1)), Integer.parseInt(dm.group(2)));
            return new LocalDateTime[]{d.atStartOfDay(), d.atTime(23, 59, 59)};
        }
        return new LocalDateTime[]{now.minusHours(24), now};
    }

    // ---------------- 执行 ----------------

    private String answerAlarms(QueryIntent intent, Map<String, Object> data) {
        var qw = new LambdaQueryWrapper<Alarm>()
                .ge(intent.from() != null, Alarm::getTriggeredAt, intent.from())
                .le(intent.to() != null, Alarm::getTriggeredAt, intent.to())
                .eq(intent.deviceId() != null, Alarm::getDeviceId, intent.deviceId())
                .eq(intent.metric() != null, Alarm::getMetric, intent.metric())
                .orderByDesc(Alarm::getTriggeredAt)
                .last("LIMIT 20");
        List<Alarm> list = alarmMapper.selectList(qw);

        data.put("count", list.size());
        data.put("alarms", list);

        if (list.isEmpty()) {
            return String.format("%s %s内没有产生告警。", who(intent), intent.timeDesc());
        }
        long critical = list.stream().filter(a -> "CRITICAL".equals(a.getLevel())).count();
        StringBuilder sb = new StringBuilder(String.format("%s %s内共 %d 条告警（CRITICAL %d 条）：\n",
                who(intent), intent.timeDesc(), list.size(), critical));
        list.stream().limit(5).forEach(a -> sb.append(String.format("- %s %s = %.1f（%s，%s）\n",
                a.getTriggeredAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                a.getMetric(), a.getMetricValue(), a.getLevel(), a.getStatus())));
        if (list.size() > 5) {
            sb.append("… 其余 ").append(list.size() - 5).append(" 条略");
        }
        return sb.toString().trim();
    }

    private String answerTelemetry(QueryIntent intent, Map<String, Object> data) {
        if (intent.deviceId() == null || intent.metric() == null) {
            return "没听懂要查哪台设备的哪个指标。可以说「3号楼网关最近1小时温度最高多少」。";
        }
        List<Telemetry> list = telemetryService.query(intent.deviceId(), intent.metric(),
                intent.from(), intent.to(), 5000);
        data.put("count", list.size());

        if (list.isEmpty()) {
            return String.format("%s %s内没有 %s 数据。", who(intent), intent.timeDesc(), metricName(intent.metric()));
        }
        double sum = 0, max = Double.MIN_VALUE, min = Double.MAX_VALUE;
        Telemetry maxAt = null, minAt = null;
        for (Telemetry t : list) {
            sum += t.getValue();
            if (t.getValue() > max) {
                max = t.getValue();
                maxAt = t;
            }
            if (t.getValue() < min) {
                min = t.getValue();
                minAt = t;
            }
        }
        double avg = sum / list.size();
        data.put("avg", round(avg));
        data.put("max", round(max));
        data.put("min", round(min));

        return String.format("""
                        %s %s内 %s 共 %d 个采样点：
                        平均 %.2f，最高 %.2f（%s），最低 %.2f（%s）。""",
                who(intent), intent.timeDesc(), metricName(intent.metric()), list.size(),
                avg, max, maxAt.getTs().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                min, minAt.getTs().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));
    }

    private String answerStatus(QueryIntent intent, Map<String, Object> data) {
        if (intent.deviceId() == null) {
            long total = deviceMapper.selectCount(null);
            long online = deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getStatus, "ONLINE"));
            data.put("total", total);
            data.put("online", online);
            return String.format("当前共 %d 台设备，其中 %d 台在线。", total, online);
        }
        Device d = deviceMapper.selectById(intent.deviceId());
        var shadow = shadowService.get(d.getDeviceKey());
        data.put("device", d);
        data.put("shadow", shadow);

        if (shadow == null) {
            return String.format("%s 当前 %s，最近没有上报数据。", d.getName(),
                    "ONLINE".equals(d.getStatus()) ? "在线" : "离线");
        }
        List<String> metrics = new ArrayList<>();
        shadow.metrics().forEach((k, v) -> metrics.add(String.format("%s=%.1f", k, v)));
        return String.format("%s 当前%s，最后上报 %s，最新读数：%s。",
                d.getName(), "ONLINE".equals(d.getStatus()) ? "在线" : "离线",
                shadow.lastSeenAt().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                String.join("，", metrics));
    }

    // ---------------- 工具 ----------------

    private String who(QueryIntent intent) {
        return intent.deviceName() == null ? "全部设备" : intent.deviceName();
    }

    private String metricName(String metric) {
        return switch (metric) {
            case "temperature" -> "温度";
            case "humidity" -> "湿度";
            case "co2" -> "CO2";
            case "current" -> "电流";
            case "voltage" -> "电压";
            default -> metric;
        };
    }

    private double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private boolean containsAny(String s, String... kws) {
        for (String k : kws) {
            if (s.contains(k)) {
                return true;
            }
        }
        return false;
    }
}
