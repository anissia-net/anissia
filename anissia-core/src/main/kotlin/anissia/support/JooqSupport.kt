package anissia.support

import org.jooq.DataType
import org.jooq.Field
import org.jooq.Record
import org.jooq.Table
import org.jooq.impl.DSL

fun jooqTable(name: String): Table<Record> = DSL.table(DSL.name(name))

fun <T> jooqColumn(table: String, name: String, type: DataType<T>): Field<T> =
    DSL.field(DSL.name(table, name), type)
