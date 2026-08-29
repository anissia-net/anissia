package anissia.account.repository.jooq

import anissia.support.jooqColumn
import anissia.support.jooqTable
import org.jooq.impl.SQLDataType

object AccountTable {
    const val NAME = "account"

    val TABLE = jooqTable(NAME)
    val AN = jooqColumn(NAME, "an", SQLDataType.BIGINT)
    val ACCOUNT_NAME = jooqColumn(NAME, "name", SQLDataType.VARCHAR)
}
