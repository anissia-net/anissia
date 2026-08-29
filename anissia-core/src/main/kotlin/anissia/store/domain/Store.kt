package anissia.store.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table

@Entity
@Table
class Store(
    @Id
    @Column(nullable = false, length = 64)
    var code: String = "",

    @Column(nullable = true, length = 128)
    var cv: String = "",

    @Lob
    @Column(nullable = true, columnDefinition = "LONGTEXT")
    var data: String = "",
)

/*
CREATE TABLE `store` (
  `code` varchar(64) NOT NULL,
  `cv` varchar(128) DEFAULT NULL,
  `data` longtext DEFAULT NULL,
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 */
