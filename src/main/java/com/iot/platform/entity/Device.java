package com.iot.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备档案。
 *
 * <p>deviceKey / secret 构成"一机一密"：设备连 MQTT 时用 deviceKey 当用户名、
 * secret 当密码，Broker 侧自定义 IAuthenticator 校验，校验不过直接断开。
 */
@Data
@TableName("device")
public class Device {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String productKey;

    private String deviceKey;

    private String secret;

    private String name;

    private String location;

    /** ONLINE / OFFLINE / DISABLED */
    private String status;

    private LocalDateTime lastSeenAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
