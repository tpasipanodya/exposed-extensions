package io.taff.hephaestus.persistence.tables.uuid

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
import java.util.*

/** Dummy tenant scoped model for testing */
data class TenantScopedDestroyableUuidRecord(
    override var title: String? = null,
    override var tenantId: UUID? = null,
    override var id: UUID? = null,
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null,
    override var destroyedAt: Instant? = null
) : TitleAware, TenantScopedModel<UUID, UUID>, DestroyableModel<UUID>

/** Dummy tenant scoped t able for testing */
var titleColum: Column<String>? = null
val tenantScopedDestroyableUuidRecords = object : TenantScopedDestroyableUuidTable<UUID, TenantScopedDestroyableUuidRecord>("tenant_scoped_destroyable_uuid_records") {
    val title = varchar("title", 50)
    init { titleColum = title }
    override val tenantId: Column<UUID> = uuid("tenant_id")
    override fun initializeModel(row: ResultRow) = TenantScopedDestroyableUuidRecord(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: TenantScopedDestroyableUuidRecord) {
        model.title?.let { stmt[title] = it }
    }
}

object TenantScopedDestroyableUuidTableSpek : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(tenantScopedDestroyableUuidRecords) }

    beforeEachTest {
        transaction {
            tenantScopedDestroyableUuidRecords.stripDefaultScope().deleteAll()
        }
    }

    afterEachTest { clearCurrentTenantId() }

    includeTenantScopedDestroyableTableSpeks(tenantScopedDestroyableUuidRecords,
        tenantIdFunc = { UUID.randomUUID() },
        otherTenantIdFunc = { UUID.randomUUID() },
        tenant1RecordsFunc = {
            arrayOf(TenantScopedDestroyableUuidRecord("Soul food"),
                TenantScopedDestroyableUuidRecord("Groovy Soul food"))
        }, tenant2RecordsFunc = {
            arrayOf(TenantScopedDestroyableUuidRecord("Smooth Soul food"),
                TenantScopedDestroyableUuidRecord("Bada-boom Soul food"))
        }, directUpdateFunc = { record, newTitle, scope ->
        when(scope) {
            DestroyableTenantScope.LIVE_FOR_TENANT -> tenantScopedDestroyableUuidRecords
            DestroyableTenantScope.DELETED_FOR_TENANT -> tenantScopedDestroyableUuidRecords.destroyed()
            DestroyableTenantScope.LIVE_AND_DESTROYED_FOR_CURRENT_TENANT -> tenantScopedDestroyableUuidRecords.liveAndDestroyed()
            DestroyableTenantScope.LIVE_FOR_ALL_TENANTS -> tenantScopedDestroyableUuidRecords.liveForAllTenants()
            DestroyableTenantScope.DELETED_FOR_ALL_TENANTS -> tenantScopedDestroyableUuidRecords.destroyedForAllTenants()
            DestroyableTenantScope.LIVE_AND_DESTROYED_FOR_ALL_TENANTS -> tenantScopedDestroyableUuidRecords.liveForAllTenants()
        }.update({ Op.build { tenantScopedDestroyableUuidRecords.id eq record.id } }) { it[titleColumn!!] = newTitle }
    })
})
