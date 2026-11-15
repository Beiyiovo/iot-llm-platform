package com.iot.platform.common;

import lombok.Data;
import org.slf4j.MDC;

/**
 * 统一响应体。所有 HTTP 接口都返回这个结构，前端只认 code == 0。
 */
@Data
public class Result<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;
    private long timestamp = System.currentTimeMillis();

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.message = "ok";
        r.data = data;
        r.traceId = MDC.get(TraceIdFilter.TRACE_ID);
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        r.traceId = MDC.get(TraceIdFilter.TRACE_ID);
        return r;
    }
}
