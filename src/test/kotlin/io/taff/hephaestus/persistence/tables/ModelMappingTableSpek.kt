package io.taff.hephaestus.persistence.tables

import com.taff.hephaestustest.expectation.any.satisfy
import com.taff.hephaestustest.expectation.boolean.beTrue
import com.taff.hephaestustest.expectation.should
import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.PersistenceError.UnpersistedUpdateError
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import io.taff.hephaestus.persistence.models.Model
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.junit.jupiter.api.fail
import java.time.OffsetDateTime
import java.util.UUID

/** Dummy model for testing */
data class Record(val title: String? = null,
                  override var id: UUID? = null,
                  override var createdAt: OffsetDateTime? = null,
                  override var updatedAt: OffsetDateTime? = null) : Model

/** Dummy table for testing */
val records = object : ModelMappingTable<Record>("records") {
    val title = varchar("title", 50)
    override fun initializeModel(row: ResultRow) = Record(title = row[title])
    override fun fillStatement(stmt: UpdateBuilder<Int>, model: Record) {
        model.title?.let { stmt[title] = it }
    }
}

object ModelAwareTableSpek  : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(records) }

    beforeEachTest { transaction { records.deleteAll() } }

    describe("insert") {
        val record by memoized { Record("Soul food") }
        val persisted by memoized { transaction { records.insert(record).first() } }
        val reloaded by memoized { transaction { records.selectAll().map(records::toModel) } }

        it("persists the record") {
            persisted should satisfy { this == record && isPersisted()}

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
            val record by memoized {   Record("Soul food") }
            val persisted by memoized { transaction { records.insert(record) } }
            val updated by memoized {
                transaction {
                    records
                        .update(this, persisted[0].copy(title = newTitle))
                        .first()
                }
            }
            val reloaded by memoized { transaction { records.selectAll().map(records::toModel) } }

            it("modifies the record") {
                updated should satisfy {
                    this != persisted[0] &&
                    isPersisted() &&
                    title == newTitle
                }

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
            val record by memoized {   Record("Soul food") }
            val updated by memoized {
                transaction {
                    records.update(this, record.copy(title = newTitle))
                        .first()
                }
            }

            it("does not modify the record") {
                try {
                    updated
                    fail("Expected an exception to be raised but none was")
                } catch (e: UnpersistedUpdateError) {
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
                records
                    .insert(Record("Soul food"))
                    .first()
            }
        }
        val reloaded by memoized {
            transaction {
                records
                    .selectAll()
                    .map(records::toModel)
            }
        }

        it("hard deletes the record") {
            persisted should satisfy { isPersisted() }

            val deleted = transaction { records.delete(persisted) }

            deleted should satisfy {
                toList().size == 1 &&
                first().title == persisted.title
            }
            reloaded should satisfy { isEmpty() }
        }
    }
})