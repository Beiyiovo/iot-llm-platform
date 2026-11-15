package com.iot.platform.controller;

import com.iot.platform.ai.nl.NlQueryService;
import com.iot.platform.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "07 · 自然语言查询")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final NlQueryService nlQueryService;

    public record AskRequest(@NotBlank(message = "问题不能为空") String question) {
    }

    @Operation(summary = "用自然语言查设备",
            description = """
                    支持问法示例：
                    - 「3号楼网关昨天掉线几次」
                    - 「冷库最近1小时温度最高多少」
                    - 「现在有几台设备在线」
                    - 「车间配电柜当前状态」

                    解析走本地规则（设备名 + 指标别名 + 时间词 + 意图词），不依赖大模型，
                    离线可用；执行只开放三类受控动作：查告警、查遥测聚合、查设备影子。
                    """)
    @PostMapping("/ask")
    public Result<NlQueryService.Answer> ask(@Valid @RequestBody AskRequest req) {
        return Result.ok(nlQueryService.ask(req.question()));
    }
}
