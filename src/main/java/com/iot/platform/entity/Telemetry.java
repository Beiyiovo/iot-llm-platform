package com.iot.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 遥测数据点。窄表设计（一条 = 一个设备一个指标一个时刻），
 * 好处是新增传感器不用改表结构，坏处是查询要按 metric 过滤，靠联合索引 (device_id, metric, ts) 兜住。
 */
@Data
@TableName("telemetry")
public class Telemetry {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long deviceId;

    private String deviceKey;

    private String metric;

    private Double value;

    private LocalDateTime ts;

    private LocalDateTime createdAt;
}
