package io.taff.hephaestus.persistence.tables.long


import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.models.Model
import io.taff.hephaestus.persistence.tables.shared.Scope
import io.taff.hephaestus.persistence.tables.shared.TitleAware
import io.taff.hephaestus.persistence.tables.shared.includeDestroyableModelSpeks
import io.taff.hephaestus.persistence.tables.uuid.destroyableUuidTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import java.time.Instant

data class DestroyableLongIdRecord(override var title: String? = null,
                                   override var id: Long? = null,
                                   override var createdAt: Instant? = null,
                                   override var updatedAt: Instant? = null,
                                   override var destroyedAt: Instant? = null) : TitleAware, Model<Long>, DestroyableModel<Long>

var titleColumn: Column<String>? = null
val destroyableLongIdRecords = object : DestroyableLongIdTable<DestroyableLongIdRecord>("destroyable_long_id_records") {
    val title = varchar("title", 50)
    override fun initializeModel(row: ResultRow) = DestroyableLongIdRecord(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: DestroyableLongIdRecord) {
        model.title?.let { stmt[title] = it }
    }
}

object DestroyableLongIdTableSpek  :Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(destroyableLongIdRecords) }

    beforeEachTest { transaction { destroyableLongIdRecords.stripDefaultScope().deleteAll() } }

    includeDestroyableModelSpeks(destroyableLongIdRecords,
        recordFxn = { DestroyableLongIdRecord("Soul food") },
        directUpdate = { record, newTitle, scope ->
            when(scope) {
                Scope.LIVE -> destroyableLongIdRecords
                Scope.DELETED -> destroyableLongIdRecords.destroyed()
                Scope.ALL -> destroyableLongIdRecords.includingDestroyed()
            }.update({ destroyableLongIdRecords.id eq record.id }) {
                it[titleColumn!!] = newTitle
            }
        })
})
