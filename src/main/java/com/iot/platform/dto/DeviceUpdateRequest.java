package com.iot.platform.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeviceUpdateRequest(
        @Size(max = 100) String name,
        @Size(max = 100) String location,
        @Pattern(regexp = "ONLINE|OFFLINE|DISABLED", message = "status 只能是 ONLINE/OFFLINE/DISABLED")
        String status
) {
}
