package com.iot.platform.ai;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 喂给模型的诊断上下文。之所以单独抽一个 record 而不是直接把 Alarm 丢进去，
 * 是因为 Mock 和真模型需要的是同一份"事实"，只是消费方式不同。
 */
public record DiagnosisContext(
        Long alarmId,
        Long deviceId,
        String deviceName,
        String deviceKey,
        String location,
        String metric,
        double value,
        double threshold,
        String operator,
        String level,
        LocalDateTime triggeredAt,
        List<Sample> samples,
        Map<String, Double> latest,
        List<String> recentAlarms
) {

    public record Sample(LocalDateTime ts, double value) {
    }

    /** 超出阈值的百分比，给模型一个"严重程度"的量化依据 */
    public double exceedPercent() {
        if (threshold == 0) {
            return 0;
        }
        return Math.abs(value - threshold) / Math.abs(threshold) * 100;
    }

    public String operatorText() {
        return switch (operator) {
            case "GT" -> "高于";
            case "LT" -> "低于";
            case "GE" -> "不低于";
            case "LE" -> "不高于";
            default -> "越过";
        };
    }

    /** 最近若干点的斜率（每点变化量），用来判断是突发还是持续恶化 */
    public double slope() {
        if (samples.size() < 2) {
            return 0;
        }
        int n = Math.min(6, samples.size());
        List<Sample> tail = samples.subList(samples.size() - n, samples.size());
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = tail.get(i).value();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        double denom = n * sumXX - sumX * sumX;
        if (denom == 0) {
            return 0;
        }
        return (n * sumXY - sumX * sumY) / denom;
    }

    /** 拼成提示词正文 */
    public String toPromptText() {
        StringBuilder sb = new StringBuilder();
        sb.append("设备：").append(deviceName).append("（").append(deviceKey).append("）");
        if (location != null && !location.isBlank()) {
            sb.append("，位置：").append(location);
        }
        sb.append('\n');
        sb.append("告警指标：").append(metric).append('\n');
        sb.append("规则：").append(metric).append(' ').append(operatorText()).append(' ')
                .append(String.format("%.2f", threshold)).append('\n');
        sb.append("触发值：").append(String.format("%.2f", value))
                .append("（偏离阈值 ").append(String.format("%.1f", exceedPercent())).append("%）\n");
        sb.append("级别：").append(level).append('\n');
        sb.append("触发时间：").append(triggeredAt).append('\n');

        if (!latest.isEmpty()) {
            sb.append("\n设备当前各指标：\n");
            latest.forEach((k, v) -> sb.append("- ").append(k).append(": ")
                    .append(String.format("%.2f", v)).append('\n'));
        }

        if (!samples.isEmpty()) {
            sb.append("\n最近 ").append(samples.size()).append(" 个 ").append(metric)
                    .append(" 采样（时间正序，末位为当前值）：\n");
            int from = Math.max(0, samples.size() - 20);
            for (int i = from; i < samples.size(); i++) {
                Sample s = samples.get(i);
                sb.append(s.ts().toLocalTime().withNano(0)).append(' ')
                        .append(String.format("%.2f", s.value())).append('\n');
            }
            sb.append("近期趋势：").append(slope() >= 0 ? "上升" : "下降")
                    .append("，每采样点约 ").append(String.format("%.2f", Math.abs(slope()))).append('\n');
        }

        if (!recentAlarms.isEmpty()) {
            sb.append("\n近 24 小时该设备的其他告警：\n");
            recentAlarms.forEach(a -> sb.append("- ").append(a).append('\n'));
        }
        return sb.toString();
    }
}
