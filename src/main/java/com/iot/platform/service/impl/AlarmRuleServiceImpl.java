package com.iot.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.platform.common.BizException;
import com.iot.platform.dto.AlarmRuleRequest;
import com.iot.platform.entity.Alarm;
import com.iot.platform.entity.AlarmRule;
import com.iot.platform.mapper.AlarmMapper;
import com.iot.platform.mapper.AlarmRuleMapper;
import com.iot.platform.service.AlarmEngine;
import com.iot.platform.service.AlarmRuleService;
import com.iot.platform.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AlarmRuleServiceImpl implements AlarmRuleService {

    private final AlarmRuleMapper ruleMapper;
    private final AlarmMapper alarmMapper;
    private final DeviceService deviceService;
    private final AlarmEngine alarmEngine;

    @Override
    @Transactional
    public AlarmRule create(AlarmRuleRequest req) {
        deviceService.getById(req.deviceId()); // 不存在直接 404
        AlarmRule rule = new AlarmRule();
        rule.setDeviceId(req.deviceId());
        rule.setMetric(req.metric());
        rule.setOperator(req.operator());
        rule.setThreshold(req.threshold());
        rule.setDurationPoints(req.pointsOrDefault());
        rule.setLevel(req.levelOrDefault());
        rule.setEnabled(1);
        ruleMapper.insert(rule);
        alarmEngine.evict(req.deviceId());
        return rule;
    }

    @Override
    public List<AlarmRule> listByDevice(Long deviceId) {
        return ruleMapper.selectList(new LambdaQueryWrapper<AlarmRule>()
                .eq(AlarmRule::getDeviceId, deviceId)
                .orderByAsc(AlarmRule::getId));
    }

    @Override
    @Transactional
    public AlarmRule toggle(Long id, boolean enabled) {
        AlarmRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw BizException.notFound("告警规则");
        }
        rule.setEnabled(enabled ? 1 : 0);
        ruleMapper.updateById(rule);
        alarmEngine.evict(rule.getDeviceId());
        return rule;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AlarmRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw BizException.notFound("告警规则");
        }
        Long open = alarmMapper.selectCount(new LambdaQueryWrapper<Alarm>()
                .eq(Alarm::getRuleId, id)
                .ne(Alarm::getStatus, "CLOSED"));
        if (open != null && open > 0) {
            throw BizException.conflict("该规则还有未关闭的告警工单，先处置完再删");
        }
        ruleMapper.deleteById(id);
        alarmEngine.evict(rule.getDeviceId());
    }

    @Override
    public void evict(Long deviceId) {
        alarmEngine.evict(deviceId);
    }
}
