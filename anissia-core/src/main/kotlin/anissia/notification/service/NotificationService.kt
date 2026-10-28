package anissia.notification.service

import anissia.anime.repository.AnimeRepository
import anissia.notification.api.dto.EditNotificationRequest
import anissia.notification.api.dto.NotificationItem
import anissia.notification.domain.AnimeNotification
import anissia.notification.repository.AnimeNotificationRepository
import anissia.security.Actor
import anissia.support.ApiResponse
import anissia.support.badRequestUnless
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 알림을 끄거나 등록을 해제한 트랜잭션에서 대기 중인 발송도 함께 취소한다. */
data class AnimeNotificationsDisabledEvent(val an: Long, val animeNo: Long)

@Service
class NotificationService(
    private val notificationRepository: AnimeNotificationRepository,
    private val animeRepository: AnimeRepository,
    private val events: ApplicationEventPublisher,
) {
    @Transactional(readOnly = true)
    fun getList(actor: Actor): List<NotificationItem> {
        actor.validateLogin()
        return notificationRepository.findAllWithAnimeByAn(actor.an).map { NotificationItem(it) }
    }

    @Transactional
    fun put(animeNo: Long, request: EditNotificationRequest, actor: Actor): ApiResponse<Unit> {
        actor.validateLogin()
        badRequestUnless(animeNo > 0) { "animeNo 는 0보다 커야 합니다." }

        if (notificationRepository.lockAccount(actor.an) == null) {
            return ApiResponse.fail("존재하지 않는 계정입니다.")
        }
        if (!animeRepository.existsById(animeNo)) {
            return ApiResponse.fail("존재하지 않는 애니메이션입니다.")
        }

        val existing = notificationRepository.findByIdOrNull(AnimeNotification.Key(actor.an, animeNo))
        if (existing == null && notificationRepository.countByAn(actor.an) >= MAX_NOTIFICATIONS) {
            return ApiResponse.fail("알림을 받을 작품은 최대 ${MAX_NOTIFICATIONS}개까지 추가할 수 있습니다.")
        }

        notificationRepository.save(
            (existing ?: AnimeNotification(an = actor.an, animeNo = animeNo)).apply { notify = request.notify },
        )
        if (!request.notify) {
            // JDBC 발송 대기열을 지우기 전에 notify 변경을 DB에도 반영한다.
            // InnoDB의 INSERT ... SELECT가 이전 설정으로 새 대기를 끼워 넣지 못하게 한다.
            notificationRepository.flush()
            events.publishEvent(AnimeNotificationsDisabledEvent(actor.an, animeNo))
        }
        return ApiResponse.ok()
    }

    @Transactional
    fun delete(animeNo: Long, actor: Actor): ApiResponse<Unit> {
        actor.validateLogin()
        badRequestUnless(animeNo > 0) { "animeNo 는 0보다 커야 합니다." }

        if (notificationRepository.lockAccount(actor.an) == null) {
            return ApiResponse.fail("존재하지 않는 계정입니다.")
        }
        notificationRepository.deleteByAnAndAnimeNo(actor.an, animeNo)
        events.publishEvent(AnimeNotificationsDisabledEvent(actor.an, animeNo))
        return ApiResponse.ok()
    }

    companion object {
        const val MAX_NOTIFICATIONS = 200
    }
}
