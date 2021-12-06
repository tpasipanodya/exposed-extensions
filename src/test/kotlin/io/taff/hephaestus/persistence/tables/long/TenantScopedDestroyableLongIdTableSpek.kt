package io.taff.hephaestus.persistence.tables.long

import io.taff.hephaestustest.expectation.any.satisfy
import io.taff.hephaestustest.expectation.should
import io.taff.hephaestustest.expectation.shouldNot
import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.TenantError
import io.taff.hephaestus.persistence.clearCurrentTenantId
import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.models.TenantScopedModel
import io.taff.hephaestus.persistence.setCurrentTenantId
import io.taff.hephaestus.persistence.tables.shared.includeTenantScopedDestroyableTableSpeks
import io.taff.hephaestustest.expectation.any.equal
import io.taff.hephaestustest.expectation.boolean.beTrue
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.fail
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Instant


/** Dummy tenant scoped model for testing */
data class TenantScopedDestroyableLongIdRecord(
    var title: String? = null,
    override var tenantId: Long? = null,
    override var id: Long? = null,
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null,
    override var destroyedAt: Instant? = null
) : TenantScopedModel<Long, Long>, DestroyableModel<Long>

/** Dummy tenant scoped t able for testing */
val tenantScopedDestroyableLongIdRecords = object : TenantScopedDestroyableLongIdTable<Long, TenantScopedDestroyableLongIdRecord>("tenant_scoped_destroyable_long_id_records") {
    val title = varchar("title", 50)
    override val tenantId: Column<Long> = long("tenant_id")
    override fun initializeModel(row: ResultRow) = TenantScopedDestroyableLongIdRecord(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: TenantScopedDestroyableLongIdRecord) {
        model.title?.let { stmt[title] = it }
    }
}

object TenantScopedDestroyableTableSpek : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(tenantScopedDestroyableLongIdRecords) }

    val tenantId by memoized { 1L }
    val otherTenantId by memoized { 2L }

    beforeEachTest { transaction { tenantScopedDestroyableLongIdRecords.deleteAll() } }

    afterEachTest { clearCurrentTenantId() }

    val tenant1Record1 by memoized { TenantScopedDestroyableLongIdRecord("Soul food") }
    val tenant1Record2 by memoized { TenantScopedDestroyableLongIdRecord("Groovy Soul food") }
    val tenant2Record1 by memoized { TenantScopedDestroyableLongIdRecord("Smooth Soul food") }
    val tenant2Record2 by memoized { TenantScopedDestroyableLongIdRecord("Bada-boom Soul food") }
    val persisted by memoized {
        transaction {
            setCurrentTenantId(tenantId)
            tenantScopedDestroyableLongIdRecords.insert(tenant1Record1, tenant1Record2)

            setCurrentTenantId(otherTenantId)
            tenantScopedDestroyableLongIdRecords.insert(tenant2Record1, tenant2Record2)
        }
    }
    val reloaded by memoized {
        transaction {
            tenantScopedDestroyableLongIdRecords
                .selectAll()
                .orderBy(tenantScopedDestroyableLongIdRecords.createdAt, SortOrder.ASC)
                .map(tenantScopedDestroyableLongIdRecords::toModel)
        }
    }

    describe("insert") {
        val reloaded by memoized {
            transaction {
                tenantScopedDestroyableLongIdRecords
                    .selectAll()
                    .map(tenantScopedDestroyableLongIdRecords::toModel)
            }
        }

        context("with tenant id set") {
            beforeEachTest { setCurrentTenantId(tenantId) }

            val persisted by memoized {
                transaction {
                    tenantScopedDestroyableLongIdRecords
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
                    clearCurrentTenantId<Long>()
                    tenantScopedDestroyableLongIdRecords
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
                    tenantScopedDestroyableLongIdRecords
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
        val otherTenantId by memoized { 20L }
        val newTitle by memoized { "groovy soul food" }
        val persisted by memoized {
            setCurrentTenantId(tenantId)
            transaction {
                tenantScopedDestroyableLongIdRecords
                    .insert(tenant1Record1)
                    .first()
            }
        }
        val reloaded by memoized {
            transaction {
                tenantScopedDestroyableLongIdRecords
                    .selectAll()
                    .orderBy(tenantScopedDestroyableLongIdRecords.createdAt, SortOrder.ASC)
                    .map(tenantScopedDestroyableLongIdRecords::toModel)
            }
        }

        context("with tenantId correctly set") {
            val updated by memoized {
                transaction {
                    setCurrentTenantId(tenantId)
                    tenantScopedDestroyableLongIdRecords
                        .update(persisted.copy(title = newTitle))
                }
            }

            it("updates") {
                persisted should satisfy { title == tenant1Record1.title }
                updated should beTrue()
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
                    tenantScopedDestroyableLongIdRecords
                        .update(persisted.copy(title = newTitle))
                }
            }

            it("doesn't update because of tenant isolation") {
                persisted should satisfy { title == tenant1Record1.title }

                try { updated; fail("Expcted a tenant error but non was raised.")
                } catch (e: TenantError) { e.message should satisfy { this == "Model ${persisted.id} can't be persisted because it doesn't belong to the current tenant." } }

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
                clearCurrentTenantId<Long>()
                transaction {
                    tenantScopedDestroyableLongIdRecords
                        .update(persisted.copy(title = newTitle))
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

            val deleted = transaction { tenantScopedDestroyableLongIdRecords.delete(tenant2Record2) }

            deleted should equal(true)

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
                transaction { tenantScopedDestroyableLongIdRecords.delete(tenant2Record2) }
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

            transaction {
                setCurrentTenantId(otherTenantId)
                tenantScopedDestroyableLongIdRecords.destroy(tenant2Record2)
            } should beTrue()

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
                transaction { tenantScopedDestroyableLongIdRecords.destroy(tenant2Record2) }
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
