package anissia.board.repository

import anissia.board.domain.BoardPost
import anissia.board.domain.BoardTicker
import anissia.board.domain.BoardTopic
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface BoardTickerRepository : JpaRepository<BoardTicker, String>

interface BoardTopicRepository : JpaRepository<BoardTopic, Long> {

    @EntityGraph(attributePaths = ["account"])
    fun findWithAccountByTickerAndTopicNo(ticker: String, topicNo: Long): BoardTopic?

    @EntityGraph(attributePaths = ["account"])
    fun findAllWithAccountByTickerOrderByTickerAscFixedDescTopicNoDesc(
        ticker: String,
        pageable: Pageable,
    ): Page<BoardTopic>

    fun findTop5ByTickerAndFixedOrderByTopicNoDesc(ticker: String, fixed: Boolean = false): List<BoardTopic>

    @Query("SELECT A.topicNo FROM BoardTopic A WHERE A.an = :an")
    fun findTopicNosByAn(an: Long): List<Long>

    @Modifying
    @Query("DELETE FROM BoardTopic A WHERE A.an = :an")
    fun deleteAllByAn(an: Long): Int
}

interface BoardPostRepository : JpaRepository<BoardPost, Long> {

    @EntityGraph(attributePaths = ["account"])
    fun findAllWithAccountByTopicNoOrderByPostNo(topicNo: Long): List<BoardPost>

    @EntityGraph(attributePaths = ["account"])
    fun findWithAccountByTopicNoAndRootIsTrue(topicNo: Long): BoardPost?

    @Modifying
    @Query("DELETE FROM BoardPost A WHERE A.topicNo = :topicNo")
    fun deleteAllByTopicNo(topicNo: Long): Int

    @Query("SELECT DISTINCT A.topicNo FROM BoardPost A WHERE A.an = :an")
    fun findTopicNosByPostAn(an: Long): List<Long>

    @Modifying
    @Query("DELETE FROM BoardPost A WHERE A.topicNo IN :topicNos")
    fun deleteAllByTopicNoIn(topicNos: Collection<Long>): Int

    @Modifying
    @Query("DELETE FROM BoardPost A WHERE A.an = :an")
    fun deleteAllByAn(an: Long): Int
}
