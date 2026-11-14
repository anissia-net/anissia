package anissia.agenda.repository

import anissia.agenda.domain.Agenda
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.OffsetDateTime

interface AgendaRepository : JpaRepository<Agenda, Long> {

    fun findAllByCodeAndStatusOrderByAgendaNoDesc(
        code: String,
        status: String,
        pageable: Pageable = PageRequest.of(0, 100),
    ): Page<Agenda>

    fun findAllByCodeOrderByStatusAscAgendaNoDesc(
        code: String,
        pageable: Pageable = PageRequest.of(0, 100),
    ): Page<Agenda>

    fun countByCodeAndStatus(code: String, status: String): Int

    fun existsByCodeAndStatusAndAn(code: String, status: String, an: Long): Boolean

    fun existsByCodeAndStatusAndAnAndUpdDtAfter(
        code: String,
        status: String,
        an: Long,
        updDt: OffsetDateTime,
    ): Boolean

    @Modifying
    @Query("""DELETE FROM Agenda a WHERE a.code = 'ANIME-DEL' and a.updDt < :baseDateTime
        and (a.data2 is null or a.data2 not like 'retained:%' or a.status <> 'wait')""")
    fun deleteDeletePadding(baseDateTime: OffsetDateTime = OffsetDateTime.now().minusDays(30)): Int

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Agenda a where a.agendaNo = :agendaNo")
    fun lockByAgendaNo(agendaNo: Long): Agenda?

    @Query("select a.data2 from Agenda a where a.agendaNo = :agendaNo and a.code = 'ANIME-DEL' and a.status = 'wait'")
    fun findDeletionReference(agendaNo: Long): String?

    @Modifying
    @Query("update Agenda a set a.status = 'purged' where a.code = 'ANIME-DEL' and a.status = 'wait' and a.data2 = :reference")
    fun finishRetainedDeletion(reference: String): Int

    @EntityGraph(attributePaths = ["polls"])
    fun findWithPollsByAgendaNoAndCode(agendaNo: Long, code: String): Agenda?

    @Query(
        "SELECT a FROM Agenda a WHERE a.an = :an AND a.code = 'TRANSLATOR-APPLY' and a.status = 'DONE' ORDER BY a.agendaNo DESC",
    )
    fun findPassedTranslatorApply(an: Long): List<Agenda>
}
