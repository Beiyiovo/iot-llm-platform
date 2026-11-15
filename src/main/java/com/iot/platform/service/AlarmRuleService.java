package com.iot.platform.service;

import com.iot.platform.dto.AlarmRuleRequest;
import com.iot.platform.entity.AlarmRule;

import java.util.List;

public interface AlarmRuleService {

    AlarmRule create(AlarmRuleRequest req);

    List<AlarmRule> listByDevice(Long deviceId);

    AlarmRule toggle(Long id, boolean enabled);

    void delete(Long id);

    /** 规则变更后让引擎的本地缓存立即失效 */
    void evict(Long deviceId);
}
