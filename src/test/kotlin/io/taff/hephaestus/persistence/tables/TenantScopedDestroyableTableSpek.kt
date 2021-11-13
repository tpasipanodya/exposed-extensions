package io.taff.hephaestus.persistence

import com.taff.hephaestustest.expectation.any.satisfy
import com.taff.hephaestustest.expectation.should
import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.models.TenantScopedModel
import io.taff.hephaestus.persistence.tables.uuid.TenantScopedDestroyableTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder.ASC
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.fail
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.util.*

/** Dummy tenant scoped model for testing */
data class TenantScopedDestroyableRecord(
    val title: String? = null,
    override var tenantId: UUID? = null,
    override var id: UUID? = null,
    override var createdAt: OffsetDateTime? = null,
    override var updatedAt: OffsetDateTime? = null,
     override var destroyedAt: OffsetDateTime? = null
) : TenantScopedModel, DestroyableModel

/** Dummy tenant scoped t able for testing */
val tenantScopedDestroyableRecords = object : TenantScopedDestroyableTable<TenantScopedDestroyableRecord>("tenant_scoped_destroyable_records") {
    val title = varchar("title", 50)
    override fun initializeModel(row: ResultRow) = TenantScopedDestroyableRecord(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: TenantScopedDestroyableRecord) {
        model.title?.let { stmt[title] = it }
    }
}

object TenantScopedDestroyableTableSpek : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(tenantScopedDestroyableRecords) }

    val tenantId by memoized { UUID.randomUUID() }
    val otherTenantId by memoized { UUID.randomUUID() }

    beforeEachTest { transaction { tenantScopedDestroyableRecords.deleteAll() } }

    afterEachTest { clearCurrentTenantId() }

    val tenant1Record1 by memoized { TenantScopedDestroyableRecord("Soul food") }
    val tenant1Record2 by memoized { TenantScopedDestroyableRecord("Groovy Soul food") }
    val tenant2Record1 by memoized { TenantScopedDestroyableRecord("Smooth Soul food") }
    val tenant2Record2 by memoized { TenantScopedDestroyableRecord("Bada-boom Soul food") }
    val persisted by memoized {
        transaction {
            setCurrentTenantId(tenantId)
            tenantScopedDestroyableRecords.insert(tenant1Record1, tenant1Record2)

            setCurrentTenantId(otherTenantId)
            tenantScopedDestroyableRecords.insert(tenant2Record1, tenant2Record2)
        }
    }
    val reloaded by memoized {
        transaction {
            tenantScopedDestroyableRecords
                    .selectAll()
                    .orderBy(tenantScopedDestroyableRecords.createdAt, ASC)
                    .map(tenantScopedDestroyableRecords::toModel)
        }
    }

    describe("delete") {
        it("hard deletes the record") {
            persisted should satisfy { all { it.isPersisted() } }

            val deleted = transaction { tenantScopedDestroyableRecords.delete(tenant2Record2) }

            deleted should satisfy {
                size == 1 &&
                this[0].run { title == tenant2Record2.title && !destroyedAt.isNull() }
            }

            reloaded should satisfy {
                size == 3 &&
                this[0].title == tenant1Record1.title &&
                this[1].title == tenant1Record2.title &&
                this[2].title == tenant2Record1.title
            }

            tenant2Record2 should satisfy { !destroyedAt.isNull() }
        }

        context("attempting to delete another tenant's records") {
            val deleted by memoized {
                setCurrentTenantId(tenantId)
                transaction { tenantScopedDestroyableRecords.delete(tenant2Record2) }
            }

            it("doesn't delete the record because of tenant isolation") {
                persisted should satisfy { all { it.isPersisted() } }

                try {
                    deleted
                    fail("Expected an exception to be raised but none was")
                } catch(e: TenantError) {
                    e.message should satisfy {
                        this == "Cannot destroy models because they belong to a different tenant."
                    }
                }

                reloaded should satisfy {
                    size == 4 &&
                    this[0].run { title == tenant1Record1.title && destroyedAt.isNull() } &&
                    this[1].run { title == tenant1Record2.title && destroyedAt.isNull() } &&
                    this[2].run { title == tenant2Record1.title && destroyedAt.isNull() } &&
                    this[3].run { title == tenant2Record2.title && destroyedAt.isNull() }
                }
            }
        }
    }

    describe("destroy") {
        it("soft deletes the record") {
            persisted should satisfy { all { it.isPersisted() } }

            setCurrentTenantId(otherTenantId)
            transaction {
                tenantScopedDestroyableRecords.destroy(this, tenant2Record2)
            } should satisfy {
                size == 1 &&
                this[0].run { title == tenant2Record2.title && !destroyedAt.isNull() }
            }

            reloaded should satisfy {
                size == 4 &&
                this[0].run { title == tenant1Record1.title && destroyedAt.isNull() } &&
                this[1].run { title == tenant1Record2.title && destroyedAt.isNull() } &&
                this[2].run { title == tenant2Record1.title && destroyedAt.isNull() } &&
                this[3].run { title == tenant2Record2.title && !destroyedAt.isNull() }
            }
        }

        context("attempting to destroy another tenant's records") {
            val destroyed by memoized {
                setCurrentTenantId(tenantId)
                transaction { tenantScopedDestroyableRecords.destroy(this, tenant2Record2) }
            }

            it("doesn't soft delete the record because of tenant isolation") {
                persisted should satisfy { all { it.isPersisted() } }

                try {
                    destroyed
                    fail("Expected an exception to be raised but none was")
                } catch(e: TenantError) {
                    e.message should satisfy {
                        this == "Cannot destroy models because they belong to a different tenant."
                    }
                }

                reloaded should satisfy {
                    size == 4 &&
                    this[0].run { title == tenant1Record1.title && destroyedAt.isNull() } &&
                    this[1].run { title == tenant1Record2.title && destroyedAt.isNull() } &&
                    this[2].run { title == tenant2Record1.title && destroyedAt.isNull() } &&
                    this[3].run { title == tenant2Record2.title && destroyedAt.isNull() }
                }
            }
        }
    }
})
