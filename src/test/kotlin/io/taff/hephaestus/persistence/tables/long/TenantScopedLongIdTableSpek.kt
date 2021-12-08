package io.taff.hephaestus.persistence.tables.long

import io.taff.hephaestustest.expectation.any.satisfy
import io.taff.hephaestustest.expectation.should
import io.taff.hephaestustest.expectation.shouldNot
import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.TenantError
import io.taff.hephaestus.persistence.clearCurrentTenantId
import io.taff.hephaestus.persistence.models.TenantScopedModel
import io.taff.hephaestus.persistence.setCurrentTenantId
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
data class TenantScopedLongIdModel(
    var title: String? = null,
    override var tenantId: Long? = null,
    override var id: Long? = null,
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null
) : TenantScopedModel<Long, Long>

/** Dummy tenant scoped t able for testing */
val tenantScopedLongIdRecords = object : TenantScopedLongIdTable<Long, TenantScopedLongIdModel>("tenant_scoped_long_id_records") {
    val title = varchar("title", 50)
    override val tenantId: Column<Long> = long("tenant_id")
    override fun initializeModel(row: ResultRow) = TenantScopedLongIdModel(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: TenantScopedLongIdModel) {
        model.title?.let { stmt[title] = it }
    }
}

object TenantScopedLongIdTableSpek : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(tenantScopedLongIdRecords) }

    val record by memoized { TenantScopedLongIdModel("Soul food", null) }
    val tenantId by memoized { 1L }

    beforeEachTest { transaction { tenantScopedLongIdRecords.deleteAll() } }

    afterEachTest { clearCurrentTenantId() }


    describe("insert") {
        val reloaded by memoized {
            transaction {
                tenantScopedLongIdRecords
                    .selectAll()
                    .map(tenantScopedLongIdRecords::toModel)
            }
        }

        context("with tenant id set") {
            beforeEachTest { setCurrentTenantId(tenantId) }

            val persisted by memoized {
                transaction {
                    tenantScopedLongIdRecords
                        .insert(record)
                        .first()
                }
            }

            it("persists") {
                persisted should satisfy {
                    this == record &&
                    isPersisted() &&
                    tenantId == tenantId
                }
                record should satisfy { isPersisted() }

                reloaded should satisfy {
                    size == 1 &&
                    first().let {
                        it.title == record.title &&
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
                    tenantScopedLongIdRecords
                        .insert(record)
                        .first()
                }
            }

            it("doesn't persist") {
                try {
                    persisted; fail("Expected an error to be raised but non was")
                } catch (e: TenantError) {
                    e should satisfy { message == "Model ${record.id} can't be persisted because There's no current tenant Id set." }
                }
                record shouldNot satisfy { isPersisted() }
                reloaded should satisfy { isEmpty() }
            }
        }
    }

    describe("update") {
        val otherTenantId by memoized { 10L }
        val newTitle by memoized { "groovy soul food" }
        val persisted by memoized {
            setCurrentTenantId(tenantId)
            transaction {
                tenantScopedLongIdRecords
                    .insert(TenantScopedLongIdModel("Soul food"))
                    .first()
            }
        }
        val reloaded by memoized {
            transaction {
                tenantScopedLongIdRecords
                    .selectAll()
                    .orderBy(tenantScopedLongIdRecords.createdAt, SortOrder.ASC)
                    .map(tenantScopedLongIdRecords::toModel)
            }
        }

        context("with tenantId correctly set") {
            val updated by memoized {
                transaction {
                    setCurrentTenantId(tenantId)
                    tenantScopedLongIdRecords
                        .update(persisted.copy(title = newTitle))
                }
            }

            it("updates") {
                persisted should satisfy { title == record.title }
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
                    tenantScopedLongIdRecords
                        .update(persisted.copy(title = newTitle))
                }
            }

            it("doesn't update because of tenant isolation") {
                persisted should satisfy { title == record.title }

                try {
                    updated; fail("Expcted a tenant error but non was raised.")
                } catch (e: TenantError) {
                    e.message should satisfy { this == "Model ${persisted.id} can't be persisted because it doesn't belong to the current tenant." }
                }

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
                    tenantScopedLongIdRecords
                        .update(persisted.copy(title = newTitle))
                }
            }

            it("doesn't update because of tenant isolation") {
                persisted should satisfy { title == record.title }

                try {
                    updated; fail("Expected an error but non was raised.")
                } catch (e: Exception) {
                    e.message should satisfy { this == "Model ${persisted.id} can't be persisted because There's no current tenant Id set." }
                }

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
        val otherTenantId by memoized { 1000L }
        val persisted by memoized {
            transaction {
                setCurrentTenantId(tenantId)
                tenantScopedLongIdRecords.insert(TenantScopedLongIdModel("Super Smooth Soul food"))
                setCurrentTenantId(otherTenantId)
                tenantScopedLongIdRecords.insert(TenantScopedLongIdModel("Soul food"))
                tenantScopedLongIdRecords.insert(TenantScopedLongIdModel("Groovy soul food")).first()
            }
        }
        val remaining by memoized {
            transaction {
                tenantScopedLongIdRecords
                    .selectAll()
                    .orderBy(tenantScopedLongIdRecords.createdAt, SortOrder.ASC)
                    .map(tenantScopedLongIdRecords::toModel)
            }
        }

        context("tenant Id set and deleting the tenant's records") {
            val deleted by memoized {
                setCurrentTenantId(otherTenantId)
                transaction { tenantScopedLongIdRecords.delete(persisted) }
            }

            it("deletes the records") {
                deleted should equal(true)

                remaining should satisfy {
                    size == 2 &&
                    get(0).title == "Super Smooth Soul food" &&
                    get(1).title == "Soul food"
                }
            }
        }

        context("tenant Id set and attempting to delete another tenant's records") {
            val deleted by memoized {
                transaction {
                    setCurrentTenantId(tenantId)
                    tenantScopedLongIdRecords.delete(persisted)
                }
            }

            it("deletes the records") {
                persisted should satisfy { !isNull() }

                try {
                    deleted; fail("Expected an error to be raised but non was")
                } catch (e: TenantError) {
                    e.message should satisfy { this == "Cannot delete models because they belong to a different tenant." }
                }

                remaining should satisfy {
                    size == 3 &&
                    get(0).title == "Super Smooth Soul food" &&
                    get(1).title == "Soul food" &&
                    get(2).title == "Groovy soul food"
                }
            }
        }

        context("With no tenant id set") {
            val deleted by memoized {
                clearCurrentTenantId<Long>()
                transaction { tenantScopedLongIdRecords.delete(persisted) }
            }

            it("doesn't delete the records") {
                persisted should satisfy { !isNull() }
                try { deleted; fail("Expected an error to be raised but non was") }
                catch (e: TenantError) { e.message should satisfy { this == "Cannot delete models because there is no CurrentTenantId." } }
                remaining should satisfy { size == 3 }
            }
        }
    }
})
