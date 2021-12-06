package io.taff.hephaestus.persistence.tables.long

import io.taff.hephaestustest.expectation.any.satisfy
import io.taff.hephaestustest.expectation.boolean.beTrue
import io.taff.hephaestustest.expectation.should
import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.PersistenceError
import io.taff.hephaestus.persistence.models.Model
import io.taff.hephaestustest.expectation.any.equal
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.fail
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Instant
import java.util.*

/** Dummy model for testing */
data class LongIdRecord(
    var title: String? = null,
    override var id: Long? = null,
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null
) : Model<Long>

/** Dummy table for testing */
val longIdRecords = object : ModelMappingLongIdTable<LongIdRecord>("long_id_records") {
    val title = varchar("title", 50)
    override fun initializeModel(row: ResultRow) = LongIdRecord(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: LongIdRecord) {
        model.title?.let { stmt[title] = it }
    }
}

object ModelMappingLongIdTableSpek  : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(longIdRecords) }

    beforeEachTest { transaction { longIdRecords.deleteAll() } }

    describe("insert") {
        val record by memoized { LongIdRecord("Soul food") }
        val persisted by memoized { transaction { longIdRecords.insert(record).first() } }
        val reloaded by memoized { transaction { longIdRecords.selectAll().map(longIdRecords::toModel) } }

        it("persists the record") {
            persisted should satisfy {
                this.title == record.title &&
                        isPersisted()
            }

            record.isPersisted() should beTrue()
            reloaded should satisfy {
                size == 1 &&
                first().let {
                    it.title == record.title &&
                            !it.createdAt.isNull() &&
                            !it.updatedAt.isNull()
                }
            }
        }
    }

    describe("update") {
        val newTitle by memoized { "groovy soul food" }

        context("when already persisted") {
            val record by memoized { LongIdRecord("Soul food") }
            val persisted by memoized { transaction { longIdRecords.insert(record) } }
            val updated by memoized {
                transaction {
                    longIdRecords.update(persisted[0].copy(title = newTitle))
                }
            }
            val reloaded by memoized { transaction { longIdRecords.selectAll().map(longIdRecords::toModel) } }

            it("modifies the record") {
                updated should beTrue()
                reloaded should satisfy {
                    size == 1 &&
                    first().let {
                        it.title == newTitle &&
                        !it.createdAt.isNull() &&
                        !it.updatedAt.isNull()
                    }
                }
            }
        }

        context("When the model hasn't been saved yet") {
            val record by memoized { LongIdRecord("Soul food") }
            val updated by memoized {
                transaction {
                    longIdRecords.update(record.copy(title = newTitle))
                }
            }

            it("does not modify the record") {
                try {
                    updated
                    fail("Expected an exception to be raised but none was")
                } catch (e: PersistenceError.UnpersistedUpdateError) {
                    e.message!! should satisfy {
                        contains("Cannot update") &&
                                contains(" because it hasn't been persisted yet")
                    }
                }
            }
        }
    }

    describe("delete") {
        val persisted by memoized {
            transaction {
                longIdRecords
                    .insert(LongIdRecord("Soul food"))
                    .first()
            }
        }
        val reloaded by memoized {
            transaction {
                longIdRecords
                    .selectAll()
                    .map(longIdRecords::toModel)
            }
        }

        it("hard deletes the record") {
            persisted should satisfy { isPersisted() }

            val deleted = transaction { longIdRecords.delete(persisted) }

            deleted should equal(true)
            reloaded should satisfy { isEmpty() }
        }
    }
})
