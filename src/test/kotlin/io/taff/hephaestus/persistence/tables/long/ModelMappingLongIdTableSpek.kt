package io.taff.hephaestus.persistence.tables.long

import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.persistence.models.Model
import io.taff.hephaestus.persistence.tables.shared.TitleAware
import io.taff.hephaestus.persistence.tables.shared.includeModelMappingTableSpeks
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import java.time.Instant

/** Dummy model for testing */
data class LongIdRecord(
    override var title: String? = null,
    override var id: Long? = null,
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null
) : Model<Long>, TitleAware

/** Dummy table for testing */
var modelMappingTitleColumnRef: Column<String>? = null
val longIdRecords = object : ModelMappingLongIdTable<LongIdRecord>("long_id_records") {
    val title = varchar("title", 50)
    init { modelMappingTitleColumnRef = title }
    override fun initializeModel(row: ResultRow) = LongIdRecord(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: LongIdRecord) {
        model.title?.let { stmt[title] = it }
    }
}

object ModelMappingLongIdTableSpek  : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(longIdRecords) }

    beforeEachTest { transaction { longIdRecords.deleteAll() } }

    includeModelMappingTableSpeks(
        table = longIdRecords,
        recordFunc = { LongIdRecord("Foo bar") },
        titleColumnRef = modelMappingTitleColumnRef!!
    )
})
