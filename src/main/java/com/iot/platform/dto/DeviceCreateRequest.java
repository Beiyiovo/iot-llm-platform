package com.iot.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建设备。secret 不传就由服务端生成，返回体里只出现一次，之后不再回显。
 */
public record DeviceCreateRequest(
        @NotBlank(message = "设备名不能为空") @Size(max = 100) String name,
        @NotBlank(message = "productKey 不能为空") @Size(max = 50) String productKey,
        @Size(max = 100) String location,
        @Size(max = 64) String secret
) {
}
