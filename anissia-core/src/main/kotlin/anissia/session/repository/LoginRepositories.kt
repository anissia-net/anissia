package anissia.session.repository

import anissia.session.domain.LoginFail
import anissia.session.domain.LoginPass
import anissia.session.domain.LoginToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

interface LoginFailRepository : JpaRepository<LoginFail, Long> {

    fun countByIpAndEmailAndFailDtAfter(ip: String, email: String, failDt: OffsetDateTime): Long

    @Modifying
    @Query("DELETE FROM LoginFail WHERE ip = :ip AND email = :email")
    fun deleteByIpAndEmail(ip: String, email: String)

    @Modifying
    @Query("DELETE FROM LoginFail WHERE email = :email")
    fun deleteAllByEmail(email: String): Int

    @Transactional
    @Modifying
    @Query("DELETE FROM LoginFail WHERE failDt < :failDt")
    fun deleteAllByFailDtBefore(failDt: OffsetDateTime = OffsetDateTime.now().minusDays(90))
}

interface LoginPassRepository : JpaRepository<LoginPass, Long> {

    @Modifying
    @Query("DELETE FROM LoginPass WHERE an = :an")
    fun deleteAllByAn(an: Long): Int

    @Transactional
    @Modifying
    @Query("DELETE FROM LoginPass WHERE passDt < :passDt")
    fun deleteAllByPassDtBefore(passDt: OffsetDateTime = OffsetDateTime.now().minusDays(90))
}

interface LoginTokenRepository : JpaRepository<LoginToken, Long> {

    fun findByTokenNoAndTokenAndExpDtAfter(tokenNo: Long, token: String, expDt: OffsetDateTime): LoginToken?

    @Modifying
    @Query("DELETE FROM LoginToken WHERE an = :an")
    fun deleteAllByAn(an: Long): Int

    @Transactional
    @Modifying
    @Query("DELETE FROM LoginToken WHERE expDt < :expDt")
    fun deleteAllByExpDtBefore(expDt: OffsetDateTime = OffsetDateTime.now().minusDays(3))
}
