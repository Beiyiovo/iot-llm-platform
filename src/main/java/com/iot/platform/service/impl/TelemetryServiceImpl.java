package com.iot.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iot.platform.entity.Device;
import com.iot.platform.entity.Telemetry;
import com.iot.platform.mapper.TelemetryMapper;
import com.iot.platform.service.TelemetryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TelemetryServiceImpl extends ServiceImpl<TelemetryMapper, Telemetry> implements TelemetryService {

    @Override
    public int ingest(Device device, Map<String, Double> metrics, LocalDateTime ts) {
        if (metrics == null || metrics.isEmpty()) {
            return 0;
        }
        List<Telemetry> batch = new ArrayList<>(metrics.size());
        for (Map.Entry<String, Double> e : metrics.entrySet()) {
            Telemetry t = new Telemetry();
            t.setDeviceId(device.getId());
            t.setDeviceKey(device.getDeviceKey());
            t.setMetric(e.getKey());
            t.setValue(e.getValue());
            t.setTs(ts);
            batch.add(t);
        }
        saveBatch(batch);
        return batch.size();
    }

    @Override
    public List<Telemetry> query(Long deviceId, String metric, LocalDateTime from, LocalDateTime to, int limit) {
        return list(new LambdaQueryWrapper<Telemetry>()
                .eq(Telemetry::getDeviceId, deviceId)
                .eq(metric != null && !metric.isBlank(), Telemetry::getMetric, metric)
                .ge(from != null, Telemetry::getTs, from)
                .le(to != null, Telemetry::getTs, to)
                .orderByAsc(Telemetry::getTs)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 5000)));
    }

    @Override
    public List<Map<String, Object>> summary(Long deviceId, int hours) {
        return baseMapper.selectAggregation(deviceId, LocalDateTime.now().minusHours(hours));
    }

    @Override
    public List<Telemetry> recent(Long deviceId, String metric, int limit) {
        return baseMapper.selectRecent(deviceId, metric, limit);
    }

    @Override
    public Map<String, Double> latestValues(Long deviceId) {
        // 每个指标取最后一条：先按 metric 分组拿最大 ts，再回查。
        // 数据量小的时候够用，真上规模应该用设备影子或降采样表。
        List<Telemetry> recent = list(new LambdaQueryWrapper<Telemetry>()
                .eq(Telemetry::getDeviceId, deviceId)
                .orderByDesc(Telemetry::getTs)
                .last("LIMIT 200"));
        Map<String, Double> map = new LinkedHashMap<>();
        for (Telemetry t : recent) {
            map.putIfAbsent(t.getMetric(), t.getValue());
        }
        return map;
    }
}
