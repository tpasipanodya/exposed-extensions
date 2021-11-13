package io.taff.hephaestus.persistence

import com.taff.hephaestustest.expectation.any.satisfy
import com.taff.hephaestustest.expectation.should
import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.tables.uuid.DestroyableModelTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.util.*

data class DestroyableRecord(val title: String? = null,
                             override var id: UUID? = null,
                             override var createdAt: OffsetDateTime? = null,
                             override var updatedAt: OffsetDateTime? = null,
                             override var destroyedAt: OffsetDateTime? = null) : DestroyableModel


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