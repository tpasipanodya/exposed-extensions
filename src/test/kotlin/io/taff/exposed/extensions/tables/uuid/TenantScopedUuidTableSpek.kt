package io.taff.exposed.extensions.tables.uuid

import io.taff.exposed.extensions.env
import io.taff.exposed.extensions.records.TenantScopedRecord
import io.taff.exposed.extensions.tables.shared.TitleAware
import io.taff.exposed.extensions.tables.shared.includeTenantScopedTableSpeks
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import java.time.Instant
import java.util.*

/** Dummy tenant scoped record for testing */
data class TenantScopedUuidRecord(
    override var title: String? = null,
    override var tenantId: UUID? = null,
    override var id: UUID? = null,
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null
) : TenantScopedRecord<UUID, UUID>, TitleAware

/** Dummy tenant scoped table for testing */
var tenantScopedTitleColumn: Column<String>? = null
val tenantScopedUuidRecords = object : TenantScopedUuidTable<UUID, TenantScopedUuidRecord>("tenant_scoped_uuid_records") {
    val title = varchar("title", 50)
    init { tenantScopedTitleColumn = title }
    override val tenantId: Column<UUID> = uuid("tenant_id")
    override fun initializeRecord(row: ResultRow) = TenantScopedUuidRecord(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, record: TenantScopedUuidRecord) {
        record.title?.let { stmt[title] = it }
    }
}

object TenantScopedUuidTableSpek : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(tenantScopedUuidRecords) }

    includeTenantScopedTableSpeks(tenantScopedUuidRecords,
        tenantIdFunc = { UUID.randomUUID () },
        tenant2IdFunc = { UUID.randomUUID() },
        tenant1RecordFunc = { TenantScopedUuidRecord("Soul food")  },
        tenant2RecordFunc = { TenantScopedUuidRecord("Groovy soul food") },
        titleColumnRef = tenantScopedTitleColumn!!
    )
})
