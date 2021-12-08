package io.taff.hephaestus.persistence.tables.long

import io.taff.hephaestus.persistence.models.SoftDeletableModel
import io.taff.hephaestus.persistence.models.TenantScopedModel
import io.taff.hephaestus.persistence.tables.traits.TenantScopedSoftDeletableTableTrait
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.Instant.now

/**
 * A table that enforces tenant isolation unless explicitly requested not to.
 * Also supports soft deletes by setting the`destroyed_at` column on destruction.
 *
 * @param TID The concrete tenantId type.
 * @param M The concrete model type.
 */
abstract class TenantScopedSoftDeletableLongIdTable<TID : Comparable<TID>, M>(name: String, )
    : LongIdTable(name),
    TenantScopedSoftDeletableTableTrait<Long, TID, M, TenantScopedSoftDeletableLongIdTable<TID, M>>
        where M : TenantScopedModel<Long, TID>, M : SoftDeletableModel<Long> {

    override val createdAt = timestamp("created_at").clientDefault { now() }
    override val updatedAt = timestamp("updated_at").clientDefault { now() }
    override val destroyedAt = timestamp("destroyed_at").nullable()
    override val defaultScope = { Op.build { currentTenantScope() and destroyedAt.isNull() } }

    override fun self() = this

    override fun softDeleted() = View(this) {
        Op.build { currentTenantScope() and destroyedAt.isNotNull() }
    }

    override fun liveAndSoftDeleted() = View(this) {
        Op.build { currentTenantScope() }
    }

    override fun softDeletedForAllTenants() = VIewWithTenantScopeStriped(this) {
        Op.build { destroyedAt.isNotNull() }
    }

    override fun liveAndSoftDeletedForAllTenants() = VIewWithTenantScopeStriped(this) {
        Op.build { destroyedAt.isNull() or destroyedAt.isNotNull() }
    }

    override fun liveForAllTenants() = VIewWithTenantScopeStriped(this) {
        Op.build { destroyedAt.isNull() }
    }

    open class View<TID : Comparable<TID>, M>(private val actual: TenantScopedSoftDeletableLongIdTable<TID, M>,
        override val defaultScope: () -> Op<Boolean>)
        : TenantScopedSoftDeletableLongIdTable<TID, M>(actual.tableName),
        TenantScopedSoftDeletableTableTrait<Long, TID, M, TenantScopedSoftDeletableLongIdTable<TID, M>>
            where M : TenantScopedModel<Long, TID>, M :  SoftDeletableModel<Long> {

        override val columns: List<Column<*>> = actual.columns

        override val tenantId = actual.tenantId

        override fun initializeModel(row: ResultRow) = actual.initializeModel(row)

        override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: M) =
            actual.appendStatementValues(stmt, model)

        override fun describe(s: Transaction, queryBuilder: QueryBuilder) = actual.describe(s, queryBuilder)

        override fun self() = this
    }

    class VIewWithTenantScopeStriped<TID : Comparable<TID>, M>(actual: TenantScopedSoftDeletableLongIdTable<TID, M>,
        override val defaultScope: () -> Op<Boolean>) : View<TID, M>(actual, defaultScope)
            where M : TenantScopedModel<Long, TID>, M :  SoftDeletableModel<Long> {
        override fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, model: M, vararg skip: Column<*>) {
            super.appendBaseStatementValues(stmt, model, tenantId, *skip)
        }
        override fun validateDestruction(models: Array<out M>) = models
    }
}
