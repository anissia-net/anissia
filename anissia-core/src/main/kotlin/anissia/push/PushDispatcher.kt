package anissia.push

import anissia.support.logger
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Component
class PushDispatcher(private val outbox: CaptionPushOutbox, private val sender: PushSender) {
    private val log = logger<PushDispatcher>()
    private var executor: ScheduledExecutorService? = null

    @PostConstruct
    fun start() {
        if (!sender.enabled) return
        // 느린 푸시 서비스가 기존 편성표/집계 Spring 스케줄러를 막지 않도록 전용 실행기를 사용한다.
        executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "caption-push-dispatcher").apply { isDaemon = true }
        }.also { it.scheduleWithFixedDelay(::dispatch, 10, 15, TimeUnit.SECONDS) }
    }

    @PreDestroy
    fun stop() {
        executor?.shutdownNow()
    }

    private fun dispatch() {
        try {
            outbox.cleanup()
            for (id in outbox.dueIds()) {
                if (Thread.currentThread().isInterrupted) return
                val claimed = outbox.claim(id) ?: continue
                val delivery = outbox.loadEligible(id, claimed.token)
                if (delivery == null) {
                    outbox.finish(id, claimed.token, "cancelled")
                    continue
                }
                try {
                    when (val status = sender.send(delivery)) {
                        in 200..299 -> outbox.finish(id, delivery.token, "sent")
                        404, 410 -> outbox.removeExpiredSubscription(delivery)
                        408, 425, 429, in 500..599 -> outbox.retry(delivery)
                        else -> {
                            outbox.finish(id, delivery.token, "failed")
                            log.warn("Web Push 발송 거절: delivery={}, status={}", id, status)
                        }
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                } catch (_: Exception) {
                    outbox.retry(delivery)
                    // 네트워크 예외의 메시지/stack trace에는 비밀 endpoint가 포함될 수 있다.
                    log.warn("Web Push 발송 실패: delivery={} (재시도 대상)", id)
                }
            }
        } catch (_: Exception) {
            log.warn("Web Push 대기열 처리에 실패했습니다. 다음 실행에서 재시도합니다.")
        }
    }
}
