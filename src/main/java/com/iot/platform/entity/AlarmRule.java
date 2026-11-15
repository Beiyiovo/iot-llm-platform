package com.iot.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警规则。durationPoints 是防抖参数：
 * 必须连续 N 个采样点都越过阈值才真正告警，避免单点毛刺刷屏。
 */
@Data
@TableName("alarm_rule")
public class AlarmRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long deviceId;

    private String metric;

    /** GT / LT / GE / LE */
    private String operator;

    private Double threshold;

    private Integer durationPoints;

    /** INFO / WARN / CRITICAL */
    private String level;

    private Integer enabled;

    private LocalDateTime createdAt;
}
