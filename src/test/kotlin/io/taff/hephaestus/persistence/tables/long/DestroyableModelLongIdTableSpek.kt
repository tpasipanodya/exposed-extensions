package io.taff.hephaestus.persistence.tables.long


import com.taff.hephaestustest.expectation.any.satisfy
import com.taff.hephaestustest.expectation.boolean.beTrue
import com.taff.hephaestustest.expectation.should
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
import java.time.OffsetDateTime

data class DestroyableLongIdRecord(var title: String? = null,
                             override var id: Long? = null,
                             override var createdAt: OffsetDateTime? = null,
                             override var updatedAt: OffsetDateTime? = null,
                             override var destroyedAt: OffsetDateTime? = null) : Model<Long>, DestroyableModel<Long>


val destroyableLongIdRecords = object : DestroyableModelLongTable<DestroyableLongIdRecord>("destroyable_long_id_records") {
    val title = varchar("title", 50)
    override fun initializeModel(row: ResultRow) = DestroyableLongIdRecord(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: DestroyableLongIdRecord) {
        model.title?.let { stmt[title] = it }
    }
}

object DestroyableModelTableSpek  :Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(destroyableLongIdRecords) }

    beforeEachTest { transaction { destroyableLongIdRecords.deleteAll() } }

    describe("insert") {
        val record by memoized { DestroyableLongIdRecord("Soul food") }
        val persisted by memoized { transaction { destroyableLongIdRecords.insert(record).first() } }
        val reloaded by memoized { transaction { destroyableLongIdRecords.selectAll().map(destroyableLongIdRecords::toModel) } }

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
            val record by memoized {   DestroyableLongIdRecord("Soul food") }
            val persisted by memoized { transaction { destroyableLongIdRecords.insert(record) } }
            val updated by memoized {
                transaction {
                    destroyableLongIdRecords
                        .update(this, persisted[0].copy(title = newTitle))
                        .first()
                }
            }
            val reloaded by memoized {
                transaction {
                    destroyableLongIdRecords
                        .selectAll()
                        .map(destroyableLongIdRecords::toModel)
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
            val record by memoized { DestroyableLongIdRecord("Soul food") }
            val updated by memoized {
                transaction {
                    destroyableLongIdRecords
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
                destroyableLongIdRecords
                    .insert(DestroyableLongIdRecord("Soul food"))
                    .first()
            }
        }
        val reloaded by memoized {
            transaction {
                destroyableLongIdRecords
                    .selectAll()
                    .map(destroyableLongIdRecords::toModel)
            }
        }

        it("hard deletes the record") {
            persisted should satisfy { isPersisted() }

            val deleted = transaction { destroyableLongIdRecords.delete(persisted) }

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
                destroyableLongIdRecords
                    .insert(DestroyableLongIdRecord("Soul food"))
                    .first()
            }
        }
        val reloaded by memoized {
            transaction {
                destroyableLongIdRecords
                    .selectAll()
                    .map(destroyableLongIdRecords::toModel)
            }
        }

        it("soft deletes the record") {
            persisted should satisfy { isPersisted() }

            val destroyed = transaction { destroyableLongIdRecords.destroy(this, persisted) }
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