package com.iot.platform.ai;

/**
 * 一次诊断的结果。
 *
 * @param text     诊断正文（Markdown / 固定小标题格式）
 * @param model    实际产出这段文字的模型名，mock 就是 "rule-based-mock"
 * @param fallback 是否由降级路径产出（true 说明大模型这次没成功，用的是本地规则）
 * @param costMs   耗时，用于看板上展示"AI 响应时间"
 */
public record DiagnosisResult(String text, String model, boolean fallback, long costMs) {
}
