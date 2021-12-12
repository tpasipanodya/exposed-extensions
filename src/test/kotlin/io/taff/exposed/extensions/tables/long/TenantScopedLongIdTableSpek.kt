package io.taff.exposed.extensions.tables.long

import io.taff.exposed.extensions.env
import io.taff.exposed.extensions.models.TenantScopedModel
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


/** Dummy tenant scoped model for testing */
data class TenantScopedLongIdModel(
    override var title: String? = null,
    override var tenantId: Long? = null,
    override var id: Long? = null,
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null
) : TenantScopedModel<Long, Long>, TitleAware

/** Dummy tenant scoped t able for testing */
var tenantScopedTitleColumn: Column<String>? = null
val tenantScopedLongIdRecords = object : TenantScopedLongIdTable<Long, TenantScopedLongIdModel>("tenant_scoped_long_id_records") {
    val title = varchar("title", 50)
    init { tenantScopedTitleColumn = title }
    override val tenantId: Column<Long> = long("tenant_id")
    override fun initializeModel(row: ResultRow) = TenantScopedLongIdModel(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: TenantScopedLongIdModel) {
        model.title?.let { stmt[title] = it }
    }
}

object TenantScopedLongIdTableSpek : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(tenantScopedLongIdRecords) }

    includeTenantScopedTableSpeks(
        tenantScopedLongIdRecords,
        tenantIdFunc = { 1L },
        tenant2IdFunc = { 2L },
        tenant1RecordFunc = { TenantScopedLongIdModel("Soul food")  },
        tenant2RecordFunc = { TenantScopedLongIdModel("Groovy soul food") },
        titleColumnRef = tenantScopedTitleColumn!!
    )

})
