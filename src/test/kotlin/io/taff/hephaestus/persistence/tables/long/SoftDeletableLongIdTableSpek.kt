package io.taff.hephaestus.persistence.tables.long


import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.persistence.models.SoftDeletableModel
import io.taff.hephaestus.persistence.models.Model
import io.taff.hephaestus.persistence.tables.shared.TitleAware
import io.taff.hephaestus.persistence.tables.shared.includeSoftDeletableTableSpeks
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import java.time.Instant

data class SoftDeletableLongIdRecord(override var title: String? = null,
                                   override var id: Long? = null,
                                   override var createdAt: Instant? = null,
                                   override var updatedAt: Instant? = null,
                                   override var softDeletedAt: Instant? = null)
    : TitleAware, Model<Long>, SoftDeletableModel<Long>

var softDeleteTitleColumn: Column<String>? = null
val softDeletableLongIdRecords = object : SoftDeletableLongIdTable<SoftDeletableLongIdRecord>("soft_deletable_long_id_records") {
    val title = varchar("title", 50)

    init { softDeleteTitleColumn = title }

    override fun initializeModel(row: ResultRow) = SoftDeletableLongIdRecord(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: SoftDeletableLongIdRecord) {
        model.title?.let { stmt[title] = it }
    }
}

object SoftDeletableLongIdTableSpek  :Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(softDeletableLongIdRecords) }

    beforeEachTest { transaction { softDeletableLongIdRecords.stripDefaultScope().deleteAll() } }

    includeSoftDeletableTableSpeks(
        softDeletableLongIdRecords,
        recordFxn = { SoftDeletableLongIdRecord("Soul food") },
        titleColumnRef = softDeleteTitleColumn!!
    )
})
