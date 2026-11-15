package com.iot.platform.ai.nl;

import java.time.LocalDateTime;

/**
 * 从自然语言里解析出来的结构化查询意图。
 *
 * <p>为什么先解析成结构体再执行：如果直接让模型"帮我查一下"，它要么编数据，
 * 要么得给它数据库连接——两条路都不对。正确做法是把自然语言降维成受限的查询参数，
 * 执行仍然走自己的受控接口。这样模型再胡说，也只会查错范围，不会查错库。
 */
public record QueryIntent(
        Type type,
        Long deviceId,
        String deviceName,
        String metric,
        LocalDateTime from,
        LocalDateTime to,
        String rawQuestion
) {

    public enum Type {
        /** 问某指标的平均/最大/最小/趋势 */
        TELEMETRY_STATS,
        /** 问告警数量或明细 */
        ALARM_LIST,
        /** 问设备现在什么状态 */
        DEVICE_STATUS
    }

    public String timeDesc() {
        if (from == null || to == null) {
            return "全部时间";
        }
        return from.toLocalDate().equals(to.toLocalDate())
                ? from.toLocalDate() + " " + from.toLocalTime() + "~" + to.toLocalTime()
                : from.toLocalDate() + " ~ " + to.toLocalDate();
    }
}
