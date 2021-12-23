package io.taff.exposed.extensions.tables.long

import io.taff.exposed.extensions.env
import io.taff.exposed.extensions.records.TenantScopedRecord
import io.taff.exposed.extensions.tables.shared.TitleAware
import io.taff.exposed.extensions.tables.shared.includeTenantScopedTableSpeks
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import java.time.Instant
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils


/** Dummy tenant scoped record for testing */
data class TenantScopedLongIdRecord(
    override var title: String? = null,
    override var tenantId: Long? = null,
    override var id: Long? = null,
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null
) : TenantScopedRecord<Long, Long>, TitleAware

/** Dummy tenant scoped t able for testing */
var tenantScopedTitleColumn: Column<String>? = null
val tenantScopedLongIdRecords = object : TenantScopedLongIdTable<Long, TenantScopedLongIdRecord>("tenant_scoped_long_id_records") {
    val title = varchar("title", 50)
    init { tenantScopedTitleColumn = title }
    override val tenantId: Column<Long> = long("tenant_id")
    override fun initializeRecord(row: ResultRow) = TenantScopedLongIdRecord(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, record: TenantScopedLongIdRecord) {
        record.title?.let { stmt[title] = it }
    }
}

object TenantScopedLongIdTableSpek : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(tenantScopedLongIdRecords) }

    includeTenantScopedTableSpeks(
        tenantScopedLongIdRecords,
        tenantIdFunc = { 1L },
        tenant2IdFunc = { 2L },
        tenant1RecordFunc = { TenantScopedLongIdRecord("Soul food")  },
        tenant2RecordFunc = { TenantScopedLongIdRecord("Groovy soul food") },
        titleColumnRef = tenantScopedTitleColumn!!
    )

})
