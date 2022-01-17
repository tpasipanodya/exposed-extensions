package io.taff.exposed.extensions.tables.uuid

import io.taff.exposed.extensions.env
import io.taff.exposed.extensions.records.Record
import io.taff.exposed.extensions.tables.shared.TitleAware
import io.taff.exposed.extensions.tables.shared.includeRecordMappingTableSpeks
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.Instant
import java.util.UUID

/** Dummy record for testing */
data class UuidRecord(
    override var title: String? = null,
    override var id: UUID? = null,
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null
) : Record<UUID>, TitleAware

/** Dummy table for testing */
var recordMappingTitleColumnRef: Column<String>? = null
val uuidRecords = object : RecordMappingUuidTable<UuidRecord>("uuid_records") {
    val title = varchar("title", 50)
    init { recordMappingTitleColumnRef = title }
    override fun initializeRecord(row: ResultRow) = UuidRecord(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, record: UuidRecord) {
        record.title?.let { stmt[title] = it }
    }
}

object RecordMappingUuidTableSpek  : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(uuidRecords) }

    beforeEachTest { transaction { uuidRecords.stripDefaultFilter().deleteAll() } }

    includeRecordMappingTableSpeks(
        table = uuidRecords,
        recordFunc = { UuidRecord("Foo bar") },
        titleColumnRef = recordMappingTitleColumnRef!!
    )
})
