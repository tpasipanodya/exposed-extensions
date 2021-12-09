package io.taff.hephaestus.persistence.tables.long

import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.persistence.clearCurrentTenantId
import io.taff.hephaestus.persistence.models.TenantScopedModel
import io.taff.hephaestus.persistence.tables.shared.TitleAware
import io.taff.hephaestus.persistence.tables.shared.includeTenantScopedTableSpeks
import io.taff.hephaestus.persistence.tables.uuid.tenantScopedUuidRecords
import io.taff.hephaestus.persistence.tables.uuid.titleColumnRef
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import java.time.Instant


/** Dummy tenant scoped model for testing */
data class TenantScopedLongIdModel(
    override var title: String? = null,
    override var tenantId: Long? = null,
    override var id: Long? = null,
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null
) : TenantScopedModel<Long, Long>, TitleAware

/** Dummy tenant scoped t able for testing */
var titleColumnRef: Column<String>? = null
val tenantScopedLongIdRecords = object : TenantScopedLongIdTable<Long, TenantScopedLongIdModel>("tenant_scoped_long_id_records") {
    val title = varchar("title", 50)
    init { titleColumnRef = title }
    override val tenantId: Column<Long> = long("tenant_id")
    override fun initializeModel(row: ResultRow) = TenantScopedLongIdModel(title = row[title])
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: TenantScopedLongIdModel) {
        model.title?.let { stmt[title] = it }
    }
}

object TenantScopedLongIdTableSpek : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(tenantScopedLongIdRecords) }

    includeTenantScopedTableSpeks(
        tenantScopedLongIdRecords,
        tenantIdFunc = { 1L },
        tenant2IdFunc = { 2L },
        tenant1RecordFunc = { TenantScopedLongIdModel("Soul food")  },
        tenant2RecordFunc = { TenantScopedLongIdModel("Groovy soul food") },
        titleColumnRef = titleColumnRef!!
    )

})
