package anissia.board.service

import anissia.board.api.dto.BoardTickerItem
import anissia.board.repository.BoardTickerRepository
import anissia.support.badRequestUnless
import anissia.support.getOrLoad
import anissia.support.ttlCache
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.time.Duration.Companion.hours

@Service
class BoardService(
    private val boardTickerRepository: BoardTickerRepository,
) {
    private val cache = ttlCache<String, BoardTickerItem>(24.hours)

    @Transactional(readOnly = true)
    fun getTicker(ticker: String): BoardTickerItem {
        badRequestUnless(ticker.isNotBlank()) { "ticker is blank" }

        return cache.getOrLoad(ticker) {
            boardTickerRepository.findByIdOrNull(it)
                ?.let { found -> BoardTickerItem(found) }
                ?: BoardTickerItem()
        }
    }

    @Transactional(readOnly = true)
    fun canWriteTopic(ticker: String, roles: List<String>): Boolean =
        boardTickerRepository.findByIdOrNull(ticker)?.run {
            writeTopicRoles.isEmpty() || roles.any { it in writeTopicRoles }
        } ?: false

    @Transactional(readOnly = true)
    fun canWritePost(ticker: String, roles: List<String>): Boolean =
        boardTickerRepository.findByIdOrNull(ticker)?.run {
            writePostRoles.isEmpty() || roles.any { it in writePostRoles }
        } ?: false
}
