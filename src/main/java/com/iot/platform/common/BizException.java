package com.iot.platform.common;

/**
 * 业务异常。code 不是 HTTP 状态码，是业务错误码，前端按 code 分支。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BizException notFound(String what) {
        return new BizException(404, what + "不存在");
    }

    public static BizException conflict(String message) {
        return new BizException(409, message);
    }
}
