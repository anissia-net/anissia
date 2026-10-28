package anissia.push

import anissia.security.Actor
import anissia.support.FailException
import anissia.support.badRequestUnless
import nl.martijndwars.webpush.Utils
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PushSubscriptionService(private val jdbc: JdbcTemplate, private val sender: PushSender) {
    @Transactional
    fun subscribe(request: PushSubscribeRequest, actor: Actor): PushStatusItem {
        actor.validateLogin()
        if (!sender.enabled) throw FailException("브라우저 알림을 준비 중입니다.")
        PushValidation.endpoint(request.endpoint)
        PushValidation.clientId(request.clientId)
        val publicKey = PushValidation.key(request.keys.p256dh, 65)
        badRequestUnless(publicKey[0] == 4.toByte()) { "알림 구독 키가 올바르지 않습니다." }
        PushValidation.key(request.keys.auth, 16)
        badRequestUnless(runCatching { Utils.loadPublicKey(request.keys.p256dh) }.isSuccess) {
            "알림 구독 키가 올바르지 않습니다."
        }
        badRequestUnless(request.expirationTime == null || request.expirationTime > System.currentTimeMillis()) {
            "만료된 알림 구독입니다. 다시 설정해주세요."
        }
        // 한 계정의 기기 제한 검사와 등록을 직렬화한다. 다른 계정의 endpoint 소유권은 이전하지 않는다.
        val account = jdbc.query("SELECT an FROM account WHERE an = ? FOR UPDATE", { rs, _ -> rs.getLong(1) }, actor.an)
        if (account.isEmpty()) throw FailException("존재하지 않는 계정입니다.")
        val endpointHash = PushValidation.hash(request.endpoint)
        val existing = jdbc.query(
            "SELECT id, an, client_id FROM push_subscription WHERE endpoint_hash = ? FOR UPDATE",
            { rs, _ -> ExistingSubscription(rs.getLong("id"), rs.getLong("an"), rs.getString("client_id")) }, endpointHash,
        ).singleOrNull()
        if (existing != null && existing.an != actor.an) throw ownershipError()
        if (existing == null) {
            val count = jdbc.queryForObject("SELECT COUNT(*) FROM push_subscription WHERE an = ?", Long::class.java, actor.an) ?: 0
            badRequestUnless(count < 20) { "등록 가능한 알림 기기 수를 초과했습니다. 사용하지 않는 기기에서 알림을 해제해주세요." }
            jdbc.update(
                """INSERT IGNORE INTO push_subscription
                    (an, endpoint_hash, endpoint, p256dh, auth, client_id, expiration_time, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))""",
                actor.an, endpointHash, request.endpoint, request.keys.p256dh, request.keys.auth,
                request.clientId, request.expirationTime,
            )
            val owner = jdbc.queryForObject("SELECT an FROM push_subscription WHERE endpoint_hash = ?", Long::class.java, endpointHash)
            if (owner != actor.an) throw ownershipError()
        } else {
            if (existing.clientId != request.clientId) {
                jdbc.update("DELETE FROM push_delivery WHERE subscription_id = ?", existing.id)
            }
            jdbc.update(
                """UPDATE push_subscription SET p256dh = ?, auth = ?, client_id = ?, expiration_time = ?,
                    updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND an = ?""",
                request.keys.p256dh, request.keys.auth, request.clientId, request.expirationTime, existing.id, actor.an,
            )
        }
        return PushStatusItem(true)
    }

    @Transactional(readOnly = true)
    fun status(request: PushEndpointRequest, actor: Actor): PushStatusItem {
        actor.validateLogin()
        PushValidation.endpoint(request.endpoint)
        PushValidation.clientId(request.clientId)
        val count = jdbc.queryForObject(
            """SELECT COUNT(*) FROM push_subscription WHERE endpoint_hash = ? AND an = ? AND client_id = ?
                AND (expiration_time IS NULL OR expiration_time > ?)""",
            Long::class.java, PushValidation.hash(request.endpoint), actor.an, request.clientId, System.currentTimeMillis(),
        ) ?: 0
        return PushStatusItem(sender.enabled && count > 0)
    }

    @Transactional
    fun unsubscribe(request: PushEndpointRequest, actor: Actor) {
        actor.validateLogin()
        PushValidation.endpoint(request.endpoint)
        jdbc.update("DELETE FROM push_subscription WHERE endpoint_hash = ? AND an = ?", PushValidation.hash(request.endpoint), actor.an)
    }

    private fun ownershipError(): FailException =
        FailException("다른 계정의 알림 구독입니다. 이 브라우저의 알림을 해제한 후 다시 켜주세요.")

    private data class ExistingSubscription(val id: Long, val an: Long, val clientId: String)
}
