package io.taff.exposed.extensions.tables.uuid

import io.taff.exposed.extensions.helpers.env
import io.taff.exposed.extensions.models.Model
import io.taff.exposed.extensions.models.SoftDeletableModel
import io.taff.exposed.extensions.tables.shared.TitleAware
import io.taff.exposed.extensions.tables.shared.includeSoftDeletableTableSpeks
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import java.time.Instant
import java.util.*

data class SoftDeletableUuidRecord(override var title: String? = null,
                                 override var id: UUID? = null,
                                 override var createdAt: Instant? = null,
                                 override var updatedAt: Instant? = null,
                                 override var softDeletedAt: Instant? = null)
    : TitleAware, Model<UUID>, SoftDeletableModel<UUID>


var softDeleteTitleColumn: Column<String>? = null
val softDeletableUuidTable = object : SoftDeletableUuidTable<SoftDeletableUuidRecord>("soft_deletable_uuid_recogrds") {
    val title = varchar("title", 50)
    init { softDeleteTitleColumn = title }

    override fun initializeModel(row: ResultRow) = SoftDeletableUuidRecord(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: SoftDeletableUuidRecord) {
        model.title?.let { stmt[title] = it }
    }
}


object SoftDeletableUuidTableSpek  : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(softDeletableUuidTable) }

    beforeEachTest { transaction { softDeletableUuidTable.stripDefaultScope().deleteAll() } }


    includeSoftDeletableTableSpeks(
        softDeletableUuidTable,
        recordFxn = { SoftDeletableUuidRecord(title = "Soul Food") },
        titleColumnRef = softDeleteTitleColumn!!
    )
})
