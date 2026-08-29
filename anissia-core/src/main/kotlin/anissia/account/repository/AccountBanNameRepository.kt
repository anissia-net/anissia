package anissia.account.repository

import anissia.account.domain.AccountBanName
import org.springframework.data.jpa.repository.JpaRepository

interface AccountBanNameRepository : JpaRepository<AccountBanName, String>
