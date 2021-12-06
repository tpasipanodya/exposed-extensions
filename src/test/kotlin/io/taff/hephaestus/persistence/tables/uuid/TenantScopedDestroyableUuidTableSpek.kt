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

//
//    xdescribe("destroy") {
//        val recordsFor = { tenantId: UUID ->
//            transaction {
//                tenantScopedDestroyableUuidRecords
//                    .stripDefaultScope()
//                    .select { tenantScopedDestroyableUuidRecords.tenantId eq tenantId }
//                    .orderBy(tenantScopedDestroyableUuidRecords.createdAt, ASC)
//                    .map(tenantScopedDestroyableUuidRecords::toModel)
//            }
//        }
//        it("soft deletes the record") {
//            persisted should satisfy { all { it.isPersisted() } }
//
//            setCurrentTenantId(otherTenantId)
//            transaction {
//                tenantScopedDestroyableUuidRecords.destroy(tenant2Record2)
//            } should beTrue()
//            recordsFor(tenantId) should satisfy {
//                size == 2 &&
//                this[0].run { title == tenant1Record1.title && destroyedAt.isNull() } &&
//                this[1].run { title == tenant1Record2.title && destroyedAt.isNull() }
//            }
//            recordsFor(otherTenantId) should satisfy {
//                size == 2 &&
//                this[0].run { title == tenant2Record1.title && destroyedAt.isNull() } &&
//                this[1].run { title == tenant2Record2.title && !destroyedAt.isNull() }
//            }
//        }
//
//        context("attempting to destroy another tenant's records") {
//            val destroyed by memoized {
//                setCurrentTenantId(tenantId)
//                transaction { tenantScopedDestroyableUuidRecords.destroy(tenant2Record2) }
//            }
//
//            it("doesn't soft delete the record because of tenant isolation") {
//                persisted should satisfy { all { it.isPersisted() } }
//
//                try {
//                    destroyed
//                    fail("Expected an exception to be raised but none was")
//                } catch(e: TenantError) {
//                    e.message should satisfy {
//                        this == "Cannot destroy models because they belong to a different tenant."
//                    }
//                }
//
//                reloaded should satisfy {
//                    size == 4 &&
//                    this[0].run { title == tenant1Record1.title && destroyedAt.isNull() } &&
//                    this[1].run { title == tenant1Record2.title && destroyedAt.isNull() } &&
//                    this[2].run { title == tenant2Record1.title && destroyedAt.isNull() } &&
//                    this[3].run { title == tenant2Record2.title && destroyedAt.isNull() }
//                }
//            }
//        }
//    }
})
