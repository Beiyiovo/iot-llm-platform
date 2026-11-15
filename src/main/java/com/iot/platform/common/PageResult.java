package com.iot.platform.common;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Function;

/**
 * 分页响应。不直接把 MyBatis-Plus 的 Page 丢出去，
 * 一是避免把内部字段（orders、optimizeCountSql…）暴露给前端，二是留了转换 VO 的口子。
 */
public record PageResult<T>(List<T> records, long total, long current, long size) {

    public static <E, V> PageResult<V> of(IPage<E> page, Function<E, V> mapper) {
        return new PageResult<>(
                page.getRecords().stream().map(mapper).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }
}
