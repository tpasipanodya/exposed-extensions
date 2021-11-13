package io.taff.hephaestus.persistence.tables

import com.taff.hephaestustest.expectation.any.satisfy
import com.taff.hephaestustest.expectation.should
import com.taff.hephaestustest.expectation.shouldNot
import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.TenantError
import io.taff.hephaestus.persistence.clearCurrentTenantId
import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.models.TenantScopedModel
import io.taff.hephaestus.persistence.setCurrentTenantId
import io.taff.hephaestus.persistence.tables.uuid.TenantScopedDestroyableTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SortOrder.ASC
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.fail
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.util.*

/** Dummy tenant scoped model for testing */
data class TenantScopedDestroyableRecord(
    var title: String? = null,
    override var tenantId: UUID? = null,
    override var id: UUID? = null,
    override var createdAt: OffsetDateTime? = null,
    override var updatedAt: OffsetDateTime? = null,
     override var destroyedAt: OffsetDateTime? = null
) : TenantScopedModel<UUID, UUID>, DestroyableModel<UUID>

/** Dummy tenant scoped t able for testing */
val tenantScopedDestroyableRecords = object : TenantScopedDestroyableTable<UUID, TenantScopedDestroyableRecord>("tenant_scoped_destroyable_records") {
    val title = varchar("title", 50)
    override val tenantId: Column<UUID> = uuid("tenant_id")
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

    describe("insert") {
        val reloaded by memoized {
            transaction {
                tenantScopedDestroyableRecords
                    .selectAll()
                    .map(tenantScopedDestroyableRecords::toModel)
            }
        }

        context("with tenant id set") {
            beforeEachTest { setCurrentTenantId(tenantId) }

            val persisted by memoized {
                transaction {
                    tenantScopedDestroyableRecords
                        .insert(tenant1Record1)
                        .first()
                }
            }

            it("persists") {
                persisted should satisfy {
                    this == tenant1Record1 &&
                    isPersisted() &&
                    tenantId == tenantId
                }

                tenant1Record1 should satisfy { isPersisted() }

                reloaded should satisfy {
                    size == 1 &&
                    first().let {
                        it.title == tenant1Record1.title &&
                        it.tenantId == tenantId &&
                        !it.createdAt.isNull() &&
                        !it.updatedAt.isNull()
                    }
                }
            }
        }

        context("when tenantId not set") {
            val persisted by memoized {
                transaction {
                    clearCurrentTenantId<UUID>()
                    tenantScopedDestroyableRecords
                        .insert(tenant1Record1)
                        .first()
                }
            }

            it("doesn't persist") {
                try { persisted; fail("Expected an error to be raised but non was") }
                catch (e: TenantError) { e should satisfy { message == "Model ${tenant1Record1.id} can't be persisted because There's no current tenant Id set." } }

                tenant1Record1 shouldNot satisfy { isPersisted() }
                reloaded should satisfy { isEmpty() }
            }
        }

        context("attempting to insert another tenant's records") {
            val persisted by memoized {
                transaction {
                    tenant1Record1.tenantId = tenantId
                    setCurrentTenantId(otherTenantId)
                    tenantScopedDestroyableRecords
                        .insert(tenant1Record1)
                        .first()
                }
            }

            it("doesn't persist") {
                try { persisted; fail("Expected an error to be raised but non was") }
                catch (e: TenantError) { e should satisfy { message == "Model ${tenant1Record1.id} can't be persisted because it doesn't belong to the current tenant." } }

                tenant1Record1 shouldNot satisfy { isPersisted() }
                reloaded should satisfy { isEmpty() }
            }
        }
    }

    describe("update") {
        val otherTenantId by memoized { UUID.randomUUID() }
        val newTitle by memoized { "groovy soul food" }
        val persisted by memoized {
            setCurrentTenantId(tenantId)
            transaction {
                tenantScopedDestroyableRecords
                    .insert(tenant1Record1)
                    .first()
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

        context("with tenantId correctly set") {
            val updated by memoized {
                transaction {
                    setCurrentTenantId(tenantId)
                    tenantScopedDestroyableRecords
                        .update(this, persisted.copy(title = newTitle))
                        .first()
                }
            }

            it("updates") {
                persisted should satisfy { title == tenant1Record1.title }

                updated should satisfy {
                    isPersisted() &&
                    title == newTitle &&
                    this.tenantId == tenantId
                }

                reloaded should satisfy {
                    size == 1 &&
                    first().let {
                        it.title == newTitle &&
                        !it.createdAt.isNull() &&
                        !it.updatedAt.isNull() &&
                        it.tenantId == tenantId
                    }
                }
            }
        }

        context("attempting to update another tenant's records") {
            val updated by memoized {
                setCurrentTenantId(otherTenantId)
                transaction {
                    tenantScopedDestroyableRecords
                        .update(this, persisted.copy(title = newTitle))
                }
            }

            it("doesn't update because of tenant isolation") {
                persisted should satisfy { title == tenant1Record1.title }

                try { updated; fail("Expcted a tenant error but non was raised.")
                } catch (e:  TenantError) { e.message should satisfy { this == "Model ${persisted.id} can't be persisted because it doesn't belong to the current tenant." } }

                reloaded should satisfy {
                    size == 1 &&
                    first().let {
                        it.title == persisted.title &&
                        !it.createdAt.isNull() &&
                        !it.updatedAt.isNull() &&
                        it.tenantId == tenantId
                    }
                }
            }
        }

        context("No tenant id set") {
            val updated by memoized {
                clearCurrentTenantId<UUID>()
                transaction {
                    tenantScopedDestroyableRecords
                        .update(this, persisted.copy(title = newTitle))
                }
            }

            it("doesn't update because of tenant isolation") {
                persisted should satisfy { title == tenant1Record1.title }

                try { updated; fail("Expected an error but non was raised.")
                } catch (e:  Exception) { e.message should satisfy { this == "Model ${persisted.id} can't be persisted because There's no current tenant Id set." } }

                reloaded should satisfy {
                    size == 1 &&
                    first().let {
                        it.title == persisted.title &&
                        !it.createdAt.isNull() &&
                        !it.updatedAt.isNull() &&
                        it.tenantId == tenantId
                    }
                }
            }
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
