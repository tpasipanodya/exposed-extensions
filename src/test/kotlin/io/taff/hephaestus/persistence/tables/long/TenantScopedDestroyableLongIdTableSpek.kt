package io.taff.hephaestus.persistence.tables.long

import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.persistence.clearCurrentTenantId
import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.models.TenantScopedModel
import io.taff.hephaestus.persistence.tables.shared.DestroyableTenantScope
import io.taff.hephaestus.persistence.tables.shared.TitleAware
import io.taff.hephaestus.persistence.tables.shared.includeTenantScopedDestroyableTableSpeks
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import java.time.Instant


/** Dummy tenant scoped model for testing */
data class TenantScopedDestroyableLongIdRecord(
    override var title: String? = null,
    override var tenantId: Long? = null,
    override var id: Long? = null,
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null,
    override var destroyedAt: Instant? = null
) : TitleAware, TenantScopedModel<Long, Long>, DestroyableModel<Long>

/** Dummy tenant scoped t able for testing */
var titleColumRef: Column<String>? = null
val tenantScopedDestroyableLongIdRecords = object : TenantScopedDestroyableLongIdTable<Long, TenantScopedDestroyableLongIdRecord>("tenant_scoped_destroyable_long_id_records") {
    val title = varchar("title", 50)
    init { titleColumRef = title }
    override val tenantId: Column<Long> = long("tenant_id")
    override fun initializeModel(row: ResultRow) = TenantScopedDestroyableLongIdRecord(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: TenantScopedDestroyableLongIdRecord) {
        model.title?.let { stmt[title] = it }
    }
}

object TenantScopedDestroyableLongIdTableSpek : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(tenantScopedDestroyableLongIdRecords) }

    beforeEachTest { transaction { tenantScopedDestroyableLongIdRecords.stripDefaultScope().deleteAll() } }

    afterEachTest { clearCurrentTenantId() }

    includeTenantScopedDestroyableTableSpeks(
        tenantScopedDestroyableLongIdRecords,
        tenantIdFunc = { 1L },
        otherTenantIdFunc = { 2L },
        tenant1RecordsFunc = {
            arrayOf(
                TenantScopedDestroyableLongIdRecord("Soul food"),
                TenantScopedDestroyableLongIdRecord("Groovy Soul food")
            )
        }, tenant2RecordsFunc = {
            arrayOf(
                TenantScopedDestroyableLongIdRecord("Smooth Soul food"),
                TenantScopedDestroyableLongIdRecord("Bada-boom Soul food")
            )
        }, directUpdateFunc = { record, newTitle, scope ->
            when(scope) {
                DestroyableTenantScope.LIVE_FOR_TENANT -> tenantScopedDestroyableLongIdRecords
                DestroyableTenantScope.DELETED_FOR_TENANT -> tenantScopedDestroyableLongIdRecords.destroyed()
                DestroyableTenantScope.LIVE_AND_DESTROYED_FOR_CURRENT_TENANT -> tenantScopedDestroyableLongIdRecords.liveAndDestroyed()
                DestroyableTenantScope.LIVE_FOR_ALL_TENANTS -> tenantScopedDestroyableLongIdRecords.liveForAllTenants()
                DestroyableTenantScope.DELETED_FOR_ALL_TENANTS -> tenantScopedDestroyableLongIdRecords.destroyedForAllTenants()
                DestroyableTenantScope.LIVE_AND_DESTROYED_FOR_ALL_TENANTS -> tenantScopedDestroyableLongIdRecords.liveForAllTenants()
            }.update({ Op.build { tenantScopedDestroyableLongIdRecords.id eq record.id } }) { it[titleColumRef!!] = newTitle }
        })
})
