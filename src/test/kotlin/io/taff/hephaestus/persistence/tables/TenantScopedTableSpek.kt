package io.taff.hephaestus.persistence

import com.taff.hephaestustest.expectation.any.satisfy
import com.taff.hephaestustest.expectation.should
import com.taff.hephaestustest.expectation.shouldNot
import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.models.TenantScopedModel
import io.taff.hephaestus.persistence.tables.TenantScopedTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.fail
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.util.*

/** Dummy tenant scoped model for testing */
data class TenantScopedRecord(val title: String? = null,
                              override var tenantId: UUID? = null,
                              override var id: UUID? = null,
                              override var createdAt: OffsetDateTime? = null,
                              override var updatedAt: OffsetDateTime? = null) : TenantScopedModel

/** Dummy tenant scoped t able for testing */
val tenantScopedRecords = object : TenantScopedTable<TenantScopedRecord>("tenant_scoped_records") {
    val title = varchar("title", 50)
    override val tenantId = uuid("tenant_id")
    override fun initializeModel(row: ResultRow) = TenantScopedRecord(title = row[title])
    override fun fillStatement(stmt: UpdateBuilder<Int>, model: TenantScopedRecord) {
        model.title?.let { stmt[title] = it }
        super.fillStatement(stmt, model)
    }
}

object TenantScopedTableSpek : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(tenantScopedRecords) }

    val record by memoized { TenantScopedRecord("Soul food", null) }
    val tenantId by memoized { UUID.randomUUID() }

    beforeEachTest { transaction { tenantScopedRecords.deleteAll() } }

    afterEachTest { clearCurrentTenantId() }

    describe("insert") {
        val reloaded by memoized {
            transaction {
                tenantScopedRecords
                    .selectAll()
                    .map(tenantScopedRecords::toModel)
            }
        }

        context("with tenant id set") {
            beforeEachTest { setCurrentTenantId(tenantId) }

            val persisted by memoized {
                transaction {
                    tenantScopedRecords
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
                    clearCurrentTenantId()
                    tenantScopedRecords
                        .insert(record)
                        .first()
                }
            }

            it("doesn't persist") {
                try { persisted; fail("Expected an error to be raised but non was") }
                catch (e: TenantError) { e should satisfy { message == "Model ${record.id} can't be persisted because There's no current tenant Id set." } }
                record shouldNot satisfy { isPersisted() }
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
                tenantScopedRecords
                    .insert(TenantScopedRecord("Soul food"))
                    .first()
            }
        }
        val reloaded by memoized {
            transaction {
                tenantScopedRecords
                    .selectAll()
                    .orderBy(tenantScopedRecords.createdAt, SortOrder.ASC)
                    .map(tenantScopedRecords::toModel)
            }
        }

        context("with tenantId correctly set") {
            val updated by memoized {
                transaction {
                    setCurrentTenantId(tenantId)
                    tenantScopedRecords
                        .update(this, persisted.copy(title = newTitle))
                        .first()
                }
            }

            it("updates") {
                persisted should satisfy { title == record.title }

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
                    tenantScopedRecords
                      .update(this, persisted.copy(title = newTitle))
                }
            }

            it("doesn't update because of tenant isolation") {
                persisted should satisfy { title == record.title }

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
                clearCurrentTenantId()
                transaction {
                    tenantScopedRecords
                      .update(this, persisted.copy(title = newTitle))
                }
            }

            it("doesn't update because of tenant isolation") {
                persisted should satisfy { title == record.title }

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
        val otherTenantId by memoized { UUID.randomUUID() }
        val persisted by memoized {
            transaction {
                setCurrentTenantId(tenantId)
                tenantScopedRecords.insert(TenantScopedRecord("Super Smooth Soul food"))
                setCurrentTenantId(otherTenantId)
                tenantScopedRecords.insert(TenantScopedRecord("Soul food"))
                tenantScopedRecords.insert(TenantScopedRecord("Groovy soul food")).first()
            }
        }
        val remaining by memoized {
            transaction {
                tenantScopedRecords
                        .selectAll()
                        .orderBy(tenantScopedRecords.createdAt, SortOrder.ASC)
                        .map(tenantScopedRecords::toModel)
            }
        }

        context("tenant Id set and deleting the tenant's records") {
            val deleted by memoized {
                setCurrentTenantId(otherTenantId)
                transaction { tenantScopedRecords.delete(persisted) }
            }

            it("deletes the records") {
                deleted should satisfy { size == 1 }

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
                    tenantScopedRecords.delete(persisted)
                }
            }

            it("deletes the records") {
                persisted should satisfy { !isNull() }

                try { deleted; fail("Expected an error to be raised but non was") }
                catch(e: TenantError) { e.message should satisfy { this == "Cannot destroy models because they belong to a different tenant." } }

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
                clearCurrentTenantId()
                transaction { tenantScopedRecords.delete(persisted) }
            }

            it("doesn't delete the records") {
                persisted should satisfy { !isNull() }

                try { deleted; fail("Expected an error to be raised but non was") }
                catch(e: TenantError) { e.message should satisfy { this == "Cannot destroy models because there is no CurrentTenantId." } }

                remaining should satisfy { size == 3 }
            }
        }
    }
})