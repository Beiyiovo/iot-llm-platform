package com.iot.platform.event;

import com.iot.platform.entity.Alarm;
import com.iot.platform.entity.Device;

/**
 * 告警产生事件。
 *
 * <p>为什么用事件而不是在 trigger 里直接调 AI：告警的产生是"同步、必须快"，
 * 诊断是"异步、可以慢、可以失败"。用事件把这两件事拆开，
 * 模型挂了不影响告警落库，告警多了也不会把 MQTT 线程堵住。
 */
public record AlarmCreatedEvent(Alarm alarm, Device device) {
}
