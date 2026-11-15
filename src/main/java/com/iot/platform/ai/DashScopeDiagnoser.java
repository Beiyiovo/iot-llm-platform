package com.iot.platform.ai;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.iot.platform.config.IotProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 真调阿里云百炼（DashScope）的通义千问。
 *
 * <p>对应简历上那张 ACA 认证——证书只证明"学过"，这段代码证明"用过"：
 * 有 Key 就走这里，没 Key 或超时就回落到 {@link MockDiagnoser}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashScopeDiagnoser implements Diagnoser {

    private final IotProperties props;

    private static final String SYSTEM_PROMPT = """
            你是工业物联网设备的故障诊断专家，服务对象是现场运维工程师。
            你会收到一台设备的告警信息、当前各指标读数、近期采样序列和其他告警。
            请基于这些事实做推断，不要编造数据里没有的信息。

            输出必须严格使用下面四个小标题，不要加开场白、不要加总结、不要用表格：

            【根因判断】
            一到两句话，指出最可能的根因，并说明依据是哪个数据特征。

            【可能原因】
            最多 3 条，按可能性从高到低排序，每条一行，只写原因不写建议。

            【处置建议】
            最多 3 条，必须是可以直接执行的现场动作，按优先级排序。

            【风险提示】
            一句话，说明不处理的后果，或需要立即升级的信号。
            """;

    public boolean available() {
        String key = props.getAi().getApiKey();
        return key != null && !key.isBlank();
    }

    @Override
    public String name() {
        return "dashscope:" + props.getAi().getModel();
    }

    @Override
    public DiagnosisResult diagnose(DiagnosisContext ctx) {
        long start = System.currentTimeMillis();
        try {
            Generation gen = new Generation();
            Message system = Message.builder()
                    .role(Role.SYSTEM.getValue())
                    .content(SYSTEM_PROMPT)
                    .build();
            Message user = Message.builder()
                    .role(Role.USER.getValue())
                    .content(ctx.toPromptText())
                    .build();

            GenerationParam param = GenerationParam.builder()
                    .apiKey(props.getAi().getApiKey())
                    .model(props.getAi().getModel())
                    .messages(List.of(system, user))
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .temperature(0.3f)
                    .build();

            GenerationResult result = gen.call(param);
            String text = result.getOutput().getChoices().get(0).getMessage().getContent();
            long cost = System.currentTimeMillis() - start;
            log.info("DashScope 诊断完成 alarm={} cost={}ms", ctx.alarmId(), cost);
            return new DiagnosisResult(text, name(), false, cost);
        } catch (Exception e) {
            // 这里不吞异常语义：抛出去让上层决定是否降级，避免"看起来成功其实是空的"
            throw new IllegalStateException("DashScope 调用失败: " + e.getMessage(), e);
        }
    }
}
