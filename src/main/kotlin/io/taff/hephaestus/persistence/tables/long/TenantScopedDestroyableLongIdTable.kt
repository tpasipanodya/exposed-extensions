package io.taff.hephaestus.persistence.tables.long

import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.models.TenantScopedModel
import io.taff.hephaestus.persistence.tables.traits.TenantScopedDestroyableTableTrait
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
abstract class TenantScopedDestroyableLongIdTable<TID : Comparable<TID>, M>(name: String, )
    : LongIdTable(name),
    TenantScopedDestroyableTableTrait<Long, TID, M, TenantScopedDestroyableLongIdTable<TID, M>>
        where M : TenantScopedModel<Long, TID>, M : DestroyableModel<Long> {

    override val createdAt = timestamp("created_at").clientDefault { now() }
    override val updatedAt = timestamp("updated_at").clientDefault { now() }
    override val destroyedAt = timestamp("destroyed_at").nullable()

    override fun self() = this

    override fun destroyed() = View(this) {
        Op.build { currentTenantScope() and destroyedAt.isNotNull() }
    }

    override fun liveAndDestroyed() = View(this) {
        Op.build { currentTenantScope() }
    }

    override fun destroyedForAllTenants() = View(this) {
        Op.build { destroyedAt.isNotNull() }
    }

    override fun liveAndDestroyedForAllTenants() = View(this) {
        Op.build { destroyedAt.isNull() or destroyedAt.isNotNull() }
    }

    override fun liveForAllTenants() = View(this) {
        Op.build { destroyedAt.isNull() }
    }

    class View<TID : Comparable<TID>, M>(private val actual: TenantScopedDestroyableLongIdTable<TID, M>,
                                         override val defaultScope: () -> Op<Boolean>)
        : TenantScopedDestroyableLongIdTable<TID, M>(actual.tableName),
        TenantScopedDestroyableTableTrait<Long, TID, M, TenantScopedDestroyableLongIdTable<TID, M>>
            where M : TenantScopedModel<Long, TID>, M :  DestroyableModel<Long> {

        override val columns: List<Column<*>> = actual.columns

        override val tenantId = actual.tenantId

        override fun initializeModel(row: ResultRow) = actual.initializeModel(row)

        override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: M) =
            actual.appendStatementValues(stmt, model)

        override fun describe(s: Transaction, queryBuilder: QueryBuilder) = actual.describe(s, queryBuilder)

        override fun self() = this
    }
}
