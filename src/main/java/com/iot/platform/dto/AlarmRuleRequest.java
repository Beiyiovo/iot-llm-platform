package com.iot.platform.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 建告警规则。durationPoints 是防抖点位数：连续 N 个点越线才报警。
 */
public record AlarmRuleRequest(
        @NotNull(message = "deviceId 不能为空") Long deviceId,
        @NotBlank(message = "metric 不能为空") String metric,
        @Pattern(regexp = "GT|LT|GE|LE", message = "operator 只能是 GT/LT/GE/LE") String operator,
        @NotNull(message = "threshold 不能为空") @DecimalMin(value = "-1e9") Double threshold,
        @Min(1) @Max(100) Integer durationPoints,
        @Pattern(regexp = "INFO|WARN|CRITICAL", message = "level 只能是 INFO/WARN/CRITICAL") String level
) {
    public int pointsOrDefault() {
        return durationPoints == null ? 3 : durationPoints;
    }

    public String levelOrDefault() {
        return level == null ? "WARN" : level;
    }
}
