package io.taff.hephaestus.persistence.tables.uuid

import io.taff.hephaestustest.expectation.any.satisfy
import io.taff.hephaestustest.expectation.boolean.beTrue
import io.taff.hephaestustest.expectation.should
import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.PersistenceError
import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.models.Model
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.fail
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Instant
import java.util.*

data class DestroyableUuidRecord(var title: String? = null,
                                 override var id: UUID? = null,
                                 override var createdAt: Instant? = null,
                                 override var updatedAt: Instant? = null,
                                 override var destroyedAt: Instant? = null) : Model<UUID>, DestroyableModel<UUID>


val destroyableUuidTable = object : DestroyableUuidTable<DestroyableUuidRecord>("destroyable_uuid_recogrds") {
    val title = varchar("title", 50)
    override fun initializeModel(row: ResultRow) = DestroyableUuidRecord(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: DestroyableUuidRecord) {
        model.title?.let { stmt[title] = it }
    }
}

object DestroyableModelTableSpek  :Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(destroyableUuidTable) }

    beforeEachTest { transaction { destroyableUuidTable.deleteAll() } }

    describe("insert") {
        val record by memoized { DestroyableUuidRecord("Soul food") }
        val persisted by memoized { transaction { destroyableUuidTable.insert(record).first() } }
        val reloaded by memoized { transaction { destroyableUuidTable.selectAll().map(destroyableUuidTable::toModel) } }

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
            val record by memoized {   DestroyableUuidRecord("Soul food") }
            val persisted by memoized { transaction { destroyableUuidTable.insert(record) } }
            val updated by memoized {
                transaction {
                    destroyableUuidTable
                        .update(this, persisted[0].copy(title = newTitle))
                        .first()
                }
            }
            val reloaded by memoized {
                transaction {
                    destroyableUuidTable
                        .selectAll()
                        .map(destroyableUuidTable::toModel)
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
            val record by memoized { DestroyableUuidRecord("Soul food") }
            val updated by memoized {
                transaction {
                    destroyableUuidTable
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
                destroyableUuidTable
                    .insert(DestroyableUuidRecord("Soul food"))
                    .first()
            }
        }
        val reloaded by memoized {
            transaction {
                destroyableUuidTable
                    .selectAll()
                    .map(destroyableUuidTable::toModel)
            }
        }

        it("hard deletes the record") {
            persisted should satisfy { isPersisted() }

            val deleted = transaction { destroyableUuidTable.delete(persisted) }

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
                 destroyableUuidTable
                     .insert(DestroyableUuidRecord("Soul food"))
                     .first()
             }
         }
         val reloaded by memoized {
             transaction {
                 destroyableUuidTable
                     .selectAll()
                     .map(destroyableUuidTable::toModel)
             }
         }

         it("soft deletes the record") {
             persisted should satisfy { isPersisted() }

             val destroyed = transaction { destroyableUuidTable.destroy(this, persisted) }
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
