package com.iot.platform.dto;

import com.iot.platform.entity.Telemetry;

import java.time.LocalDateTime;

public record TelemetryVO(String metric, double value, LocalDateTime ts) {

    public static TelemetryVO of(Telemetry t) {
        return new TelemetryVO(t.getMetric(), t.getValue(), t.getTs());
    }
}
