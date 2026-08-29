package anissia.account.repository

import anissia.account.domain.AccountRecoverAuth
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

interface AccountRecoverAuthRepository : JpaRepository<AccountRecoverAuth, Long> {

    fun existsByAnAndExpDtAfter(an: Long, expDt: OffsetDateTime): Boolean

    fun findByNoAndTokenAndExpDtAfterAndUsedDtNull(no: Long, token: String, expDt: OffsetDateTime): AccountRecoverAuth?

    @Modifying
    @Query("DELETE FROM AccountRecoverAuth WHERE an = :an")
    fun deleteAllByAn(an: Long): Int

    @Transactional
    @Modifying
    @Query("DELETE FROM AccountRecoverAuth WHERE expDt < :expDt")
    fun deleteAllByExpDtBefore(expDt: OffsetDateTime = OffsetDateTime.now().minusDays(30))
}
