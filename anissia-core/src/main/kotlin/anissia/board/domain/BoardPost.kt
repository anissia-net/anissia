package anissia.board.domain

import anissia.account.domain.Account
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.ForeignKey
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.UpdateTimestamp
import java.time.OffsetDateTime

@Entity
@Table(
    indexes = [Index(name = "board_post_idx__topicNo_postNo", columnList = "topicNo,postNo")],
)
class BoardPost(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    var postNo: Long = 0,

    @Column(nullable = false)
    var topicNo: Long = 0,

    @Column(nullable = false)
    var root: Boolean = false,

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    var content: String = "",

    @Column(nullable = false)
    var an: Long = 0,

    @Column(nullable = false)
    var regDt: OffsetDateTime = OffsetDateTime.now(),

    @UpdateTimestamp
    @Column(nullable = false)
    var updDt: OffsetDateTime = OffsetDateTime.now(),

    @OneToOne
    @JoinColumn(
        name = "an",
        foreignKey = ForeignKey(name = "board_post_fk_account"),
        nullable = false,
        insertable = false,
        updatable = false,
    )
    var account: Account? = null,
) {
    fun edit(content: String) {
        this.content = content
    }

    companion object {
        fun create(topicNo: Long, content: String, an: Long): BoardPost =
            BoardPost(topicNo = topicNo, content = content, an = an)

        fun createRootPost(topicNo: Long, content: String, an: Long): BoardPost =
            BoardPost(topicNo = topicNo, root = true, content = content, an = an)
    }
}

/*
CREATE TABLE `board_post` (
  `post_no` bigint(20) NOT NULL AUTO_INCREMENT,
  `topic_no` bigint(20) NOT NULL,
  `root` bit(1) NOT NULL,
  `content` longtext NOT NULL,
  `an` bigint(20) NOT NULL,
  `reg_dt` datetime NOT NULL,
  `upd_dt` datetime NOT NULL,
  PRIMARY KEY (`post_no`),
  KEY `board_post_idx1` (`topic_no`,`post_no`),
  KEY `board_post_fk_idx1` (`an`),
  CONSTRAINT `board_post_fk1` FOREIGN KEY (`an`) REFERENCES `account` (`an`),
  CONSTRAINT `board_post_fk2` FOREIGN KEY (`topic_no`) REFERENCES `board_topic` (`topic_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 */
