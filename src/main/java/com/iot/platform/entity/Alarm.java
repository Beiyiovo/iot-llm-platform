package com.iot.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警工单。状态机：TRIGGERED → ACKED → CLOSED。
 * 每次流转都记人和时间，这是"工单"和"日志"的区别。
 */
@Data
@TableName("alarm")
public class Alarm {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long deviceId;

    private Long ruleId;

    private String metric;

    private Double metricValue;

    private Double threshold;

    private String level;

    /** TRIGGERED / ACKED / CLOSED */
    private String status;

    private String message;

    private String aiDiagnosis;

    private String aiModel;

    private LocalDateTime triggeredAt;

    private LocalDateTime ackedAt;

    private String ackedBy;

    private LocalDateTime closedAt;

    private String closedBy;
}
