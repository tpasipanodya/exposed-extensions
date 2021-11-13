package io.taff.hephaestus.persistence

import com.taff.hephaestustest.expectation.any.satisfy
import com.taff.hephaestustest.expectation.boolean.beTrue
import com.taff.hephaestustest.expectation.should
import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.models.Model
import io.taff.hephaestus.persistence.tables.uuid.DestroyableModelTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.fail
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.util.*

data class DestroyableRecord(var title: String? = null,
                             override var id: UUID? = null,
                             override var createdAt: OffsetDateTime? = null,
                             override var updatedAt: OffsetDateTime? = null,
                             override var destroyedAt: OffsetDateTime? = null) : Model, DestroyableModel


val destroyableRecords = object : DestroyableModelTable<DestroyableRecord>("destroyable_records") {
    val title = varchar("title", 50)
    override fun initializeModel(row: ResultRow) = DestroyableRecord(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: DestroyableRecord) {
        model.title?.let { stmt[title] = it }
    }
}

object DestroyableModelTableSpek  :Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(destroyableRecords) }

    beforeEachTest { transaction { destroyableRecords.deleteAll() } }

    describe("insert") {
        val record by memoized { DestroyableRecord("Soul food") }
        val persisted by memoized { transaction { destroyableRecords.insert(record).first() } }
        val reloaded by memoized { transaction { destroyableRecords.selectAll().map(destroyableRecords::toModel) } }

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
            val record by memoized {   DestroyableRecord("Soul food") }
            val persisted by memoized { transaction { destroyableRecords.insert(record) } }
            val updated by memoized {
                transaction {
                    destroyableRecords
                        .update(this, persisted[0].copy(title = newTitle))
                        .first()
                }
            }
            val reloaded by memoized {
                transaction {
                    destroyableRecords
                        .selectAll()
                        .map(destroyableRecords::toModel)
                }
            }

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
            val record by memoized { DestroyableRecord("Soul food") }
            val updated by memoized {
                transaction {
                    destroyableRecords
                        .update(this, record.copy(title = newTitle))
                        .first()
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
                destroyableRecords
                    .insert(DestroyableRecord("Soul food"))
                    .first()
            }
        }
        val reloaded by memoized {
            transaction {
                destroyableRecords
                    .selectAll()
                    .map(destroyableRecords::toModel)
            }
        }

        it("hard deletes the record") {
            persisted should satisfy { isPersisted() }

            val deleted = transaction { destroyableRecords.delete(persisted) }

            deleted should satisfy {
                toList().size == 1 &&
                        first().title == persisted.title &&
                        !first().destroyedAt.isNull()
            }
            reloaded should satisfy { isEmpty() }
        }
    }

     describe("destroy") {
         val persisted by memoized {
             transaction {
                 destroyableRecords
                     .insert(DestroyableRecord("Soul food"))
                     .first()
             }
         }
         val reloaded by memoized {
             transaction {
                 destroyableRecords
                     .selectAll()
                     .map(destroyableRecords::toModel)
             }
         }

         it("soft deletes the record") {
             persisted should satisfy { isPersisted() }

             val destroyed = transaction { destroyableRecords.destroy(this, persisted) }
             destroyed should satisfy {
                 size == 1 &&
                 first().title == persisted.title &&
                 !first().destroyedAt.isNull()
             }

             reloaded should satisfy {
                 size == 1 &&
                 first().title == persisted.title &&
                 !first().destroyedAt.isNull() }
         }
     }
})