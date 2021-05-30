package io.taff.hephaestus.persistence

import com.taff.hephaestustest.expectation.any.satisfy
import com.taff.hephaestustest.expectation.iterable.beAnOrderedCollectionOf
import com.taff.hephaestustest.expectation.iterable.beAnUnOrderedCollectionOf
import com.taff.hephaestustest.expectation.should
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

/** Dummy tenant scoped model for testing */
data class TenantScopedRecord(val title: String? = null,
                              override var tenantId: Long? = null,
                              override var id: Long? = null,
                              override var createdAt: OffsetDateTime? = null,
                              override var updatedAt: OffsetDateTime? = null) : TenantScopedModel<Long>

/** Dummy tenant scoped t able for testing */
val tenantScopedRecords = object : TenantScopedTable<Long, TenantScopedRecord>("tenant_scoped_records") {
    val title = varchar("title", 50)
    override val tenantId = long("tenant_id")
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
    val tenantId by memoized { 1L }

    beforeEachTest { transaction { tenantScopedRecords.deleteAll() } }

    afterEachTest { clearCurrentTenantId() }

    describe("insert") {

        beforeEachTest { setCurrentTenantId(tenantId) }

        context("with non iterables") {
            val persisted by memoized { transaction { tenantScopedRecords.insert(record) } }
            val reloaded by memoized { transaction { tenantScopedRecords.selectAll().map(tenantScopedRecords::toModel) } }

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

        context("with iterables") {
            val persisted by memoized { transaction { tenantScopedRecords.insert(listOf(record)) } }
            val reloaded by memoized { transaction { tenantScopedRecords.selectAll().map(tenantScopedRecords::toModel) } }

            it("persists") {
                persisted should beAnOrderedCollectionOf(record)
                    persisted should satisfy {
                    toList().size == 1 &&
                    first().isPersisted() &&
                    first().tenantId == tenantId
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
    }

    describe("update") {
        val otherTenantId by memoized { 100L }
        val newTitle by memoized { "groovy soul food" }
        val persisted by memoized {
            setCurrentTenantId(tenantId)
            transaction { tenantScopedRecords.insert(TenantScopedRecord("Soul food")) }
        }

        context("with non-iterables") {
            val updated by memoized {
                transaction {
                    setCurrentTenantId(tenantId)
                    tenantScopedRecords
                        .update(this, persisted.copy(title = newTitle))
                }
            }
            val reloaded by memoized {
                transaction {
                    tenantScopedRecords
                        .selectAll()
                        .map(tenantScopedRecords::toModel)
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

            context("attempting to update another tenant's records") {
                val updated by memoized {
                    setCurrentTenantId(otherTenantId)
                    transaction {
                        tenantScopedRecords
                          .update(this, persisted.copy(title = newTitle))
                    }
                }

                it("updates") {
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

                it("updates") {
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

        context("with an iterable") {
            val updated by memoized {
                transaction {
                    setCurrentTenantId(tenantId)
                    tenantScopedRecords
                      .update(this, listOf(persisted.copy(title = newTitle)))
                }
            }
            val reloaded by memoized {
                transaction {
                    tenantScopedRecords
                      .selectAll()
                      .map(tenantScopedRecords::toModel)
                }
            }

            it("updates") {
                persisted should satisfy { this.title == record.title }
                updated should satisfy {
                    toList().size == 1 &&
                    first().isPersisted() &&
                    first().title == newTitle &&
                    first().tenantId == tenantId
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

            context("attempting to update another tenant's records") {
                val updated by memoized {
                    setCurrentTenantId(otherTenantId)
                    transaction {
                        tenantScopedRecords
                          .update(this, listOf(persisted.copy(title = newTitle)))
                    }
                }

                it("updates") {
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
                          .update(this, listOf(persisted.copy(title = newTitle)))
                    }
                }

                it("updates") {
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
    }

    describe("scopedDelete") {
        val otherTenantId by memoized { 1000L }
        val persisted by memoized {
            transaction {
                setCurrentTenantId(tenantId)
                tenantScopedRecords.insert(TenantScopedRecord("Super Smooth Soul food"))
                setCurrentTenantId(otherTenantId)
                tenantScopedRecords.insert(TenantScopedRecord("Soul food"))
                tenantScopedRecords.insert(TenantScopedRecord("Groovy soul food"))


            }
        }
        val remaining by memoized {
            transaction {
                tenantScopedRecords
                        .selectAll()
                        .orderBy(tenantScopedRecords.id, SortOrder.ASC)
                        .map(tenantScopedRecords::toModel)
            }
        }

        context("tenant Id set and deleting the tenant's records") {
            val deleted by memoized {
                setCurrentTenantId(otherTenantId)
                transaction {
                    tenantScopedRecords.scopedDelete {
                        tenantScopedRecords.id eq persisted.id
                    }
                }
            }

            it("deletes the records") {
                deleted should satisfy { this == 1 }
                remaining should satisfy {
                    size == 2 &&
                    get(0).title == "Super Smooth Soul food" &&
                    get(1).title == "Soul food"
                }
            }
        }

        context("tenant Id set and attempting to delete another tenant's records") {
            val deleted by memoized {
                setCurrentTenantId(tenantId)
                transaction {
                    tenantScopedRecords.scopedDelete {
                        tenantScopedRecords.id eq persisted.id
                    }
                }
            }

            it("deletes the records") {
                deleted should satisfy { this == 0 }
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
                transaction {
                    tenantScopedRecords
                        .scopedDelete { tenantScopedRecords.id eq persisted.id }
                }
            }

            it("doesn't delete the records") {
                deleted should satisfy { this == 0 }
                remaining should satisfy { size == 3 }
            }
        }
    }

    describe("scopedSelect") {
        val otherTenantId by memoized { 1000L }
        val persisted by memoized {
            transaction {
                setCurrentTenantId(tenantId)
                tenantScopedRecords.insert(TenantScopedRecord("Super Smooth Soul food"))
                setCurrentTenantId(otherTenantId)
                tenantScopedRecords.insert(TenantScopedRecord("Soul food"))
                tenantScopedRecords.insert(TenantScopedRecord("Groovy soul food"))
            }
        }
        val tenant1Records by memoized {
            transaction {
                setCurrentTenantId(tenantId)
                tenantScopedRecords
                        .scopedSelect()
                        .orderBy(tenantScopedRecords.id, SortOrder.ASC)
                        .map(tenantScopedRecords::toModel)
            }
        }
        val tenant2Records by memoized {
            transaction {
                setCurrentTenantId(otherTenantId)
                tenantScopedRecords
                        .scopedSelect()
                        .orderBy(tenantScopedRecords.id, SortOrder.ASC)
                        .map(tenantScopedRecords::toModel)
            }
        }

        beforeEachTest { persisted }

        it("correctly filters records") {
            tenant1Records should satisfy {
                size == 1 &&
                first().title == "Super Smooth Soul food"
            }
            tenant2Records should satisfy {
                size == 2 &&
                get(0).title == "Soul food" &&
                get(1).title == "Groovy soul food"
            }
        }

        context("with no tenant Id set") {
            val tenant1Selecteds by memoized {
                transaction {
                    clearCurrentTenantId()
                    tenantScopedRecords
                            .scopedSelect()
                            .orderBy(tenantScopedRecords.id, SortOrder.ASC)
                            .map(tenantScopedRecords::toModel)
                }
            }
            val tenant2Selecteds by memoized {
                transaction {
                    clearCurrentTenantId()
                    tenantScopedRecords
                            .scopedSelect()
                            .orderBy(tenantScopedRecords.id, SortOrder.ASC)
                            .map(tenantScopedRecords::toModel)
                }
            }

            it("doesn't return any records") {
                tenant1Selecteds should beAnUnOrderedCollectionOf()
                tenant2Selecteds should beAnUnOrderedCollectionOf()
            }
        }

        context("attempting to select another tenant's records") {
            val tenant1Selecteds by memoized {
                transaction {
                    setCurrentTenantId(tenantId)
                    tenantScopedRecords
                            .scopedSelect { tenantScopedRecords.id eq persisted.id }
                            .orderBy(tenantScopedRecords.id, SortOrder.ASC)
                            .map(tenantScopedRecords::toModel)
                }
            }

            it("doesn't return any records") {
                tenant1Selecteds should beAnUnOrderedCollectionOf()
            }
        }
    }
})