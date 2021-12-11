package io.taff.exposed.extensions.tables.uuid

import io.taff.exposed.extensions.models.SoftDeletableModel
import io.taff.exposed.extensions.models.TenantScopedModel
import io.taff.exposed.extensions.tables.traits.TenantScopedSoftDeletableTableTrait
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.Instant.now
import java.util.UUID

/**
 * A table that enforces tenant isolation unless explicitly requested not to.
 * Also supports soft deletes by setting the`soft_deleted_at` column on destruction.
 *
 * @param TID The concrete tenantId type.
 * @param M The concrete model type.
 */
abstract class TenantScopedSoftDeletableUuidTable<TID : Comparable<TID>, M>(name: String, )
    : UUIDTable(name),
    TenantScopedSoftDeletableTableTrait<UUID, TID, M, TenantScopedSoftDeletableUuidTable<TID, M>>
        where M : TenantScopedModel<UUID, TID>, M : SoftDeletableModel<UUID> {

    override val createdAt = timestamp("created_at").clientDefault { now() }
    override val updatedAt = timestamp("updated_at").clientDefault { now() }
    override val softDeletedAt = timestamp("soft_deleted_at").nullable()
    override val defaultScope = { Op.build { currentTenantScope() and softDeletedAt.isNull() } }

    override fun self() = this

    override fun softDeleted() = View(this) {
        Op.build { currentTenantScope() and softDeletedAt.isNotNull() }
    }

    override fun liveAndSoftDeleted() = View(this) {
        Op.build { currentTenantScope() }
    }

    override fun softDeletedForAllTenants() = VIewWithTenantScopeStriped(this) {
        Op.build { softDeletedAt.isNotNull() }
    }

    override fun liveAndSoftDeletedForAllTenants() = VIewWithTenantScopeStriped(this) {
        Op.build { softDeletedAt.isNull() or softDeletedAt.isNotNull() }
    }

    override fun liveForAllTenants() = VIewWithTenantScopeStriped(this) {
        Op.build { softDeletedAt.isNull() }
    }

    open class View<TID : Comparable<TID>, M>(private val actual: TenantScopedSoftDeletableUuidTable<TID, M>,
                                         override val defaultScope: () -> Op<Boolean>)
        : TenantScopedSoftDeletableUuidTable<TID, M>(actual.tableName),
        TenantScopedSoftDeletableTableTrait<UUID, TID, M, TenantScopedSoftDeletableUuidTable<TID, M>>
            where M : TenantScopedModel<UUID, TID>, M :  SoftDeletableModel<UUID> {

        override val columns: List<Column<*>> = actual.columns

        override val tenantId = actual.tenantId

        override fun initializeModel(row: ResultRow) = actual.initializeModel(row)

        override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: M) =
            actual.appendStatementValues(stmt, model)

        override fun describe(s: Transaction, queryBuilder: QueryBuilder) = actual.describe(s, queryBuilder)

        override fun self() = this
    }

    class VIewWithTenantScopeStriped<TID : Comparable<TID>, M>(actual: TenantScopedSoftDeletableUuidTable<TID, M>,
        override val defaultScope: () -> Op<Boolean>) : View<TID, M>(actual, defaultScope)
            where M : TenantScopedModel<UUID, TID>, M :  SoftDeletableModel<UUID> {
        override fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, model: M, vararg skip: Column<*>) {
          super.appendBaseStatementValues(stmt, model, tenantId, *skip)
        }
        override fun validateDestruction(models: Array<out M>) = models
    }
}
