package com.iot.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iot.platform.entity.Device;
import com.iot.platform.entity.Telemetry;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface TelemetryService extends IService<Telemetry> {

    /**
     * 一次上报落库。metrics 里每个 key 落一行。
     *
     * @return 实际写入行数
     */
    int ingest(Device device, Map<String, Double> metrics, LocalDateTime ts);

    List<Telemetry> query(Long deviceId, String metric, LocalDateTime from, LocalDateTime to, int limit);

    List<Map<String, Object>> summary(Long deviceId, int hours);

    List<Telemetry> recent(Long deviceId, String metric, int limit);

    Map<String, Double> latestValues(Long deviceId);
}
