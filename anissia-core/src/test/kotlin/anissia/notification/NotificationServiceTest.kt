package anissia.notification

import anissia.account.domain.Account
import anissia.anime.repository.AnimeRepository
import anissia.notification.api.dto.EditNotificationRequest
import anissia.notification.domain.AnimeNotification
import anissia.notification.repository.AnimeNotificationRepository
import anissia.notification.service.AnimeNotificationsDisabledEvent
import anissia.notification.service.NotificationService
import anissia.security.Actor
import anissia.support.ApiResponse
import anissia.support.FailException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional

class NotificationServiceTest {
    private val notifications = mock(AnimeNotificationRepository::class.java)
    private val anime = mock(AnimeRepository::class.java)
    private val events = mock(ApplicationEventPublisher::class.java)
    private val service = NotificationService(notifications, anime, events)
    private val actor = Actor(an = 7)
    private val animeNo = 11L
    private val key = AnimeNotification.Key(actor.an, animeNo)

    private fun allowWrite() {
        `when`(notifications.lockAccount(actor.an)).thenReturn(Account(an = actor.an))
        `when`(anime.existsById(animeNo)).thenReturn(true)
        `when`(notifications.findById(key)).thenReturn(Optional.empty())
    }

    @Test
    fun `목록 등록 해제 모두 로그인 이전에는 저장소를 조회하지 않는다`() {
        assertThrows<FailException> { service.getList(Actor()) }
        assertThrows<FailException> { service.put(animeNo, EditNotificationRequest(), Actor()) }
        assertThrows<FailException> { service.delete(animeNo, Actor()) }
        verifyNoInteractions(notifications, anime, events)
    }

    @Test
    fun `기본 알림은 본인 계정으로 저장하며 켜져 있다`() {
        allowWrite()
        `when`(notifications.countByAn(actor.an)).thenReturn(199)

        assertEquals(ApiResponse.OK, service.put(animeNo, EditNotificationRequest(), actor).code)

        val saved = ArgumentCaptor.forClass(AnimeNotification::class.java)
        verify(notifications).save(saved.capture())
        assertEquals(actor.an, saved.value.an)
        assertEquals(animeNo, saved.value.animeNo)
        assertTrue(saved.value.notify)
        verifyNoInteractions(events)
        inOrder(notifications).run {
            verify(notifications).lockAccount(actor.an)
            verify(notifications).countByAn(actor.an)
        }
    }

    @Test
    fun `200개 한도에서는 새로운 작품 알림과 발송 작업을 만들지 않는다`() {
        allowWrite()
        `when`(notifications.countByAn(actor.an)).thenReturn(200)

        val response = service.put(animeNo, EditNotificationRequest(notify = true), actor)

        assertEquals(ApiResponse.FAIL, response.code)
        val saved = ArgumentCaptor.forClass(AnimeNotification::class.java)
        verify(notifications, never()).save(saved.capture())
        verifyNoInteractions(events)
    }

    @Test
    fun `기존 작품 알림의 재등록은 한도 검사 없이 알림 설정만 바꾼다`() {
        allowWrite()
        val existing = AnimeNotification(an = actor.an, animeNo = animeNo, notify = false)
        `when`(notifications.findById(key)).thenReturn(Optional.of(existing))

        assertEquals(ApiResponse.OK, service.put(animeNo, EditNotificationRequest(notify = true), actor).code)

        assertTrue(existing.notify)
        verify(notifications).save(existing)
        verify(notifications, never()).countByAn(actor.an)
        verifyNoInteractions(events)
    }

    @Test
    fun `알림을 끌 때는 대기 중인 알림 취소 이벤트도 발행한다`() {
        allowWrite()
        val existing = AnimeNotification(an = actor.an, animeNo = animeNo, notify = true)
        `when`(notifications.findById(key)).thenReturn(Optional.of(existing))

        service.put(animeNo, EditNotificationRequest(notify = false), actor)

        assertFalse(existing.notify)
        inOrder(notifications, events).run {
            verify(notifications).save(existing)
            verify(notifications).flush()
            verify(events).publishEvent(AnimeNotificationsDisabledEvent(actor.an, animeNo))
        }
    }

    @Test
    fun `없는 작품에는 알림을 등록할 수 없다`() {
        allowWrite()
        `when`(anime.existsById(animeNo)).thenReturn(false)

        assertEquals(ApiResponse.FAIL, service.put(animeNo, EditNotificationRequest(), actor).code)

        verify(notifications, never()).findById(key)
        verifyNoInteractions(events)
    }

    @Test
    fun `이미 없는 알림 해제도 성공하며 본인 작품의 대기 알림만 취소한다`() {
        `when`(notifications.lockAccount(actor.an)).thenReturn(Account(an = actor.an))
        `when`(notifications.deleteByAnAndAnimeNo(actor.an, animeNo)).thenReturn(0)

        assertEquals(ApiResponse.OK, service.delete(animeNo, actor).code)

        verify(notifications).deleteByAnAndAnimeNo(actor.an, animeNo)
        verify(events).publishEvent(AnimeNotificationsDisabledEvent(actor.an, animeNo))
        verifyNoInteractions(anime)
    }
}
