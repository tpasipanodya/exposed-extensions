package io.taff.hephaestus.persistence

import com.taff.hephaestustest.expectation.any.satisfy
import com.taff.hephaestustest.expectation.iterable.beAnOrderedCollectionOf
import com.taff.hephaestustest.expectation.should
import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.models.TenantScopedModel
import io.taff.hephaestus.persistence.tables.TenantScopedTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime


data class TenantScopedRecord(val title: String? = null,
                              override var tenantId: Long? = null,
                              override var id: Long? = null,
                              override var createdAt: OffsetDateTime? = null,
                              override var updatedAt: OffsetDateTime? = null) : TenantScopedModel<Long>

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

    afterEachTest { unsetCurrentTenantId() }

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
        val newTitle by memoized { "groovy soul food" }
        val persisted by memoized { transaction { tenantScopedRecords.insert(TenantScopedRecord("Soul food")) } }

        beforeEachTest { setCurrentTenantId(tenantId) }

        context("with non-iterables") {
            val updated by memoized {
                transaction {
                    tenantScopedRecords.update(
                        this,
                        persisted.copy(title = newTitle)
                    )
                }
            }
            val reloaded by memoized { transaction { tenantScopedRecords.selectAll().map(tenantScopedRecords::toModel) } }

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

        context("with an iterable") {
            val updated by memoized {
                transaction {
                    tenantScopedRecords.update(
                        this,
                        listOf(persisted.copy(title = newTitle))
                    )
                }
            }
            val reloaded by memoized { transaction { tenantScopedRecords.selectAll().map(tenantScopedRecords::toModel) } }

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
        }
    }

    describe("scopedDelete") {
        val otherTenantId by memoized { 1000L }
        val persisted by memoized {
            transaction {
                val prev = setCurrentTenantId(otherTenantId)
                tenantScopedRecords.insert(TenantScopedRecord("Super Smooth Soul food"))
                setCurrentTenantId(prev)
                tenantScopedRecords.insert(TenantScopedRecord("Soul food"))
                tenantScopedRecords.insert(TenantScopedRecord("Groovy soul food"))


            }
        }
        val deleted by memoized { transaction { tenantScopedRecords.scopedDelete { tenantScopedRecords.id eq persisted.id } } }
        val remaining by memoized {
            tenantScopedRecords
                .selectAll()
                .orderBy(tenantScopedRecords.id, SortOrder.ASC)
                .map(tenantScopedRecords::toModel)
        }

        context("With a tenant id correctly set") {
            beforeEachTest { setCurrentTenantId(tenantId) }
            afterEachTest { unsetCurrentTenantId() }
        }

        it("deletes") {
            deleted should satisfy { this == 1 }
            remaining should satisfy {
                size == 2 &&
                get(0).title == "Super Smooth Soul food" &&
                get(1).title == "Soul food"
            }
        }
    }

    describe("scopedSelect"){

    }
})