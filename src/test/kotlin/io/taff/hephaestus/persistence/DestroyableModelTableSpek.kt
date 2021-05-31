package io.taff.hephaestus.persistence

import com.taff.hephaestustest.expectation.any.equal
import com.taff.hephaestustest.expectation.any.satisfy
import com.taff.hephaestustest.expectation.should
import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.tablestenantId.DestroyableModelTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SortOrder.ASC
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime

data class DestroyableRecord(val title: String? = null,
                             override var id: Long? = null,
                             override var createdAt: OffsetDateTime? = null,
                             override var updatedAt: OffsetDateTime? = null,
                             override var destroyedAt: OffsetDateTime? = null) : DestroyableModel


val destroyableRecords = object : DestroyableModelTable<DestroyableRecord>("destroyable_records") {
    val title = varchar("title", 50)
    override fun initializeModel(row: ResultRow) = DestroyableRecord(title = row[title])
    override fun fillStatement(stmt: UpdateBuilder<Int>, model: DestroyableRecord) {
        model.title?.let { stmt[title] = it }
        super.fillStatement(stmt, model)
    }
}

object DestroyableModelTableSpek  :Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(destroyableRecords) }

    beforeEachTest { transaction { destroyableRecords.deleteAll() } }

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

     describe("destroy (models)") {
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

    describe("destroy (where clause)") {
        val record by memoized { DestroyableRecord("Soul food") }
        val otherRecord by memoized { DestroyableRecord("Groovy Soul food") }
        val persisted by memoized {
            transaction {
                destroyableRecords
                    .insert(record, otherRecord)
                    .first()
            }
        }
        val reloaded by memoized {
            transaction {
                destroyableRecords
                    .selectAll()
                    .orderBy(destroyableRecords.id, ASC)
                    .map(destroyableRecords::toModel)
            }
        }

        it("soft deletes the record") {
            persisted should satisfy { isPersisted() }

            val destroyed = transaction { destroyableRecords.destroy { destroyableRecords.id eq persisted.id} }
            destroyed should equal(1)
            reloaded should satisfy {
                size == 2 &&
                this[0].title == record.title &&
                !this[0].destroyedAt.isNull() &&
                this[1].title ==  otherRecord.title &&
                this[1].destroyedAt.isNull()
            }
        }
    }

    describe("scopedSelect") {
        val record by memoized { DestroyableRecord("Soul food") }
        val otherRecord by memoized { DestroyableRecord("Groovy Soul food") }
        val destroyed by memoized {
            transaction {
                destroyableRecords
                    .insert(record, otherRecord)
                    .first()
                    .let { destroyableRecords.destroy(this, it) }
            }
        }
        val selectedsAll by memoized {
            transaction {
                destroyableRecords
                    .scopedSelect()
                    .orderBy(destroyableRecords.id, ASC)
                    .map(destroyableRecords::toModel)
            }
        }
        val selectedsWhere by memoized {
            transaction {
                val ids = listOf(record.id!!, otherRecord.id!!)
                destroyableRecords
                        .scopedSelect { destroyableRecords.id inList ids }
                        .orderBy(destroyableRecords.id, ASC)
                        .map(destroyableRecords::toModel)
            }
        }

        it("filters out destroyed records") {
            destroyed should satisfy {
                size == 1 && this[0].isDestroyed()
            }
            selectedsAll should satisfy {
                size == 1 &&
                this[0].let { !it.isDestroyed() && it.title == otherRecord.title }
            }
            selectedsWhere should satisfy {
                size == 1 &&
                this[0].let { !it.isDestroyed() && it.title == otherRecord.title }
            }
        }
    }
})