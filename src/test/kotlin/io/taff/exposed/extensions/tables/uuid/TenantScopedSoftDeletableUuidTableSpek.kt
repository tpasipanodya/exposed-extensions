package io.taff.exposed.extensions.tables.uuid

import io.taff.exposed.extensions.clearCurrentTenantId
import io.taff.exposed.extensions.helpers.env
import io.taff.exposed.extensions.models.SoftDeletableModel
import io.taff.exposed.extensions.models.TenantScopedModel
import io.taff.exposed.extensions.tables.shared.TitleAware
import io.taff.exposed.extensions.tables.shared.includeTenantScopedSoftDeletableTableSpeks
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import java.time.Instant
import java.util.*

/** Dummy tenant scoped model for testing */
data class TenantScopedSoftDeletableUuidRecord(
    override var title: String? = null,
    override var tenantId: UUID? = null,
    override var id: UUID? = null,
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null,
    override var softDeletedAt: Instant? = null
) : TitleAware, TenantScopedModel<UUID, UUID>, SoftDeletableModel<UUID>

/** Dummy tenant scoped t able for testing */
var softDeleteTenantScopedTitleColumn: Column<String>? = null
val tenantScopedSoftDeletableUuidRecords = object : TenantScopedSoftDeletableUuidTable<UUID, TenantScopedSoftDeletableUuidRecord>("tenant_scoped_soft_deletable_uuid_records") {
    val title = varchar("title", 50)
    init { softDeleteTenantScopedTitleColumn = title }
    override val tenantId: Column<UUID> = uuid("tenant_id")
    override fun initializeModel(row: ResultRow) = TenantScopedSoftDeletableUuidRecord(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: TenantScopedSoftDeletableUuidRecord) {
        model.title?.let { stmt[title] = it }
    }
}

object TenantScopedSoftDeletableUuidTableSpek : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(tenantScopedSoftDeletableUuidRecords) }

    beforeEachTest {
        transaction {
            tenantScopedSoftDeletableUuidRecords.stripDefaultScope().deleteAll()
        }
    }

    afterEachTest { clearCurrentTenantId() }

    includeTenantScopedSoftDeletableTableSpeks(
        tenantScopedSoftDeletableUuidRecords,
        tenantIdFunc = { UUID.randomUUID() },
        otherTenantIdFunc = { UUID.randomUUID() },
        tenant1RecordsFunc = {
            arrayOf(TenantScopedSoftDeletableUuidRecord("Soul food"),
                TenantScopedSoftDeletableUuidRecord("Groovy Soul food"))
        }, tenant2RecordsFunc = {
            arrayOf(TenantScopedSoftDeletableUuidRecord("Smooth Soul food"),
                TenantScopedSoftDeletableUuidRecord("Bada-boom Soul food"))
        }, titleColumnRef = softDeleteTenantScopedTitleColumn!!
    )
})