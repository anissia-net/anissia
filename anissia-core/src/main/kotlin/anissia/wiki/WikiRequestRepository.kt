package anissia.wiki

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface WikiRequestRepository : JpaRepository<WikiRequest, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from WikiRequest r where r.id = :id")
    fun lockRequest(id: Long): WikiRequest?

    @Query("""
        select r from WikiRequest r
        where (:operator = true or r.requesterAn = :accountNo)
          and (:status is null or r.status = :status)
        order by r.id desc
    """)
    fun visibleRequests(accountNo: Long, operator: Boolean, status: WikiRequestStatus?, pageable: Pageable): Page<WikiRequest>
}
