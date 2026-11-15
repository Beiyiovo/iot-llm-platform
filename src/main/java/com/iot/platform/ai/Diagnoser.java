package com.iot.platform.ai;

/**
 * 诊断器统一接口。两个实现：
 * <ul>
 *     <li>{@link MockDiagnoser} 纯本地规则，永远可用</li>
 *     <li>{@link DashScopeDiagnoser} 真的调通义千问</li>
 * </ul>
 * 上层的 {@link AiDiagnosisService} 负责选谁、超时、降级。
 */
public interface Diagnoser {

    DiagnosisResult diagnose(DiagnosisContext ctx);

    String name();
}
