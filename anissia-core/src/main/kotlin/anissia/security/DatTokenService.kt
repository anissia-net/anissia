package anissia.security

import me.saro.dat.dat.DatCmsManager
import org.springframework.stereotype.Component

@Component
class DatTokenService(
    private val datCmsManager: DatCmsManager,
) {
    fun parse(dat: String?, ip: String): Actor {
        if (dat.isNullOrBlank()) {
            return Actor.anonymous(ip)
        }
        return datCmsManager.parse(dat)
            .map { payload ->
                val record = CODEC.read(payload.plain)
                if (record.isEmpty()) {
                    null
                } else {
                    Actor(
                        an = payload.secure.toLong(),
                        email = record[0],
                        name = record[1],
                        roles = record[2].takeIf { it.isNotBlank() }?.split(",") ?: listOf(),
                        ip = ip,
                    )
                }
            }
            .getOrElse { Actor.anonymous(ip) }
    }

    fun issue(actor: Actor): String {
        val plain = CODEC.write(actor.email, actor.name, actor.roles.joinToString(","))
        return datCmsManager.issue(plain, actor.an.toString()).getOrNull()
            ?: throw SecurityException("Failed to issue dat")
    }

    companion object {
        const val HEADER = "dat"
        private val CODEC = RecordCodec(version = "2", recordCount = 3)
    }
}
