package com.iot.platform.event;

import com.iot.platform.ai.AiDiagnosisService;
import com.iot.platform.ai.DiagnosisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 告警一落库就自动跑一次诊断，把结果写回工单。
 *
 * <p>用 AFTER_COMMIT 而不是普通 @EventListener：普通监听器在事务提交前触发，
 * 异步线程去查库时很可能查不到刚插入的告警，最后报个莫名其妙的"告警不存在"。
 * 这是分布式/异步场景里最常踩的一个坑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmDiagnosisListener {

    private final AiDiagnosisService aiDiagnosisService;

    @Async("ingestExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAlarmCreated(AlarmCreatedEvent event) {
        try {
            DiagnosisResult r = aiDiagnosisService.diagnoseAndSave(event.alarm().getId());
            log.info("告警 {} 自动诊断完成 model={} fallback={} cost={}ms",
                    event.alarm().getId(), r.model(), r.fallback(), r.costMs());
        } catch (Exception e) {
            log.error("告警 {} 自动诊断失败", event.alarm().getId(), e);
        }
    }
}
