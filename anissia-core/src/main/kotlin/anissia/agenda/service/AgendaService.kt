package anissia.agenda.service

import anissia.agenda.repository.AgendaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AgendaService(
    private val agendaRepository: AgendaRepository,
) {
    @Transactional
    fun deleteDeletePaddingAnime() {
        agendaRepository.deleteDeletePadding()
    }
}
