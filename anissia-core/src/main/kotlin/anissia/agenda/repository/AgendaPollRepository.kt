package anissia.agenda.repository

import anissia.agenda.domain.AgendaPoll
import org.springframework.data.jpa.repository.JpaRepository

interface AgendaPollRepository : JpaRepository<AgendaPoll, Long>
