package com.iot.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.platform.entity.Telemetry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TelemetryMapper extends BaseMapper<Telemetry> {

    /**
     * 取某设备某指标最近 N 条样本，按时间正序返回（给 AI 诊断当上下文用）。
     */
    @Select("""
            SELECT * FROM (
                SELECT * FROM telemetry
                WHERE device_id = #{deviceId} AND metric = #{metric}
                ORDER BY ts DESC
                LIMIT #{limit}
            ) t ORDER BY t.ts ASC
            """)
    List<Telemetry> selectRecent(@Param("deviceId") Long deviceId,
                                 @Param("metric") String metric,
                                 @Param("limit") int limit);

    /**
     * 某设备在时间窗内的指标聚合，用于看板概览。
     */
    @Select("""
            SELECT metric,
                   COUNT(*) AS cnt,
                   AVG(value) AS avgValue,
                   MIN(value) AS minValue,
                   MAX(value) AS maxValue
            FROM telemetry
            WHERE device_id = #{deviceId} AND ts >= #{from}
            GROUP BY metric
            """)
    List<java.util.Map<String, Object>> selectAggregation(@Param("deviceId") Long deviceId,
                                                          @Param("from") LocalDateTime from);
}
