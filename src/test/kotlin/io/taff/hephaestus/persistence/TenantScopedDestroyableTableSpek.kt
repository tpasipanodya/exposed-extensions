package io.taff.hephaestus.persistence

import com.taff.hephaestustest.expectation.any.satisfy
import com.taff.hephaestustest.expectation.should
import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.models.TenantScopedDestroyableModel
import io.taff.hephaestus.persistence.tables.TenantScopedDestroyableTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime

/** Dummy tenant scoped model for testing */
data class TenantScopedDestroyableRecord(val title: String? = null,
                                         override var tenantId: Long? = null,
                                         override var id: Long? = null,
                                         override var createdAt: OffsetDateTime? = null,
                                         override var updatedAt: OffsetDateTime? = null,
                                         override var destroyedAt: OffsetDateTime? = null) : TenantScopedDestroyableModel<Long>

/** Dummy tenant scoped t able for testing */
val tenantScopedDestroyableRecords = object : TenantScopedDestroyableTable<Long, TenantScopedDestroyableRecord>("tenant_scoped_destroyable_records") {
    val title = varchar("title", 50)
    override val tenantId = long("tenant_id")
    override fun initializeModel(row: ResultRow) = TenantScopedDestroyableRecord(title = row[title])
    override fun fillStatement(stmt: UpdateBuilder<Int>, model: TenantScopedDestroyableRecord) {
        model.title?.let { stmt[title] = it }
        super.fillStatement(stmt, model)
    }
}

object TenantScopedDestroyableTableSpek : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(tenantScopedDestroyableRecords) }

    val tenantId by memoized { 1L }
    val otherTenantId by memoized { 2L }

    beforeEachTest { transaction { tenantScopedDestroyableRecords.deleteAll() } }

    afterEachTest { clearCurrentTenantId() }

    describe("delete") {
        val persisted by memoized {
            setCurrentTenantId(tenantId)
            transaction {
                tenantScopedDestroyableRecords
                        .insert(TenantScopedDestroyableRecord("Soul food"))
                        .first()
            }
        }
        val reloaded by memoized {
            setCurrentTenantId(tenantId)
            transaction {
                tenantScopedDestroyableRecords
                        .selectAll()
                        .map(tenantScopedDestroyableRecords::toModel)
            }
        }

        it("hard deletes the record ignoring tenant isolation") {
            persisted should satisfy { isPersisted() }

            val deleted = transaction { tenantScopedDestroyableRecords.delete(persisted) }

            deleted should satisfy {
                toList().size == 1 &&
                first().title == persisted.title &&
                !first().destroyedAt.isNull()
            }
            reloaded should satisfy { isEmpty() }
        }
    }
})
