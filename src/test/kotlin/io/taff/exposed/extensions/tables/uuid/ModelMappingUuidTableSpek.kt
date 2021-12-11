package io.taff.exposed.extensions.tables.uuid

import io.taff.hephaestus.helpers.env
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import io.taff.hephaestus.persistence.models.Model
import io.taff.hephaestus.persistence.tables.shared.TitleAware
import io.taff.hephaestus.persistence.tables.shared.includeModelMappingTableSpeks
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.Instant
import java.util.UUID

/** Dummy model for testing */
data class UuidRecord(
    override var title: String? = null,
    override var id: UUID? = null,
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null
) : Model<UUID>, TitleAware

/** Dummy table for testing */
var modelMappingTitleColumnRef: Column<String>? = null
val uuidRecords = object : ModelMappingUuidTable<UuidRecord>("uuid_records") {
    val title = varchar("title", 50)
    init { modelMappingTitleColumnRef = title }
    override fun initializeModel(row: ResultRow) = UuidRecord(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: UuidRecord) {
        model.title?.let { stmt[title] = it }
    }
}

object ModelMappingUuidTableSpek  : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(uuidRecords) }

    beforeEachTest { transaction { uuidRecords.stripDefaultScope().deleteAll() } }

    includeModelMappingTableSpeks(
        table = uuidRecords,
        recordFunc = { UuidRecord("Foo bar") },
        titleColumnRef = modelMappingTitleColumnRef!!
    )
})
