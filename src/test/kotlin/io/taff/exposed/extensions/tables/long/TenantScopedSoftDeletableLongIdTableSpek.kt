package io.taff.exposed.extensions.tables.long

import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.persistence.clearCurrentTenantId
import io.taff.hephaestus.persistence.models.SoftDeletableModel
import io.taff.hephaestus.persistence.models.TenantScopedModel
import io.taff.hephaestus.persistence.tables.shared.TitleAware
import io.taff.hephaestus.persistence.tables.shared.includeTenantScopedSoftDeletableTableSpeks
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import java.time.Instant


/** Dummy tenant scoped model for testing */
data class TenantScopedSoftDeletableLongIdRecord(
    override var title: String? = null,
    override var tenantId: Long? = null,
    override var id: Long? = null,
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null,
    override var softDeletedAt: Instant? = null
) : TitleAware, TenantScopedModel<Long, Long>, SoftDeletableModel<Long>

/** Dummy tenant scoped t able for testing */
var softDeleteTenantScopedTitleColumn: Column<String>? = null
val tenantScopedSoftDeletableLongIdRecords = object : TenantScopedSoftDeletableLongIdTable<Long, TenantScopedSoftDeletableLongIdRecord>("tenant_scoped_soft_deletable_long_id_records") {
    val title = varchar("title", 50)
    init { softDeleteTenantScopedTitleColumn = title }
    override val tenantId: Column<Long> = long("tenant_id")
    override fun initializeModel(row: ResultRow) = TenantScopedSoftDeletableLongIdRecord(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: TenantScopedSoftDeletableLongIdRecord) {
        model.title?.let { stmt[title] = it }
    }
}

object TenantScopedSoftDeletableLongIdTableSpek : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(tenantScopedSoftDeletableLongIdRecords) }

    beforeEachTest { transaction { tenantScopedSoftDeletableLongIdRecords.stripDefaultScope().deleteAll() } }

    afterEachTest { clearCurrentTenantId() }

    includeTenantScopedSoftDeletableTableSpeks(
        tenantScopedSoftDeletableLongIdRecords,
        tenantIdFunc = { 1L },
        otherTenantIdFunc = { 2L },
        tenant1RecordsFunc = {
            arrayOf(
                TenantScopedSoftDeletableLongIdRecord("Soul food"),
                TenantScopedSoftDeletableLongIdRecord("Groovy Soul food")
            )
        }, tenant2RecordsFunc = {
            arrayOf(
                TenantScopedSoftDeletableLongIdRecord("Smooth Soul food"),
                TenantScopedSoftDeletableLongIdRecord("Bada-boom Soul food")
            )
        }, titleColumnRef = softDeleteTenantScopedTitleColumn!!
    )
})
