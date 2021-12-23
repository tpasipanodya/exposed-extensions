package io.taff.exposed.extensions.tables.long

import io.taff.exposed.extensions.env
import io.taff.exposed.extensions.records.Record
import io.taff.exposed.extensions.tables.shared.TitleAware
import io.taff.exposed.extensions.tables.shared.includeRecordMappingTableSpeks
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import java.time.Instant

/** Dummy record for testing */
data class LongIdRecord(
    override var title: String? = null,
    override var id: Long? = null,
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null
) : Record<Long>, TitleAware

/** Dummy table for testing */
var recordMappingTitleColumnRef: Column<String>? = null
val longIdRecords = object : RecordMappingLongIdTable<LongIdRecord>("long_id_records") {
    val title = varchar("title", 50)
    init { recordMappingTitleColumnRef = title }
    override fun initializeRecord(row: ResultRow) = LongIdRecord(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, record: LongIdRecord) {
        record.title?.let { stmt[title] = it }
    }
}

object RecordMappingLongIdTableSpek  : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(longIdRecords) }

    beforeEachTest { transaction { longIdRecords.deleteAll() } }

    includeRecordMappingTableSpeks(
        table = longIdRecords,
        recordFunc = { LongIdRecord("Foo bar") },
        titleColumnRef = recordMappingTitleColumnRef!!
    )
})
