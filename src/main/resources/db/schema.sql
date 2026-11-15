-- ========================================================
-- IoT 设备接入与智能诊断平台 · 表结构
-- 幂等脚本，随应用启动自动执行（spring.sql.init.mode=always）
-- ========================================================

CREATE TABLE IF NOT EXISTS sys_user
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username   VARCHAR(50)  NOT NULL COMMENT '登录名',
    password   VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
    nickname   VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    role       VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色 ADMIN / USER',
    enabled    TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 禁用',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='平台用户';

CREATE TABLE IF NOT EXISTS device
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    product_key  VARCHAR(50) NOT NULL COMMENT '产品分组',
    device_key   VARCHAR(64) NOT NULL COMMENT '设备唯一标识，MQTT 用户名',
    secret       VARCHAR(64) NOT NULL COMMENT '设备密钥，MQTT 密码',
    name         VARCHAR(100) NOT NULL COMMENT '设备名',
    location     VARCHAR(100) DEFAULT NULL COMMENT '安装位置',
    status       VARCHAR(20) NOT NULL DEFAULT 'OFFLINE' COMMENT 'ONLINE / OFFLINE / DISABLED',
    last_seen_at DATETIME     DEFAULT NULL COMMENT '最后一次上报时间',
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_key (device_key),
    KEY idx_product (product_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='设备档案';

CREATE TABLE IF NOT EXISTS telemetry
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    device_id  BIGINT      NOT NULL COMMENT '设备ID',
    device_key VARCHAR(64) NOT NULL COMMENT '冗余，方便按设备查',
    metric     VARCHAR(50) NOT NULL COMMENT '指标名 temperature/humidity/co2',
    value      DOUBLE      NOT NULL COMMENT '指标值',
    ts         DATETIME(3) NOT NULL COMMENT '采集时间（毫秒精度）',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_device_ts (device_id, ts),
    KEY idx_device_metric_ts (device_id, metric, ts)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='遥测时序数据';

CREATE TABLE IF NOT EXISTS alarm_rule
(
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    device_id       BIGINT      NOT NULL COMMENT '设备ID',
    metric          VARCHAR(50) NOT NULL COMMENT '被监控指标',
    operator        VARCHAR(4)  NOT NULL COMMENT 'GT / LT / GE / LE',
    threshold       DOUBLE      NOT NULL COMMENT '阈值',
    duration_points INT         NOT NULL DEFAULT 3 COMMENT '连续 N 个点超限才触发（防抖）',
    level           VARCHAR(10) NOT NULL DEFAULT 'WARN' COMMENT 'INFO / WARN / CRITICAL',
    enabled         TINYINT     NOT NULL DEFAULT 1,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_device_metric (device_id, metric)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='告警规则';

CREATE TABLE IF NOT EXISTS alarm
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    device_id    BIGINT      NOT NULL,
    rule_id      BIGINT       DEFAULT NULL,
    metric       VARCHAR(50)  DEFAULT NULL,
    metric_value DOUBLE       DEFAULT NULL COMMENT '触发时的值',
    threshold    DOUBLE       DEFAULT NULL,
    level        VARCHAR(10)  DEFAULT 'WARN',
    status       VARCHAR(20) NOT NULL DEFAULT 'TRIGGERED' COMMENT 'TRIGGERED / ACKED / CLOSED',
    message      VARCHAR(255) DEFAULT NULL,
    ai_diagnosis TEXT COMMENT '大模型诊断结果',
    ai_model     VARCHAR(50)  DEFAULT NULL COMMENT '实际使用的模型名',
    triggered_at DATETIME    NOT NULL,
    acked_at     DATETIME     DEFAULT NULL,
    acked_by     VARCHAR(50)  DEFAULT NULL,
    closed_at    DATETIME     DEFAULT NULL,
    closed_by    VARCHAR(50)  DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_status_time (status, triggered_at),
    KEY idx_device_time (device_id, triggered_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='告警工单';
