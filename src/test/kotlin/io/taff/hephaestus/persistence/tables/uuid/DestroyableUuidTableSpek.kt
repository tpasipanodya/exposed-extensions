package io.taff.hephaestus.persistence.tables.uuid

import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.models.Model
import io.taff.hephaestus.persistence.tables.shared.Scope
import io.taff.hephaestus.persistence.tables.shared.TitleAware
import io.taff.hephaestus.persistence.tables.shared.includeDestroyableModelSpeks
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import java.time.Instant
import java.util.*

data class DestroyableUuidRecord(override var title: String? = null,
                                 override var id: UUID? = null,
                                 override var createdAt: Instant? = null,
                                 override var updatedAt: Instant? = null,
                                 override var destroyedAt: Instant? = null) : TitleAware, Model<UUID>, DestroyableModel<UUID>


var titleColumn: Column<String>? = null
val destroyableUuidTable = object : DestroyableUuidTable<DestroyableUuidRecord>("destroyable_uuid_recogrds") {
    val title = varchar("title", 50)
    init { titleColumn = title }

    override fun initializeModel(row: ResultRow) = DestroyableUuidRecord(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: DestroyableUuidRecord) {
        model.title?.let { stmt[title] = it }
    }
}


object DestroyableUuidTableSpek  : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(destroyableUuidTable) }

    beforeEachTest { transaction { destroyableUuidTable.stripDefaultScope().deleteAll() } }


    includeDestroyableModelSpeks(destroyableUuidTable,
        recordFxn = { DestroyableUuidRecord(title = "Soul Food") },
        directUpdate = { record, newTitle, scope ->
            when(scope) {
                Scope.LIVE -> destroyableUuidTable
                Scope.DELETED -> destroyableUuidTable.destroyed()
                Scope.ALL -> destroyableUuidTable.includingDestroyed()
            }.update({ destroyableUuidTable.id eq record.id }) {
                it[titleColumn!!] = newTitle
            }
        })
})
