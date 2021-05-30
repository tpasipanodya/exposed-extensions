package io.taff.hephaestus.persistence.tables

import com.taff.hephaestustest.expectation.any.satisfy
import com.taff.hephaestustest.expectation.boolean.beTrue
import com.taff.hephaestustest.expectation.iterable.beAnOrderedCollectionOf
import com.taff.hephaestustest.expectation.should
import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.helpers.isNull
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import io.taff.hephaestus.persistence.models.Model
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.OffsetDateTime

data class Record(val title: String? = null,
                  override var id: Long? = null,
                  override var createdAt: OffsetDateTime? = null,
                  override var updatedAt: OffsetDateTime? = null) : Model

val records = object : ModelAwareTable<Record>("records") {
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

        context("with non iterables") {
            val persisted by memoized { transaction { records.insert(record) } }
            val reloaded by memoized { transaction { records.selectAll().map(records::toModel) } }

            it("persists") {
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

        context("with iterables") {
            val persisted by memoized { transaction { records.insert(listOf(record)) } }
            val reloaded by memoized { transaction { records.selectAll().map(records::toModel) } }

            it("persists") {
                persisted should beAnOrderedCollectionOf(record)
                record.isPersisted() should beTrue()
                reloaded  should satisfy {
                    size == 1 &&
                    first().let {
                        it.title == record.title &&
                                !it.createdAt.isNull() &&
                                !it.updatedAt.isNull()
                    }
                }
            }
        }
    }

    describe("update") {
        val newTitle by memoized { "groovy soul food" }
        val persisted by memoized { transaction { records.insert(Record("Soul food")) } }

        context("with non-iterables") {
            val updated by memoized {
                transaction {
                    records.update(
                        this,
                        persisted.copy(title = newTitle)
                    )
                }
            }
            val reloaded by memoized { transaction { records.selectAll().map(records::toModel) } }

            it("updates") {
                persisted should satisfy { this != updated }
                updated should satisfy { isPersisted() && title == newTitle }
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

        context("with iterables") {
            val updateds by memoized {
                transaction {
                    records.update(
                        this,
                        listOf(persisted.copy(title = newTitle))
                    )
                }
            }
            val reloaded by memoized { transaction { records.selectAll().map(records::toModel) } }

            it("updates") {
                updateds should beAnOrderedCollectionOf(persisted.copy(title = newTitle))
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
    }

    describe("delete") {
        val persisted by memoized { transaction { records.insert(Record("Soul food")) } }
        val reloaded by memoized { transaction { records.selectAll().map(records::toModel) } }

        context("with non-iterables") {
            it("deletes") {
                persisted should satisfy { isPersisted() }

                val deleted = transaction { records.delete(persisted) }

                deleted should satisfy {
                    toList().size == 1 &&
                    first().title == persisted.title
                }
                reloaded should satisfy { isEmpty() }
            }
        }

        context("with iterables") {
            it("deletes") {
                persisted should satisfy { isPersisted() }

                val deleted = transaction { records.delete(listOf(persisted)) }

                deleted should satisfy {
                    toList().size == 1 &&
                    first().title == persisted.title
                }
                reloaded should satisfy { isEmpty() }
            }
        }
    }
})