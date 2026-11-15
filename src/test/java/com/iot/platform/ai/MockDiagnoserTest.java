package com.iot.platform.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("本地规则诊断器")
class MockDiagnoserTest {

    private final MockDiagnoser diagnoser = new MockDiagnoser();

    private DiagnosisContext context(String metric, double value, double threshold, List<Double> samples) {
        List<DiagnosisContext.Sample> list = new ArrayList<>();
        LocalDateTime t = LocalDateTime.now().minusMinutes(samples.size());
        for (Double v : samples) {
            list.add(new DiagnosisContext.Sample(t = t.plusMinutes(1), v));
        }
        Map<String, Double> latest = new LinkedHashMap<>();
        latest.put(metric, value);
        return new DiagnosisContext(1L, 1L, "3号楼网关", "dev_test", "3号楼",
                metric, value, threshold, "GT", "CRITICAL", LocalDateTime.now(),
                list, latest, List.of());
    }

    @Test
    @DisplayName("输出四个固定小标题，前端可以直接渲染")
    void shouldReturnStructuredSections() {
        DiagnosisResult r = diagnoser.diagnose(
                context("temperature", 65.0, 60.0, List.of(58.0, 60.0, 62.0, 64.0, 65.0)));

        assertTrue(r.text().contains("【根因判断】"));
        assertTrue(r.text().contains("【可能原因】"));
        assertTrue(r.text().contains("【处置建议】"));
        assertTrue(r.text().contains("【风险提示】"));
        assertEquals("rule-based-mock", r.model());
        assertFalse(r.fallback());
    }

    @Test
    @DisplayName("上升趋势应被识别为持续恶化")
    void shouldRecogniseRisingTrend() {
        DiagnosisResult r = diagnoser.diagnose(
                context("temperature", 72.0, 60.0, List.of(55.0, 58.0, 62.0, 67.0, 72.0)));
        assertTrue(r.text().contains("上升") || r.text().contains("恶化"));
    }

    @Test
    @DisplayName("未知指标走兜底知识库，不会抛异常")
    void shouldFallbackForUnknownMetric() {
        DiagnosisResult r = diagnoser.diagnose(
                context("pm25", 300, 150, List.of(100.0, 200.0, 300.0)));
        assertTrue(r.text().contains("【根因判断】"));
    }

    @Test
    @DisplayName("斜率计算：单调上升应为正")
    void slopeShouldBePositiveForIncreasingSeries() {
        DiagnosisContext ctx = context("temperature", 70, 60, List.of(60.0, 62.0, 64.0, 66.0, 70.0));
        assertTrue(ctx.slope() > 0);
    }
}
